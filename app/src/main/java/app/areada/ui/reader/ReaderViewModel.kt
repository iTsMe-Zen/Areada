package app.areada.ui.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.areada.R
import app.areada.data.AreadaCacheManager
import app.areada.data.BookNoteLink
import app.areada.data.library.buildSearchIndex
import app.areada.data.BookStatus
import app.areada.data.DocumentResolver
import app.areada.data.reader.DocumentType
import app.areada.data.library.LibraryBookEntry
import app.areada.data.library.LibraryFileFilter
import app.areada.data.library.LibraryFolderEntry
import app.areada.data.library.LibraryFolderPickerEntry
import app.areada.data.library.LibraryRepository
import app.areada.data.library.LibraryRoot
import app.areada.data.library.LibrarySearchIndexEntry
import app.areada.data.library.LibrarySearchResult
import app.areada.data.library.LibrarySearchResultType
import app.areada.data.library.LibrarySortMode
import app.areada.data.reader.ReaderDocument
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReaderStateStore
import app.areada.data.reader.ReadingBookmark
import app.areada.data.reader.ReadingBookmarkActions
import app.areada.data.reader.ReadingProgress
import app.areada.data.reader.RecentDocument
import app.areada.data.reader.RecentDocumentStore
import app.areada.data.reader.RecentReadingActions
import app.areada.data.ArchiveBookContainer
import app.areada.data.ZipBookContainer
import app.areada.data.library.folderDocumentTypesById
import app.areada.data.library.isReadingProgressCompleted
import app.areada.data.library.libraryRootSignature
import app.areada.data.parseArchiveBookUriString
import app.areada.data.parseZipBookUriString
import app.areada.data.library.rootPickerEntries
import app.areada.data.library.sameLibraryBooks
import app.areada.data.library.sameLibraryFolders
import app.areada.data.reader.sanitizeReaderPreferences
import app.areada.data.library.sortLibraryBooks
import app.areada.data.library.sortLibraryFolders
import app.areada.reader.epub.EpubBook
import app.areada.reader.epub.EpubEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class ReaderViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState = _uiState.asStateFlow()

    private var initialized = false
    private val epubBookCache = linkedMapOf<String, EpubBook>()
    private val epubCacheLock = Any()
    private var libraryLoadJob: Job? = null
    private var documentOpenJob: Job? = null
    private var searchJob: Job? = null
    private var searchIndexJob: Job? = null
    private var cacheCleanupJob: Job? = null
    private var searchIndex: List<LibrarySearchIndexEntry> = emptyList()
    private var searchIndexSignature: String = ""
    private var requestedSearchIndexSignature: String = ""
    private var searchIndexGeneration: Int = 0
    private var libraryAddedAtCache: Map<String, Long> = emptyMap()
    private var noteDocumentIdsCache: Set<String> = emptySet()
    private var bookNoteLinksCache: Map<String, BookNoteLink> = emptyMap()
    private var lastNoteSectionCache: Map<String, String> = emptyMap()
    private var bookNoteReturnScreen: ReaderScreen? = null
    private var pendingZipAddToRecents: Boolean = true
    private var applicationContext: Context? = null
    private val textSaveMutex = Mutex()
    private val textSaveSequenceByUri = mutableMapOf<String, Long>()
    private val folderStateCacheLock = Any()
    private val folderStateCache = linkedMapOf<FolderCacheKey, CachedFolderState>()
    private val folderCacheFreshMillis = 60_000L

    private data class FolderCacheKey(
        val rootUriString: String,
        val relativePath: String,
    )

    private data class CachedFolderState(
        val folderState: app.areada.data.library.LibraryFolderState,
        val loadedAtMillis: Long,
    )

    fun initialize(
        context: Context,
        externalOpenUri: Uri? = null,
    ) {
        if (initialized) {
            externalOpenUri?.let { uri -> openExternalDocument(context, uri) }
            return
        }
        initialized = true

        val appContext = context.applicationContext
        applicationContext = appContext
        viewModelScope.launch(Dispatchers.IO) {
            val recents = RecentDocumentStore.load(appContext)
            val preferences = ReaderStateStore.loadPreferences(appContext)
            val progressByUri = ReaderStateStore.loadProgress(appContext)
            val bookStatusByUri = ReaderStateStore.loadBookStatuses(appContext)
            val bookmarks = ReaderStateStore.loadBookmarks(appContext)
            val libraryRoots = ReaderStateStore.loadLibraryRoots(appContext)
            val sortMode = ReaderStateStore.loadLibrarySortMode(appContext)
            val fileFilter = ReaderStateStore.loadLibraryFileFilter(appContext)
            val selectedHomeTabName = ReaderStateStore.loadHomeTabName(appContext)
            val pinnedIds = ReaderStateStore.loadPinnedLibraryItemIds(appContext)
            val addedAtById = ReaderStateStore.loadLibraryAddedAt(appContext)
            noteDocumentIdsCache = ReaderStateStore.loadNoteDocumentIds(appContext)
            bookNoteLinksCache = ReaderStateStore.loadBookNoteLinks(appContext)
            lastNoteSectionCache = ReaderStateStore.loadLastNoteSections(appContext)
            libraryAddedAtCache = addedAtById
            val pickerEntries = rootPickerEntries(libraryRoots)

            if (libraryRoots.isEmpty()) {
                clearSearchIndex()
                _uiState.update { state ->
                    state.copy(
                        recents = recents,
                        bookmarks = bookmarks,
                        preferences = preferences,
                        progressByUri = progressByUri,
                        bookStatusByUri = bookStatusByUri,
                        bookNoteLinksByUri = bookNoteLinksCache,
                        lastNoteSectionByUri = lastNoteSectionCache,
                        libraryRoots = emptyList(),
                        libraryFolderPickerEntries = emptyList(),
                        librarySortMode = sortMode,
                        libraryFileFilter = fileFilter,
                        selectedHomeTabName = selectedHomeTabName,
                        pinnedLibraryItemIds = pinnedIds,
                        libraryAddedAtById = addedAtById,
                    )
                }
                if (externalOpenUri == null) {
                    scheduleCacheCleanup(appContext)
                }
                externalOpenUri?.let { uri -> openExternalDocument(appContext, uri) }
                return@launch
            }

            val initialRoot = libraryRoots.first()
            runCatching {
                prepareFolderState(
                    context = appContext,
                    folderState = LibraryRepository.loadFolder(
                        context = appContext,
                        root = initialRoot,
                        relativePath = "",
                    ),
                    sortMode = sortMode,
                    pinnedIds = pinnedIds,
                    addedAtById = addedAtById,
                    progressByUri = progressByUri,
                    recents = recents,
                    bookStatusByUri = bookStatusByUri,
                )
            }.onSuccess { folderState ->
                cacheFolderState(initialRoot, folderState)
                _uiState.update { state ->
                    state.copy(
                        recents = recents,
                        bookmarks = bookmarks,
                        preferences = preferences,
                        progressByUri = progressByUri,
                        bookStatusByUri = bookStatusByUri,
                        bookNoteLinksByUri = bookNoteLinksCache,
                        lastNoteSectionByUri = lastNoteSectionCache,
                        libraryRoots = libraryRoots,
                        libraryFolderPickerEntries = rootPickerEntries(libraryRoots),
                        selectedRootUriString = initialRoot.treeUriString,
                        currentRelativePath = folderState.currentRelativePath,
                        currentPathSegments = folderState.pathSegments,
                        currentFolders = folderState.folders,
                        currentBooks = folderState.books,
                        librarySortMode = sortMode,
                        libraryFileFilter = fileFilter,
                        selectedHomeTabName = selectedHomeTabName,
                        pinnedLibraryItemIds = pinnedIds,
                        libraryAddedAtById = libraryAddedAtCache,
                    )
                }
                rebuildSearchIndex(
                    context = appContext,
                    roots = libraryRoots,
                    delayMillis = 250L,
                )
                if (externalOpenUri == null) {
                    scheduleCacheCleanup(appContext)
                }
                externalOpenUri?.let { uri -> openExternalDocument(appContext, uri) }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        recents = recents,
                        bookmarks = bookmarks,
                        preferences = preferences,
                        progressByUri = progressByUri,
                        bookStatusByUri = bookStatusByUri,
                        bookNoteLinksByUri = bookNoteLinksCache,
                        lastNoteSectionByUri = lastNoteSectionCache,
                        libraryRoots = libraryRoots,
                        libraryFolderPickerEntries = pickerEntries,
                        selectedRootUriString = initialRoot.treeUriString,
                        librarySortMode = sortMode,
                        libraryFileFilter = fileFilter,
                        selectedHomeTabName = selectedHomeTabName,
                        pinnedLibraryItemIds = pinnedIds,
                        libraryAddedAtById = addedAtById,
                        errorMessage = appContext.getString(R.string.folder_access_lost_body),
                    )
                }
                rebuildSearchIndex(
                    context = appContext,
                    roots = libraryRoots,
                    delayMillis = 250L,
                )
                if (externalOpenUri == null) {
                    scheduleCacheCleanup(appContext)
                }
                externalOpenUri?.let { uri -> openExternalDocument(appContext, uri) }
            }
        }
    }

    fun addLibraryRoot(
        context: Context,
        treeUri: Uri,
    ) {
        val appContext = context.applicationContext
        libraryLoadJob?.cancel()
        libraryLoadJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isLoading = true, errorMessage = null)
            }

            try {
                persistTreePermission(appContext, treeUri)
                val resolvedRoot = withContext(Dispatchers.IO) {
                    LibraryRepository.resolveRoot(appContext, treeUri)
                }
                val existingRoots = _uiState.value.libraryRoots
                val updatedRoots = buildList {
                    add(resolvedRoot)
                    addAll(existingRoots.filterNot { it.treeUriString == resolvedRoot.treeUriString })
                }
                withContext(Dispatchers.IO) {
                    ReaderStateStore.saveLibraryRoots(appContext, updatedRoots)
                }
                val folderState = withContext(Dispatchers.IO) {
                    prepareFolderState(
                        context = appContext,
                        folderState = LibraryRepository.loadFolder(
                            context = appContext,
                            root = resolvedRoot,
                            relativePath = "",
                        ),
                        sortMode = _uiState.value.librarySortMode,
                        pinnedIds = _uiState.value.pinnedLibraryItemIds,
                        addedAtById = _uiState.value.libraryAddedAtById,
                    )
                }
                cacheFolderState(resolvedRoot, folderState)

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        libraryRoots = updatedRoots,
                        libraryFolderPickerEntries = rootPickerEntries(updatedRoots),
                        selectedRootUriString = resolvedRoot.treeUriString,
                        currentRelativePath = folderState.currentRelativePath,
                        currentPathSegments = folderState.pathSegments,
                        currentFolders = folderState.folders,
                        currentBooks = folderState.books,
                        libraryAddedAtById = libraryAddedAtCache,
                        errorMessage = null,
                    )
                }
                markSearchIndexDirty(appContext, updatedRoots)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    return@launch
                }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = displayError(throwable, "Unable to open that folder."),
                    )
                }
            }
        }
    }

    fun selectLibraryRoot(
        context: Context,
        root: LibraryRoot,
    ) {
        loadLibraryFolder(
            context = context,
            root = root,
            relativePath = "",
        )
    }

    fun openLibraryFolder(
        context: Context,
        relativePath: String,
    ) {
        val state = _uiState.value
        val root = state.libraryRoots.firstOrNull { it.treeUriString == state.selectedRootUriString }
            ?: return
        if (state.currentRelativePath == relativePath) {
            return
        }
        loadLibraryFolder(context, root, relativePath)
    }

    fun openLibraryPickerEntry(
        context: Context,
        entry: LibraryFolderPickerEntry,
    ) {
        if (
            entry.rootUriString == _uiState.value.selectedRootUriString &&
            entry.relativePath == _uiState.value.currentRelativePath
        ) {
            return
        }

        val root = _uiState.value.libraryRoots.firstOrNull { it.treeUriString == entry.rootUriString }
        if (root == null) {
            _uiState.update { state ->
                state.copy(errorMessage = "That folder is no longer available.")
            }
            return
        }
        loadLibraryFolder(context, root, entry.relativePath)
    }

    fun refreshCurrentFolder(
        context: Context,
        showLoading: Boolean = false,
    ) {
        val state = _uiState.value
        if (
            !initialized ||
            state.isLoading ||
            state.currentScreen !is ReaderScreen.Home ||
            state.libraryRoots.isEmpty()
        ) {
            return
        }

        val root = state.libraryRoots.firstOrNull { it.treeUriString == state.selectedRootUriString }
            ?: return
        if (showLoading) {
            clearFolderStateCache()
            LibraryRepository.clearFolderNavigationCache(root.treeUriString)
        }
        loadLibraryFolder(
            context = context,
            root = root,
            relativePath = state.currentRelativePath,
            showLoading = showLoading,
            useCache = !showLoading,
        )
        markSearchIndexDirty(context.applicationContext, state.libraryRoots)
    }

    fun updateSearchQuery(
        context: Context,
        query: String,
    ) {
        val appContext = context.applicationContext
        val cleanQuery = query.take(80)
        searchJob?.cancel()

        if (cleanQuery.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    searchQuery = "",
                    searchResults = emptyList(),
                    isSearching = false,
                )
            }
            return
        }

        val roots = _uiState.value.libraryRoots
        val freshIndex = searchIndexSignature == roots.libraryRootSignature()
        if (!freshIndex) {
            val indexJobActive = searchIndexJob?.isActive == true
            if (!indexJobActive || searchIndex.isEmpty()) {
                rebuildSearchIndex(
                    context = appContext,
                    roots = roots,
                    force = indexJobActive,
                )
            }
        }

        _uiState.update { state ->
            state.copy(
                searchQuery = cleanQuery,
                isSearching = !freshIndex && searchIndex.isEmpty(),
            )
        }

        searchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(35L)
            val results = filterSearchIndex(cleanQuery)
            _uiState.update { state ->
                if (state.searchQuery != cleanQuery) {
                    state
                } else {
                    state.copy(
                        searchResults = results,
                        isSearching = searchIndexJob?.isActive == true && results.isEmpty(),
                    )
                }
            }
        }
    }

    private fun filterSearchIndex(query: String): List<LibrarySearchResult> {
        val cleanQuery = query.trim().lowercase(Locale.ROOT)
        if (cleanQuery.isBlank()) {
            return emptyList()
        }
        return searchIndex.asSequence()
            .filter { entry -> entry.searchText.contains(cleanQuery) }
            .take(80)
            .map { entry -> entry.result }
            .toList()
    }

    private fun rebuildSearchIndex(
        context: Context,
        roots: List<LibraryRoot>,
        force: Boolean = false,
        delayMillis: Long = 0L,
    ) {
        val signature = roots.libraryRootSignature()

        if (roots.isEmpty()) {
            clearSearchIndex()
            _uiState.update { state ->
                state.copy(searchResults = emptyList(), isSearching = false)
            }
            return
        }

        if (!force && searchIndexSignature == signature && searchIndex.isNotEmpty()) {
            val currentQuery = _uiState.value.searchQuery
            val currentResults = filterSearchIndex(currentQuery)
            val folderDocumentTypes = folderDocumentTypesById(searchIndex)
            _uiState.update { state ->
                if (state.searchQuery.isBlank()) {
                    state.copy(
                        folderDocumentTypesById = folderDocumentTypes,
                        isSearching = false,
                    )
                } else {
                    state.copy(
                        folderDocumentTypesById = folderDocumentTypes,
                        searchResults = currentResults,
                        isSearching = false,
                    )
                }
            }
            return
        }

        if (!force && requestedSearchIndexSignature == signature && searchIndexJob?.isActive == true) {
            return
        }

        val generation = ++searchIndexGeneration
        requestedSearchIndexSignature = signature
        searchIndexJob?.cancel()

        _uiState.update { state ->
            if (state.searchQuery.isBlank()) {
                state
            } else {
                state.copy(isSearching = true)
            }
        }

        val appContext = context.applicationContext
        searchIndexJob = viewModelScope.launch(Dispatchers.IO) {
            if (delayMillis > 0L && _uiState.value.searchQuery.isBlank()) {
                delay(delayMillis)
            }
            val rebuiltIndex = buildSearchIndex(appContext, roots)
            if (searchIndexGeneration != generation || requestedSearchIndexSignature != signature) {
                return@launch
            }

            val folderDocumentTypes = folderDocumentTypesById(rebuiltIndex)
            searchIndex = rebuiltIndex
            searchIndexSignature = signature
            val currentQuery = _uiState.value.searchQuery
            val currentResults = withContext(Dispatchers.Default) {
                filterSearchIndex(currentQuery)
            }
            _uiState.update { state ->
                if (state.searchQuery.isBlank()) {
                    state.copy(
                        folderDocumentTypesById = folderDocumentTypes,
                        isSearching = false,
                    )
                } else {
                    state.copy(
                        folderDocumentTypesById = folderDocumentTypes,
                        searchResults = currentResults,
                        isSearching = false,
                    )
                }
            }
        }
    }

    private fun clearSearchIndex() {
        searchIndexJob?.cancel()
        searchIndexGeneration++
        searchIndex = emptyList()
        searchIndexSignature = ""
        requestedSearchIndexSignature = ""
        _uiState.update { state -> state.copy(folderDocumentTypesById = emptyMap()) }
    }

    private fun pauseSearchIndexingForReaderOpen() {
        searchJob?.cancel()
        if (searchIndexJob?.isActive == true) {
            searchIndexJob?.cancel()
            searchIndexGeneration++
            requestedSearchIndexSignature = ""
        }
    }

    private fun markSearchIndexDirty(
        context: Context,
        roots: List<LibraryRoot>,
    ) {
        rebuildSearchIndex(
            context = context.applicationContext,
            roots = roots,
            force = true,
            delayMillis = if (_uiState.value.searchQuery.isBlank()) 350L else 0L,
        )
    }

    fun openSearchResult(
        context: Context,
        result: LibrarySearchResult,
    ) {
        _uiState.update { state ->
            state.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
            )
        }
        when (result.type) {
            LibrarySearchResultType.FOLDER -> {
                val root = _uiState.value.libraryRoots.firstOrNull { root ->
                    root.treeUriString == result.rootUriString
                } ?: return
                loadLibraryFolder(context, root, result.relativePath)
            }

            LibrarySearchResultType.BOOK -> {
                val uriString = result.uriString ?: return
                val uri = Uri.parse(uriString)
                val document = result.documentType?.let { type ->
                    ReaderDocument(
                        uri = uri,
                        uriString = uriString,
                        title = result.title,
                        type = type,
                    )
                }
                openDocument(context, uri, preResolvedDocument = document)
            }
        }
    }

    fun openLibraryBook(
        context: Context,
        book: LibraryBookEntry,
    ) {
        val uri = Uri.parse(book.uriString)
        openDocument(
            context = context,
            uri = uri,
            preResolvedDocument = ReaderDocument(
                uri = uri,
                uriString = book.uriString,
                title = book.title,
                type = book.type,
            ),
        )
    }

    fun openDocument(
        context: Context,
        uri: Uri,
        fromRecent: Boolean = false,
        addToRecents: Boolean = true,
        preResolvedDocument: ReaderDocument? = null,
        initialProgressOverride: ReadingProgress? = null,
    ) {
        val appContext = context.applicationContext

        documentOpenJob?.cancel()
        pauseSearchIndexingForReaderOpen()
        documentOpenJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isLoading = true, errorMessage = null)
            }

            try {
                val initialDocument = preResolvedDocument ?: withContext(Dispatchers.IO) {
                    DocumentResolver.resolve(appContext, uri)
                }
                val document = when {
                    parseZipBookUriString(initialDocument.uriString) != null -> withContext(Dispatchers.IO) {
                        val reference = parseZipBookUriString(initialDocument.uriString)
                            ?: error(appContext.getString(R.string.zip_could_not_open_selected))
                        val entry = ZipBookContainer
                            .listSupportedEntries(appContext, Uri.parse(reference.archiveUriString))
                            .firstOrNull { zipEntry -> zipEntry.entryName == reference.entryName }
                            ?: error(appContext.getString(R.string.zip_could_not_open_selected))
                        ZipBookContainer.extractEntry(appContext, entry)
                    }

                    parseArchiveBookUriString(initialDocument.uriString) != null -> withContext(Dispatchers.IO) {
                        val reference = parseArchiveBookUriString(initialDocument.uriString)
                            ?: error(appContext.getString(R.string.zip_could_not_open_selected))
                        val entry = ArchiveBookContainer
                            .listSupportedEntries(appContext, Uri.parse(reference.archiveUriString))
                            .firstOrNull { archiveEntry -> archiveEntry.entryName == reference.entryName }
                            ?: error(appContext.getString(R.string.zip_could_not_open_selected))
                        ArchiveBookContainer.extractEntry(appContext, entry)
                    }

                    initialDocument.type == DocumentType.ZIP -> {
                        val zipEntries = withContext(Dispatchers.IO) {
                            ZipBookContainer.listSupportedEntries(appContext, initialDocument.uri)
                        }
                        when (zipEntries.size) {
                            0 -> error(appContext.getString(R.string.zip_no_supported_books))
                            1 -> withContext(Dispatchers.IO) {
                                ZipBookContainer.extractEntry(appContext, zipEntries.first())
                            }
                            else -> {
                                pendingZipAddToRecents = addToRecents
                                _uiState.update { state ->
                                    state.copy(
                                        isLoading = false,
                                        zipEntriesToChoose = zipEntries,
                                        errorMessage = null,
                                    )
                                }
                                return@launch
                            }
                        }
                    }

                    initialDocument.type == DocumentType.ARCHIVE -> {
                        val archiveEntries = withContext(Dispatchers.IO) {
                            ArchiveBookContainer.listSupportedEntries(appContext, initialDocument.uri)
                        }
                        when (archiveEntries.size) {
                            0 -> error(appContext.getString(R.string.zip_no_supported_books))
                            1 -> withContext(Dispatchers.IO) {
                                ArchiveBookContainer.extractEntry(appContext, archiveEntries.first())
                            }
                            else -> {
                                pendingZipAddToRecents = addToRecents
                                _uiState.update { state ->
                                    state.copy(
                                        isLoading = false,
                                        zipEntriesToChoose = archiveEntries,
                                        errorMessage = null,
                                    )
                                }
                                return@launch
                            }
                        }
                    }

                    else -> initialDocument
                }
                val savedProgress = initialProgressOverride ?: _uiState.value.progressByUri[document.uriString]

                val screen = when (document.type) {
                    DocumentType.EPUB -> {
                        cancelCacheCleanup()
                        val book = withContext(Dispatchers.IO) {
                            synchronized(epubCacheLock) {
                                epubBookCache[document.uriString]
                                    ?.takeIf { cachedBook -> EpubEngine.isCacheUsable(cachedBook) }
                            } ?: run {
                                val parsed = EpubEngine.parse(appContext, document.uri, document.title)
                                synchronized(epubCacheLock) {
                                    epubBookCache[document.uriString] = parsed
                                }
                                trimEpubCache()
                                parsed
                            }
                        }
                        ReaderScreen.Epub(
                            document = document,
                            book = book,
                            initialChapterIndex = savedProgress?.epubChapterIndex?.coerceIn(0, book.chapters.lastIndex) ?: 0,
                            initialScrollFraction = savedProgress?.epubScrollFraction?.coerceIn(0f, 1f) ?: 0f,
                        )
                    }

                    DocumentType.PDF -> ReaderScreen.Pdf(
                        document = document,
                        initialPageIndex = savedProgress?.pdfPageIndex?.coerceAtLeast(0) ?: 0,
                        initialZoomScale = savedProgress?.pdfZoomScale?.coerceIn(1f, 5f) ?: 1f,
                    )

                    DocumentType.TXT,
                    DocumentType.FB2 -> {
                        val content = withContext(Dispatchers.IO) {
                            LibraryRepository.readTextLikeDocumentContent(appContext, document)
                        }
                        ReaderScreen.Text(
                            document = content.title
                                ?.takeIf { title -> document.type == DocumentType.FB2 && title.isNotBlank() }
                                ?.let { title -> document.copy(title = title) }
                                ?: document,
                            initialText = content.text,
                            initialScrollFraction = savedProgress?.txtScrollFraction?.coerceIn(0f, 1f) ?: 0f,
                            editable = document.type == DocumentType.TXT,
                            sectionedNote = document.type == DocumentType.TXT &&
                                document.uriString in noteDocumentIdsCache,
                            initialNoteSectionTitle = lastNoteSectionCache[document.uriString],
                        )
                    }

                    DocumentType.ZIP,
                    DocumentType.ARCHIVE -> error(appContext.getString(R.string.zip_could_not_open))
                }

                val updatedRecents = if (addToRecents) {
                    withContext(Dispatchers.IO) {
                        RecentReadingActions.updatedForOpen(_uiState.value.recents, document).also { recents ->
                            RecentDocumentStore.save(appContext, recents)
                        }
                    }
                } else {
                    _uiState.value.recents
                }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        recents = updatedRecents,
                        currentScreen = screen,
                        zipEntriesToChoose = emptyList(),
                        errorMessage = null,
                    )
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    return@launch
                }

                if (fromRecent) {
                    val sanitizedState = RecentReadingActions.removed(
                        recents = _uiState.value.recents,
                        progressByUri = _uiState.value.progressByUri,
                        uriString = uri.toString(),
                    )
                    withContext(Dispatchers.IO) {
                        RecentDocumentStore.save(appContext, sanitizedState.recents)
                        ReaderStateStore.saveProgress(appContext, sanitizedState.progressByUri)
                    }
                    _uiState.update { state ->
                        state.copy(
                            recents = sanitizedState.recents,
                            progressByUri = sanitizedState.progressByUri,
                        )
                    }
                }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = displayError(throwable, "Unable to open that file."),
                    )
                }
            }
        }
    }

    fun openExternalDocument(
        context: Context,
        uri: Uri,
        addToRecents: Boolean = true,
    ) {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "content" && scheme != "file") {
            _uiState.update { state ->
                state.copy(errorMessage = context.getString(R.string.could_not_open_file))
            }
            return
        }

        openDocument(context = context, uri = uri, addToRecents = addToRecents)
    }

    fun openPickedDocument(
        context: Context,
        uri: Uri,
    ) {
        persistDocumentReadPermission(context.applicationContext, uri)
        openExternalDocument(context, uri, addToRecents = false)
    }

    fun openZipEntry(
        context: Context,
        entry: app.areada.data.ZipBookEntry,
    ) {
        _uiState.update { state ->
            state.copy(zipEntriesToChoose = emptyList())
        }
        openDocument(
            context = context,
            uri = Uri.parse(entry.uriString),
            addToRecents = pendingZipAddToRecents,
            preResolvedDocument = ReaderDocument(
                uri = Uri.parse(entry.uriString),
                uriString = entry.uriString,
                title = entry.title,
                type = entry.type,
            ),
        )
        pendingZipAddToRecents = true
    }

    fun dismissZipEntries() {
        pendingZipAddToRecents = true
        _uiState.update { state ->
            state.copy(zipEntriesToChoose = emptyList())
        }
    }

    fun reopenRecent(
        context: Context,
        recent: RecentDocument,
    ) {
        val uri = Uri.parse(recent.uriString)
        openDocument(
            context = context,
            uri = uri,
            fromRecent = true,
            preResolvedDocument = ReaderDocument(
                uri = uri,
                uriString = recent.uriString,
                title = recent.title,
                type = recent.type,
            ),
        )
    }

    fun removeBookmark(
        context: Context,
        bookmark: ReadingBookmark,
    ) {
        val appContext = context.applicationContext
        val updatedBookmarks = _uiState.value.bookmarks.filterNot { it.id == bookmark.id }
        _uiState.update { state ->
            state.copy(bookmarks = updatedBookmarks)
        }
        viewModelScope.launch(Dispatchers.IO) {
            ReaderStateStore.saveBookmarks(appContext, updatedBookmarks)
        }
    }

    fun openBookmark(
        context: Context,
        bookmark: ReadingBookmark,
    ) {
        val uri = Uri.parse(bookmark.uriString)
        openDocument(
            context = context,
            uri = uri,
            preResolvedDocument = ReaderDocument(
                uri = uri,
                uriString = bookmark.uriString,
                title = bookmark.title,
                type = bookmark.type,
            ),
            initialProgressOverride = ReadingBookmarkActions.toProgress(bookmark),
        )
    }

    fun toggleEpubBookmark(
        context: Context,
        document: ReaderDocument,
        chapterIndex: Int,
        chapterCount: Int,
        scrollFraction: Float,
        chapterTitle: String,
    ) {
        toggleBookmark(
            context = context,
            bookmark = ReadingBookmarkActions.createEpubBookmark(
                document = document,
                chapterIndex = chapterIndex,
                chapterCount = chapterCount,
                scrollFraction = scrollFraction,
                chapterTitle = chapterTitle,
            ),
        )
    }

    fun togglePdfBookmark(
        context: Context,
        document: ReaderDocument,
        pageIndex: Int,
        pageCount: Int,
    ) {
        toggleBookmark(
            context = context,
            bookmark = ReadingBookmarkActions.createPdfBookmark(
                document = document,
                pageIndex = pageIndex,
                pageCount = pageCount,
            ),
        )
    }

    fun toggleTextBookmark(
        context: Context,
        document: ReaderDocument,
        scrollFraction: Float,
    ) {
        toggleBookmark(
            context = context,
            bookmark = ReadingBookmarkActions.createTextBookmark(
                document = document,
                scrollFraction = scrollFraction,
            ),
        )
    }

    fun removeRecent(
        context: Context,
        recent: RecentDocument,
    ) {
        val appContext = context.applicationContext
        val updatedState = RecentReadingActions.removed(
            recents = _uiState.value.recents,
            progressByUri = _uiState.value.progressByUri,
            uriString = recent.uriString,
        )
        _uiState.update { state ->
            state.copy(
                recents = updatedState.recents,
                progressByUri = updatedState.progressByUri,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            RecentDocumentStore.save(appContext, updatedState.recents)
            ReaderStateStore.saveProgress(appContext, updatedState.progressByUri)
        }
    }

    fun removeLibraryRoot(
        context: Context,
        root: LibraryRoot,
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val previousState = _uiState.value
            val updatedRoots = previousState.libraryRoots
                .filterNot { it.treeUriString == root.treeUriString }
            val removedPrefix = "${root.treeUriString}::"
            val updatedPinnedIds = previousState.pinnedLibraryItemIds
                .filterNot { id -> id == root.treeUriString || id.startsWith(removedPrefix) || id.startsWith(root.treeUriString) }
                .toSet()
            val updatedAddedAt = previousState.libraryAddedAtById
                .filterKeys { id -> id != root.treeUriString && !id.startsWith(removedPrefix) && !id.startsWith(root.treeUriString) }

            _uiState.update { state ->
                state.copy(isLoading = true, errorMessage = null)
            }

            withContext(Dispatchers.IO) {
                releaseTreePermission(appContext, Uri.parse(root.treeUriString))
                ReaderStateStore.saveLibraryRoots(appContext, updatedRoots)
                ReaderStateStore.savePinnedLibraryItemIds(appContext, updatedPinnedIds)
                ReaderStateStore.saveLibraryAddedAt(appContext, updatedAddedAt)
            }
            removeCachedRoot(root.treeUriString)
            LibraryRepository.clearFolderNavigationCache(root.treeUriString)

            if (updatedRoots.isEmpty()) {
                clearSearchIndex()
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        libraryRoots = emptyList(),
                        libraryFolderPickerEntries = emptyList(),
                        selectedRootUriString = null,
                        currentRelativePath = "",
                        currentPathSegments = emptyList(),
                        currentFolders = emptyList(),
                        currentBooks = emptyList(),
                        pinnedLibraryItemIds = updatedPinnedIds,
                        libraryAddedAtById = updatedAddedAt,
                        errorMessage = null,
                    )
                }
                return@launch
            }

            val nextRoot = updatedRoots.firstOrNull { it.treeUriString == previousState.selectedRootUriString }
                ?: updatedRoots.first()
            val nextRelativePath = if (nextRoot.treeUriString == previousState.selectedRootUriString) {
                previousState.currentRelativePath
            } else {
                ""
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    prepareFolderState(
                        context = appContext,
                        folderState = LibraryRepository.loadFolder(
                            context = appContext,
                            root = nextRoot,
                            relativePath = nextRelativePath,
                        ),
                        sortMode = previousState.librarySortMode,
                        pinnedIds = updatedPinnedIds,
                        addedAtById = updatedAddedAt,
                    )
                }
            }.onSuccess { folderState ->
                cacheFolderState(nextRoot, folderState)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        libraryRoots = updatedRoots,
                        libraryFolderPickerEntries = rootPickerEntries(updatedRoots),
                        selectedRootUriString = nextRoot.treeUriString,
                        currentRelativePath = folderState.currentRelativePath,
                        currentPathSegments = folderState.pathSegments,
                        currentFolders = folderState.folders,
                        currentBooks = folderState.books,
                        pinnedLibraryItemIds = updatedPinnedIds,
                        libraryAddedAtById = libraryAddedAtCache,
                        errorMessage = null,
                    )
                }
                markSearchIndexDirty(appContext, updatedRoots)
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        libraryRoots = updatedRoots,
                        libraryFolderPickerEntries = rootPickerEntries(updatedRoots),
                        selectedRootUriString = nextRoot.treeUriString,
                        currentRelativePath = "",
                        currentPathSegments = emptyList(),
                        currentFolders = emptyList(),
                        currentBooks = emptyList(),
                        pinnedLibraryItemIds = updatedPinnedIds,
                        libraryAddedAtById = updatedAddedAt,
                        errorMessage = displayError(throwable, "Unable to open that folder."),
                    )
                }
                markSearchIndexDirty(appContext, updatedRoots)
            }
        }
    }

    fun createTextNote(context: Context) {
        val appContext = context.applicationContext
        val root = currentRoot() ?: return
        val relativePath = _uiState.value.currentRelativePath

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(errorMessage = null)
            }

            runCatching {
                val note = withContext(Dispatchers.IO) {
                    LibraryRepository.createTextNote(appContext, root, relativePath)
                        ?: error("Unable to create a note in this folder.")
                }
                val document = ReaderDocument(
                    uri = Uri.parse(note.uriString),
                    uriString = note.uriString,
                    title = note.title,
                    type = DocumentType.TXT,
                )
                val stateBeforeUpdate = _uiState.value
                val now = System.currentTimeMillis()
                val decoratedNote = note.copy(
                    addedAt = stateBeforeUpdate.libraryAddedAtById[note.id] ?: now,
                    pinned = note.id in stateBeforeUpdate.pinnedLibraryItemIds,
                )
                val updatedAddedAt = stateBeforeUpdate.libraryAddedAtById + (note.id to decoratedNote.addedAt)
                val updatedNoteDocumentIds = noteDocumentIdsCache + note.uriString
                libraryAddedAtCache = updatedAddedAt
                noteDocumentIdsCache = updatedNoteDocumentIds
                val updatedBooks = sortLibraryBooks(
                    books = stateBeforeUpdate.currentBooks.filterNot { book -> book.id == note.id } + decoratedNote,
                    sortMode = stateBeforeUpdate.librarySortMode,
                    progressByUri = stateBeforeUpdate.progressByUri,
                    recents = stateBeforeUpdate.recents,
                    bookStatusByUri = stateBeforeUpdate.bookStatusByUri,
                )
                val updatedRecents = RecentReadingActions.updatedForOpen(stateBeforeUpdate.recents, document)
                if (
                    stateBeforeUpdate.selectedRootUriString == root.treeUriString &&
                    stateBeforeUpdate.currentRelativePath == relativePath
                ) {
                    cacheFolderState(
                        root = root,
                        folderState = app.areada.data.library.LibraryFolderState(
                            root = root,
                            currentRelativePath = relativePath,
                            pathSegments = stateBeforeUpdate.currentPathSegments,
                            folders = stateBeforeUpdate.currentFolders,
                            books = updatedBooks,
                        ),
                    )
                }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        recents = updatedRecents,
                        currentBooks = if (
                            state.selectedRootUriString == root.treeUriString &&
                            state.currentRelativePath == relativePath
                        ) {
                            updatedBooks
                        } else {
                            state.currentBooks
                        },
                        libraryAddedAtById = updatedAddedAt,
                        currentScreen = ReaderScreen.Text(
                            document = document,
                            initialText = "",
                            initialScrollFraction = 0f,
                            deleteOnDiscard = true,
                            sectionedNote = true,
                        ),
                        errorMessage = null,
                    )
                }
                viewModelScope.launch(Dispatchers.IO) {
                    RecentDocumentStore.save(appContext, updatedRecents)
                    ReaderStateStore.saveLibraryAddedAt(appContext, updatedAddedAt)
                    ReaderStateStore.saveNoteDocumentIds(appContext, updatedNoteDocumentIds)
                }
                markSearchIndexDirty(appContext, _uiState.value.libraryRoots)
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = displayError(throwable, "Unable to create that note."),
                    )
                }
            }
        }
    }

    fun openBookNote(
        context: Context,
        document: ReaderDocument,
    ) {
        val returnScreen = _uiState.value.currentScreen.takeIf { screen ->
            screen !is ReaderScreen.Home && !(screen is ReaderScreen.Text && screen.sectionedNote)
        }
        openOrCreateBookNote(
            context = context,
            bookUriString = document.uriString,
            bookTitle = document.title,
            returnScreen = returnScreen,
        )
    }

    fun openBookNoteForBook(
        context: Context,
        book: LibraryBookEntry,
    ) {
        openOrCreateBookNote(
            context = context,
            bookUriString = book.uriString,
            bookTitle = book.title,
            returnScreen = null,
        )
    }

    private fun openOrCreateBookNote(
        context: Context,
        bookUriString: String,
        bookTitle: String,
        returnScreen: ReaderScreen?,
    ) {
        if (bookUriString.isBlank()) {
            return
        }
        val appContext = context.applicationContext

        viewModelScope.launch {
            _uiState.update { state -> state.copy(errorMessage = null) }

            runCatching {
                val stateBeforeUpdate = _uiState.value
                val previousReaderScreen = returnScreen
                val existingLink = stateBeforeUpdate.bookNoteLinksByUri[bookUriString]
                val noteDocument = if (existingLink != null) {
                    ReaderDocument(
                        uri = Uri.parse(existingLink.noteUriString),
                        uriString = existingLink.noteUriString,
                        title = existingLink.noteTitle.ifBlank { "Book Note" },
                        type = DocumentType.TXT,
                    )
                } else {
                    val bookLocationUriString = parseZipBookUriString(bookUriString)?.archiveUriString ?: bookUriString
                    val targetLocation = withContext(Dispatchers.IO) {
                        LibraryRepository.findBookLocation(
                            context = appContext,
                            roots = stateBeforeUpdate.libraryRoots,
                            uriString = bookLocationUriString,
                        )
                    }
                    val targetRoot = targetLocation?.root
                        ?: currentRoot()
                        ?: error(appContext.getString(R.string.choose_folder_before_book_note))
                    val targetRelativePath = targetLocation?.folderRelativePath
                        ?: stateBeforeUpdate.currentRelativePath
                    val createdNote = withContext(Dispatchers.IO) {
                        LibraryRepository.createTextNote(
                            context = appContext,
                            root = targetRoot,
                            relativePath = targetRelativePath,
                            baseName = "${bookTitle.ifBlank { "Book" }} Note",
                        ) ?: error(appContext.getString(R.string.could_not_create_book_note))
                    }
                    val document = ReaderDocument(
                        uri = Uri.parse(createdNote.uriString),
                        uriString = createdNote.uriString,
                        title = createdNote.title,
                        type = DocumentType.TXT,
                    )
                    val now = System.currentTimeMillis()
                    val decoratedNote = createdNote.copy(
                        addedAt = stateBeforeUpdate.libraryAddedAtById[createdNote.id] ?: now,
                        pinned = createdNote.id in stateBeforeUpdate.pinnedLibraryItemIds,
                    )
                    val updatedAddedAt = stateBeforeUpdate.libraryAddedAtById + (createdNote.id to decoratedNote.addedAt)
                    val updatedNoteDocumentIds = noteDocumentIdsCache + createdNote.uriString
                    val updatedBookNoteLinks = stateBeforeUpdate.bookNoteLinksByUri + (
                        bookUriString to BookNoteLink(
                            bookUriString = bookUriString,
                            noteUriString = createdNote.uriString,
                            noteTitle = createdNote.title,
                        )
                    )
                    libraryAddedAtCache = updatedAddedAt
                    noteDocumentIdsCache = updatedNoteDocumentIds
                    bookNoteLinksCache = updatedBookNoteLinks

                    val updatedBooks = sortLibraryBooks(
                        books = stateBeforeUpdate.currentBooks.filterNot { book -> book.id == createdNote.id } + decoratedNote,
                        sortMode = stateBeforeUpdate.librarySortMode,
                        progressByUri = stateBeforeUpdate.progressByUri,
                        recents = stateBeforeUpdate.recents,
                        bookStatusByUri = stateBeforeUpdate.bookStatusByUri,
                    )
                    if (
                        stateBeforeUpdate.selectedRootUriString == targetRoot.treeUriString &&
                        stateBeforeUpdate.currentRelativePath == targetRelativePath
                    ) {
                        cacheFolderState(
                            root = targetRoot,
                            folderState = app.areada.data.library.LibraryFolderState(
                                root = targetRoot,
                                currentRelativePath = targetRelativePath,
                                pathSegments = stateBeforeUpdate.currentPathSegments,
                                folders = stateBeforeUpdate.currentFolders,
                                books = updatedBooks,
                            ),
                        )
                    }
                    _uiState.update { state ->
                        state.copy(
                            currentBooks = if (
                                state.selectedRootUriString == targetRoot.treeUriString &&
                                state.currentRelativePath == targetRelativePath
                            ) {
                                updatedBooks
                            } else {
                                state.currentBooks
                            },
                            libraryAddedAtById = updatedAddedAt,
                            bookNoteLinksByUri = updatedBookNoteLinks,
                        )
                    }
                    withContext(Dispatchers.IO) {
                        ReaderStateStore.saveLibraryAddedAt(appContext, updatedAddedAt)
                        ReaderStateStore.saveNoteDocumentIds(appContext, updatedNoteDocumentIds)
                        ReaderStateStore.saveBookNoteLinks(appContext, updatedBookNoteLinks)
                    }
                    markSearchIndexDirty(appContext, _uiState.value.libraryRoots)
                    document
                }

                val text = withContext(Dispatchers.IO) {
                    LibraryRepository.readText(appContext, noteDocument.uri)
                }
                val updatedRecents = RecentReadingActions.updatedForOpen(_uiState.value.recents, noteDocument)
                withContext(Dispatchers.IO) {
                    RecentDocumentStore.save(appContext, updatedRecents)
                }
                bookNoteReturnScreen = previousReaderScreen
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        recents = updatedRecents,
                        currentScreen = ReaderScreen.Text(
                            document = noteDocument,
                            initialText = text,
                            initialScrollFraction = 0f,
                            deleteOnDiscard = false,
                            editable = true,
                            sectionedNote = true,
                            initialNoteSectionTitle = lastNoteSectionCache[noteDocument.uriString],
                        ),
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                bookNoteReturnScreen = null
                _uiState.update { state ->
                    state.copy(errorMessage = displayError(throwable, appContext.getString(R.string.could_not_open_book_note)))
                }
            }
        }
    }

    fun closeReader() {
        documentOpenJob?.cancel()
        val returnScreen = bookNoteReturnScreen
        val currentScreen = _uiState.value.currentScreen
        if (returnScreen != null && currentScreen is ReaderScreen.Text && currentScreen.sectionedNote) {
            bookNoteReturnScreen = null
            _uiState.update { state ->
                state.copy(currentScreen = returnScreen.withLatestProgress(state.progressByUri))
            }
            return
        }
        bookNoteReturnScreen = null
        _uiState.update { state ->
            state.copy(currentScreen = ReaderScreen.Home)
        }
        applicationContext?.let { context -> scheduleCacheCleanup(context) }
    }

    fun dismissError() {
        _uiState.update { state ->
            state.copy(errorMessage = null)
        }
    }

    fun updatePreferences(
        context: Context,
        preferences: ReaderPreferences,
    ) {
        val appContext = context.applicationContext
        val sanitized = sanitizeReaderPreferences(preferences)
        _uiState.update { state ->
            state.copy(preferences = sanitized)
        }

        viewModelScope.launch(Dispatchers.IO) {
            ReaderStateStore.savePreferences(appContext, sanitized)
        }
    }

    fun updateBookStatus(
        context: Context,
        uriString: String,
        status: BookStatus,
    ) {
        if (uriString.isBlank()) {
            return
        }

        val appContext = context.applicationContext
        val updatedStatuses = _uiState.value.bookStatusByUri + (uriString to status)
        _uiState.update { state ->
            state.copy(bookStatusByUri = updatedStatuses)
        }

        viewModelScope.launch(Dispatchers.IO) {
            ReaderStateStore.saveBookStatuses(appContext, updatedStatuses)
        }
    }

    fun updateLastNoteSection(
        context: Context,
        noteUriString: String,
        sectionTitle: String,
    ) {
        if (noteUriString.isBlank() || sectionTitle.isBlank()) {
            return
        }
        val appContext = context.applicationContext
        val updatedSections = _uiState.value.lastNoteSectionByUri + (noteUriString to sectionTitle)
        lastNoteSectionCache = updatedSections
        _uiState.update { state ->
            state.copy(lastNoteSectionByUri = updatedSections)
        }
        viewModelScope.launch(Dispatchers.IO) {
            ReaderStateStore.saveLastNoteSections(appContext, updatedSections)
        }
    }

    fun togglePinDocument(
        context: Context,
        uriString: String,
    ) {
        if (uriString.isNotBlank()) {
            togglePinnedItem(context, uriString)
        }
    }

    fun moveRecent(
        context: Context,
        recent: RecentDocument,
        offset: Int,
    ) {
        val appContext = context.applicationContext
        val currentRecents = _uiState.value.recents
        val updatedRecents = RecentReadingActions.moved(currentRecents, recent, offset)
        if (updatedRecents == currentRecents) {
            return
        }
        _uiState.update { state -> state.copy(recents = updatedRecents) }
        viewModelScope.launch(Dispatchers.IO) {
            RecentDocumentStore.save(appContext, updatedRecents)
        }
    }

    fun moveBookmark(
        context: Context,
        bookmark: ReadingBookmark,
        offset: Int,
    ) {
        val appContext = context.applicationContext
        val currentBookmarks = _uiState.value.bookmarks
        val updatedBookmarks = ReadingBookmarkActions.moved(currentBookmarks, bookmark, offset)
        if (updatedBookmarks == currentBookmarks) {
            return
        }
        _uiState.update { state -> state.copy(bookmarks = updatedBookmarks) }
        viewModelScope.launch(Dispatchers.IO) {
            ReaderStateStore.saveBookmarks(appContext, updatedBookmarks)
        }
    }

    fun updateLibrarySortMode(
        context: Context,
        sortMode: LibrarySortMode,
    ) {
        val appContext = context.applicationContext
        clearFolderStateCache()
        _uiState.update { state ->
            state.copy(librarySortMode = sortMode)
        }

        viewModelScope.launch(Dispatchers.IO) {
            ReaderStateStore.saveLibrarySortMode(appContext, sortMode)
        }
        reloadCurrentLibraryFolder(context)
    }

    fun updateLibraryFileFilter(
        context: Context,
        filter: LibraryFileFilter,
    ) {
        val appContext = context.applicationContext
        val roots = _uiState.value.libraryRoots
        _uiState.update { state ->
            state.copy(libraryFileFilter = filter)
        }
        viewModelScope.launch(Dispatchers.IO) {
            ReaderStateStore.saveLibraryFileFilter(appContext, filter)
        }
        if (
            filter.documentTypes != null &&
            roots.isNotEmpty() &&
            (searchIndex.isEmpty() || searchIndexSignature != roots.libraryRootSignature())
        ) {
            rebuildSearchIndex(
                context = context.applicationContext,
                roots = roots,
                delayMillis = 0L,
            )
        }
    }

    fun updateHomeTab(
        context: Context,
        tabName: String,
    ) {
        if (tabName.isBlank()) {
            return
        }
        val appContext = context.applicationContext
        _uiState.update { state ->
            state.copy(selectedHomeTabName = tabName)
        }
        viewModelScope.launch(Dispatchers.IO) {
            ReaderStateStore.saveHomeTabName(appContext, tabName)
        }
    }

    fun togglePinFolder(
        context: Context,
        folder: LibraryFolderEntry,
    ) {
        togglePinnedItem(context, folder.id)
    }

    fun togglePinBook(
        context: Context,
        book: LibraryBookEntry,
    ) {
        togglePinnedItem(context, book.id)
    }

    fun deleteLibraryFolder(
        context: Context,
        folder: LibraryFolderEntry,
    ) {
        val appContext = context.applicationContext
        val root = currentRoot() ?: return
        viewModelScope.launch {
            runCatching {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val deleted = withContext(Dispatchers.IO) {
                    LibraryRepository.deleteFolder(appContext, root, folder.relativePath)
                }
                if (!deleted) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Unable to delete that folder.")
                    }
                    return@launch
                }
                LibraryRepository.clearFolderNavigationCache(root.treeUriString)
                clearFolderStateCache()
                reloadCurrentLibraryFolder(appContext)
                markSearchIndexDirty(appContext, _uiState.value.libraryRoots)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = displayError(throwable, "Unable to delete that folder."),
                    )
                }
            }
        }
    }

    fun deleteLibraryBook(
        context: Context,
        book: LibraryBookEntry,
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            runCatching {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val deleted = withContext(Dispatchers.IO) {
                    LibraryRepository.deleteBook(appContext, book.uriString)
                }
                if (!deleted) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Unable to delete that book.")
                    }
                    return@launch
                }

                val updatedRecents = _uiState.value.recents.filterNot { it.uriString == book.uriString }
                val updatedProgress = _uiState.value.progressByUri - book.uriString
                val updatedBookmarks = _uiState.value.bookmarks.filterNot { it.uriString == book.uriString }
                val updatedNoteDocumentIds = noteDocumentIdsCache - book.uriString
                val updatedBookNoteLinks = _uiState.value.bookNoteLinksByUri
                    .filterKeys { bookUriString -> bookUriString != book.uriString }
                    .filterValues { link -> link.noteUriString != book.uriString }
                val updatedLastNoteSections = _uiState.value.lastNoteSectionByUri - book.uriString
                noteDocumentIdsCache = updatedNoteDocumentIds
                bookNoteLinksCache = updatedBookNoteLinks
                lastNoteSectionCache = updatedLastNoteSections
                withContext(Dispatchers.IO) {
                    RecentDocumentStore.save(appContext, updatedRecents)
                    ReaderStateStore.saveProgress(appContext, updatedProgress)
                    ReaderStateStore.saveBookmarks(appContext, updatedBookmarks)
                    ReaderStateStore.saveNoteDocumentIds(appContext, updatedNoteDocumentIds)
                    ReaderStateStore.saveBookNoteLinks(appContext, updatedBookNoteLinks)
                    ReaderStateStore.saveLastNoteSections(appContext, updatedLastNoteSections)
                }
                _uiState.update {
                    it.copy(
                        recents = updatedRecents,
                        progressByUri = updatedProgress,
                        bookmarks = updatedBookmarks,
                        bookNoteLinksByUri = updatedBookNoteLinks,
                        lastNoteSectionByUri = updatedLastNoteSections,
                    )
                }
                clearFolderStateCache()
                reloadCurrentLibraryFolder(appContext)
                markSearchIndexDirty(appContext, _uiState.value.libraryRoots)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = displayError(throwable, "Unable to delete that book."),
                    )
                }
            }
        }
    }

    fun renameLibraryFolder(
        context: Context,
        folder: LibraryFolderEntry,
        newName: String,
    ) {
        val appContext = context.applicationContext
        val root = currentRoot() ?: return
        viewModelScope.launch {
            runCatching {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val renamed = withContext(Dispatchers.IO) {
                    LibraryRepository.renameFolder(appContext, root, folder.relativePath, newName)
                }
                if (!renamed) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Unable to rename that folder.")
                    }
                    return@launch
                }
                LibraryRepository.clearFolderNavigationCache(root.treeUriString)
                clearFolderStateCache()
                reloadCurrentLibraryFolder(appContext)
                markSearchIndexDirty(appContext, _uiState.value.libraryRoots)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = displayError(throwable, "Unable to rename that folder."),
                    )
                }
            }
        }
    }

    fun renameLibraryBook(
        context: Context,
        book: LibraryBookEntry,
        newTitle: String,
    ) {
        val appContext = context.applicationContext
        val root = currentRoot()
        val relativePath = _uiState.value.currentRelativePath
        viewModelScope.launch {
            runCatching {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val renamed = withContext(Dispatchers.IO) {
                    LibraryRepository.renameBook(appContext, root, relativePath, book, newTitle)
                }
                if (!renamed) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Unable to rename that book.")
                    }
                    return@launch
                }
                clearFolderStateCache()
                reloadCurrentLibraryFolder(appContext)
                markSearchIndexDirty(appContext, _uiState.value.libraryRoots)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = displayError(throwable, "Unable to rename that book."),
                    )
                }
            }
        }
    }

    fun saveEpubProgress(
        context: Context,
        document: ReaderDocument,
        chapterIndex: Int,
        chapterCount: Int,
        scrollFraction: Float,
    ) {
        saveProgress(
            context = context,
            progress = ReadingProgress(
                uriString = document.uriString,
                type = document.type,
                epubChapterIndex = chapterIndex.coerceAtLeast(0),
                epubChapterCount = chapterCount.coerceAtLeast(0),
                epubScrollFraction = scrollFraction.coerceIn(0f, 1f),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun savePdfProgress(
        context: Context,
        document: ReaderDocument,
        pageIndex: Int,
        pageCount: Int,
        zoomScale: Float,
    ) {
        saveProgress(
            context = context,
            progress = ReadingProgress(
                uriString = document.uriString,
                type = document.type,
                pdfPageIndex = pageIndex.coerceAtLeast(0),
                pdfPageCount = pageCount.coerceAtLeast(0),
                pdfZoomScale = zoomScale.coerceIn(1f, 5f),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun saveTextProgress(
        context: Context,
        document: ReaderDocument,
        scrollFraction: Float,
    ) {
        saveProgress(
            context = context,
            progress = ReadingProgress(
                uriString = document.uriString,
                type = document.type,
                txtScrollFraction = scrollFraction.coerceIn(0f, 1f),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun saveTextDocument(
        context: Context,
        document: ReaderDocument,
        text: String,
    ) {
        if (document.type != DocumentType.TXT) {
            return
        }
        val appContext = context.applicationContext
        val uriString = document.uriString
        val saveSequence = synchronized(textSaveSequenceByUri) {
            val nextSequence = (textSaveSequenceByUri[uriString] ?: 0L) + 1L
            textSaveSequenceByUri[uriString] = nextSequence
            nextSequence
        }
        viewModelScope.launch(Dispatchers.IO) {
            val saved = textSaveMutex.withLock {
                val isLatestQueuedSave = synchronized(textSaveSequenceByUri) {
                    textSaveSequenceByUri[uriString] == saveSequence
                }
                if (!isLatestQueuedSave) {
                    return@withLock true
                }
                LibraryRepository.saveText(appContext, document.uri, text)
            }
            if (!saved) {
                _uiState.update { state ->
                    val currentScreen = state.currentScreen
                    if (
                        currentScreen is ReaderScreen.Text &&
                        currentScreen.document.uriString == document.uriString
                    ) {
                        state.copy(errorMessage = appContext.getString(R.string.could_not_save_note))
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun discardTextDocument(
        context: Context,
        document: ReaderDocument,
        deleteFile: Boolean,
    ) {
        if (!deleteFile) {
            closeReader()
            return
        }

        val appContext = context.applicationContext
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    LibraryRepository.deleteBook(appContext, document.uriString)
                }
                val updatedRecents = _uiState.value.recents.filterNot { it.uriString == document.uriString }
                val updatedProgress = _uiState.value.progressByUri - document.uriString
                val updatedBookmarks = _uiState.value.bookmarks.filterNot { it.uriString == document.uriString }
                val updatedNoteDocumentIds = noteDocumentIdsCache - document.uriString
                val updatedBookNoteLinks = _uiState.value.bookNoteLinksByUri
                    .filterKeys { bookUriString -> bookUriString != document.uriString }
                    .filterValues { link -> link.noteUriString != document.uriString }
                val updatedLastNoteSections = _uiState.value.lastNoteSectionByUri - document.uriString
                noteDocumentIdsCache = updatedNoteDocumentIds
                bookNoteLinksCache = updatedBookNoteLinks
                lastNoteSectionCache = updatedLastNoteSections
                withContext(Dispatchers.IO) {
                    RecentDocumentStore.save(appContext, updatedRecents)
                    ReaderStateStore.saveProgress(appContext, updatedProgress)
                    ReaderStateStore.saveBookmarks(appContext, updatedBookmarks)
                    ReaderStateStore.saveNoteDocumentIds(appContext, updatedNoteDocumentIds)
                    ReaderStateStore.saveBookNoteLinks(appContext, updatedBookNoteLinks)
                    ReaderStateStore.saveLastNoteSections(appContext, updatedLastNoteSections)
                }
                _uiState.update { state ->
                    state.copy(
                        recents = updatedRecents,
                        progressByUri = updatedProgress,
                        bookmarks = updatedBookmarks,
                        bookNoteLinksByUri = updatedBookNoteLinks,
                        lastNoteSectionByUri = updatedLastNoteSections,
                        currentScreen = ReaderScreen.Home,
                    )
                }
                clearFolderStateCache()
                reloadCurrentLibraryFolder(appContext)
                markSearchIndexDirty(appContext, _uiState.value.libraryRoots)
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        currentScreen = ReaderScreen.Home,
                        errorMessage = displayError(throwable, "Unable to discard that note."),
                    )
                }
            }
        }
    }

    fun renameTextDocument(
        context: Context,
        document: ReaderDocument,
        newTitle: String,
        currentText: String,
    ) {
        val appContext = context.applicationContext
        val stateBeforeRename = _uiState.value
        val currentRoot = stateBeforeRename.libraryRoots.firstOrNull { root ->
            root.treeUriString == stateBeforeRename.selectedRootUriString
        }
        val currentRelativePath = stateBeforeRename.currentRelativePath
        val roots = stateBeforeRename.libraryRoots
        viewModelScope.launch {
            runCatching {
                val renamedDocument = withContext(Dispatchers.IO) {
                    LibraryRepository.renameTextDocument(
                        context = appContext,
                        roots = roots,
                        currentRoot = currentRoot,
                        currentRelativePath = currentRelativePath,
                        document = document,
                        newTitle = newTitle,
                    )
                        ?: error("Unable to rename that note.")
                }
                withContext(Dispatchers.IO) {
                    LibraryRepository.saveText(appContext, renamedDocument.uri, currentText)
                }
                val currentScreen = _uiState.value.currentScreen
                val updatedBookmarks = _uiState.value.bookmarks.map { bookmark ->
                    if (bookmark.uriString == document.uriString) {
                        ReadingBookmarkActions.withDocument(bookmark, renamedDocument)
                    } else {
                        bookmark
                    }
                }
                val updatedRecents = RecentReadingActions.renamedDocument(
                    recents = _uiState.value.recents,
                    oldUriString = document.uriString,
                    document = renamedDocument,
                )
                val updatedNoteDocumentIds = if (document.uriString in noteDocumentIdsCache) {
                    noteDocumentIdsCache - document.uriString + renamedDocument.uriString
                } else {
                    noteDocumentIdsCache
                }
                val updatedBookNoteLinks = _uiState.value.bookNoteLinksByUri.mapValues { (_, link) ->
                    if (link.noteUriString == document.uriString) {
                        link.copy(
                            noteUriString = renamedDocument.uriString,
                            noteTitle = renamedDocument.title,
                        )
                    } else {
                        link
                    }
                }
                val updatedLastNoteSections = _uiState.value.lastNoteSectionByUri.let { sections ->
                    sections[document.uriString]?.let { sectionTitle ->
                        sections - document.uriString + (renamedDocument.uriString to sectionTitle)
                    } ?: sections
                }
                noteDocumentIdsCache = updatedNoteDocumentIds
                bookNoteLinksCache = updatedBookNoteLinks
                lastNoteSectionCache = updatedLastNoteSections
                withContext(Dispatchers.IO) {
                    RecentDocumentStore.save(appContext, updatedRecents)
                    ReaderStateStore.saveBookmarks(appContext, updatedBookmarks)
                    ReaderStateStore.saveNoteDocumentIds(appContext, updatedNoteDocumentIds)
                    ReaderStateStore.saveBookNoteLinks(appContext, updatedBookNoteLinks)
                    ReaderStateStore.saveLastNoteSections(appContext, updatedLastNoteSections)
                }
                _uiState.update { state ->
                    state.copy(
                        recents = updatedRecents,
                        bookmarks = updatedBookmarks,
                        bookNoteLinksByUri = updatedBookNoteLinks,
                        lastNoteSectionByUri = updatedLastNoteSections,
                        currentScreen = if (
                            currentScreen is ReaderScreen.Text &&
                            currentScreen.document.uriString == document.uriString
                        ) {
                            currentScreen.copy(
                                document = renamedDocument,
                                initialText = currentText,
                            )
                        } else {
                            state.currentScreen
                        },
                    )
                }
                clearFolderStateCache()
                reloadCurrentLibraryFolder(appContext)
                markSearchIndexDirty(appContext, _uiState.value.libraryRoots)
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(errorMessage = displayError(throwable, "Unable to rename that note."))
                }
            }
        }
    }

    fun goBackInLibrary(context: Context): Boolean {
        val state = _uiState.value
        val root = currentRoot() ?: return false
        if (state.currentRelativePath.isBlank()) {
            return false
        }

        val parentPath = state.currentRelativePath.substringBeforeLast('/', "")
        loadLibraryFolder(context, root, parentPath)
        return true
    }

    private fun loadLibraryFolder(
        context: Context,
        root: LibraryRoot,
        relativePath: String,
        showLoading: Boolean = true,
        useCache: Boolean = true,
    ) {
        val appContext = context.applicationContext
        val cachedFolderState = if (useCache) {
            cachedFolderState(root, relativePath)
        } else {
            null
        }
        libraryLoadJob?.cancel()
        val cachedFolder = cachedFolderState?.folderState
        val shouldRefreshCachedFolder = cachedFolderState?.let { cached ->
            System.currentTimeMillis() - cached.loadedAtMillis > folderCacheFreshMillis
        } == true
        if (cachedFolder != null) {
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    selectedRootUriString = root.treeUriString,
                    currentRelativePath = cachedFolder.currentRelativePath,
                    currentPathSegments = cachedFolder.pathSegments,
                    currentFolders = cachedFolder.folders,
                    currentBooks = cachedFolder.books,
                    libraryFolderPickerEntries = rootPickerEntries(state.libraryRoots),
                    libraryAddedAtById = libraryAddedAtCache,
                    errorMessage = null,
                )
            }
            if (!shouldRefreshCachedFolder) {
                return
            }
        }

        libraryLoadJob = viewModelScope.launch {
            if (showLoading && cachedFolder == null) {
                _uiState.update { state ->
                    state.copy(isLoading = true, errorMessage = null)
                }
            } else if (cachedFolder == null) {
                _uiState.update { state ->
                    state.copy(errorMessage = null)
                }
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    prepareFolderState(
                        context = appContext,
                        folderState = LibraryRepository.loadFolder(
                            context = appContext,
                            root = root,
                            relativePath = relativePath,
                        ),
                        sortMode = _uiState.value.librarySortMode,
                        pinnedIds = _uiState.value.pinnedLibraryItemIds,
                        addedAtById = _uiState.value.libraryAddedAtById,
                    )
                }
            }.onSuccess { folderState ->
                cacheFolderState(root, folderState)
                _uiState.update { state ->
                    if (
                        (cachedFolder != null || !showLoading) &&
                        state.selectedRootUriString == root.treeUriString &&
                        state.currentRelativePath == folderState.currentRelativePath &&
                        sameLibraryFolders(state.currentFolders, folderState.folders) &&
                        sameLibraryBooks(state.currentBooks, folderState.books)
                    ) {
                        return@update state.copy(isLoading = false)
                    }

                    state.copy(
                        isLoading = false,
                        selectedRootUriString = root.treeUriString,
                        currentRelativePath = folderState.currentRelativePath,
                        currentPathSegments = folderState.pathSegments,
                        currentFolders = folderState.folders,
                        currentBooks = folderState.books,
                        libraryFolderPickerEntries = rootPickerEntries(state.libraryRoots),
                        libraryAddedAtById = libraryAddedAtCache,
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    return@onFailure
                }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = displayError(throwable, "Unable to open that folder."),
                    )
                }
            }
        }
    }

    private fun persistTreePermission(
        context: Context,
        treeUri: Uri,
    ) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    private fun persistDocumentReadPermission(
        context: Context,
        uri: Uri,
    ) {
        if (uri.scheme?.lowercase(Locale.ROOT) != "content") {
            return
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun releaseTreePermission(
        context: Context,
        treeUri: Uri,
    ) {
        val permission = context.contentResolver.persistedUriPermissions
            .firstOrNull { persisted -> persisted.uri == treeUri }
            ?: return
        var flags = 0
        if (permission.isReadPermission) {
            flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        if (permission.isWritePermission) {
            flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        if (flags != 0) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(treeUri, flags)
            }.onFailure {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
        }
    }

    private fun toggleBookmark(
        context: Context,
        bookmark: ReadingBookmark,
    ) {
        val appContext = context.applicationContext
        val updatedBookmarks = ReadingBookmarkActions.toggled(_uiState.value.bookmarks, bookmark)

        _uiState.update { state ->
            state.copy(bookmarks = updatedBookmarks)
        }
        viewModelScope.launch(Dispatchers.IO) {
            ReaderStateStore.saveBookmarks(appContext, updatedBookmarks)
        }
    }

    private fun saveProgress(
        context: Context,
        progress: ReadingProgress,
    ) {
        val appContext = context.applicationContext
        val completed = isReadingProgressCompleted(progress)
        val updatedState = RecentReadingActions.updatedForProgressSave(
            recents = _uiState.value.recents,
            progressByUri = _uiState.value.progressByUri,
            progress = progress,
            completed = completed,
        )
        _uiState.update { state ->
            state.copy(
                recents = updatedState.recents,
                progressByUri = updatedState.progressByUri,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (completed) {
                RecentDocumentStore.save(appContext, updatedState.recents)
            }
            ReaderStateStore.saveProgress(appContext, updatedState.progressByUri)
        }
    }

    private fun currentRoot(): LibraryRoot? =
        _uiState.value.libraryRoots.firstOrNull { it.treeUriString == _uiState.value.selectedRootUriString }

    private fun protectedCacheRootsFor(screen: ReaderScreen): Set<File> =
        when (screen) {
            is ReaderScreen.Epub -> setOf(screen.book.extractedRoot)
            else -> emptySet()
        }

    private fun scheduleCacheCleanup(
        context: Context,
        protectedRoots: Set<File> = activeReaderCacheRoots(),
    ) {
        val appContext = context.applicationContext
        cacheCleanupJob?.cancel()
        cacheCleanupJob = viewModelScope.launch(Dispatchers.IO) {
            AreadaCacheManager.cleanup(appContext, protectedRoots)
        }
    }

    private fun cancelCacheCleanup() {
        cacheCleanupJob?.cancel()
        cacheCleanupJob = null
    }

    private fun activeReaderCacheRoots(): Set<File> =
        protectedCacheRootsFor(_uiState.value.currentScreen)

    private fun reloadCurrentLibraryFolder(context: Context) {
        val root = currentRoot() ?: return
        loadLibraryFolder(
            context = context,
            root = root,
            relativePath = _uiState.value.currentRelativePath,
            useCache = false,
        )
    }

    private fun togglePinnedItem(
        context: Context,
        itemId: String,
    ) {
        val appContext = context.applicationContext
        val updatedPinnedIds = if (itemId in _uiState.value.pinnedLibraryItemIds) {
            _uiState.value.pinnedLibraryItemIds - itemId
        } else {
            _uiState.value.pinnedLibraryItemIds + itemId
        }

        _uiState.update { state ->
            state.copy(pinnedLibraryItemIds = updatedPinnedIds)
        }

        viewModelScope.launch(Dispatchers.IO) {
            ReaderStateStore.savePinnedLibraryItemIds(appContext, updatedPinnedIds)
        }
        clearFolderStateCache()
        reloadCurrentLibraryFolder(context)
    }

    private fun cachedFolderState(
        root: LibraryRoot,
        relativePath: String,
    ): CachedFolderState? =
        synchronized(folderStateCacheLock) {
            val key = FolderCacheKey(root.treeUriString, relativePath)
            val cached = folderStateCache.remove(key)
            if (cached != null) {
                folderStateCache[key] = cached
            }
            cached
        }

    private fun cacheFolderState(
        root: LibraryRoot,
        folderState: app.areada.data.library.LibraryFolderState,
    ) {
        synchronized(folderStateCacheLock) {
            folderStateCache[FolderCacheKey(root.treeUriString, folderState.currentRelativePath)] = CachedFolderState(
                folderState = folderState,
                loadedAtMillis = System.currentTimeMillis(),
            )
            while (folderStateCache.size > 48 && folderStateCache.isNotEmpty()) {
                val oldestKey = folderStateCache.keys.first()
                folderStateCache.remove(oldestKey)
            }
        }
    }

    private fun removeCachedRoot(rootUriString: String) {
        synchronized(folderStateCacheLock) {
            val keysToRemove = folderStateCache.keys
                .filter { key -> key.rootUriString == rootUriString }
            keysToRemove.forEach { key -> folderStateCache.remove(key) }
        }
    }

    private fun clearFolderStateCache() {
        synchronized(folderStateCacheLock) {
            folderStateCache.clear()
        }
    }

    private fun trimEpubCache() {
        synchronized(epubCacheLock) {
            while (epubBookCache.size > 4) {
                val oldestKey = epubBookCache.keys.firstOrNull() ?: return
                epubBookCache.remove(oldestKey)
            }
        }
    }

    private fun prepareFolderState(
        context: Context,
        folderState: app.areada.data.library.LibraryFolderState,
        sortMode: LibrarySortMode,
        pinnedIds: Set<String>,
        addedAtById: Map<String, Long>,
        progressByUri: Map<String, ReadingProgress> = _uiState.value.progressByUri,
        recents: List<RecentDocument> = _uiState.value.recents,
        bookStatusByUri: Map<String, BookStatus> = _uiState.value.bookStatusByUri,
    ): app.areada.data.library.LibraryFolderState {
        val now = System.currentTimeMillis()
        val updatedAddedAt = addedAtById.toMutableMap()
        val folders = folderState.folders.map { folder ->
            val addedAt = updatedAddedAt.getOrPut(folder.id) { now }
            folder.copy(
                addedAt = addedAt,
                pinned = folder.id in pinnedIds,
            )
        }
        val books = folderState.books.map { book ->
            val addedAt = updatedAddedAt.getOrPut(book.id) { now }
            book.copy(
                addedAt = addedAt,
                pinned = book.id in pinnedIds,
            )
        }

        val savedAddedAt = updatedAddedAt.toMap()
        libraryAddedAtCache = savedAddedAt
        if (savedAddedAt != addedAtById) {
            ReaderStateStore.saveLibraryAddedAt(context, savedAddedAt)
        }

        return folderState.copy(
            folders = sortLibraryFolders(folders, sortMode),
            books = sortLibraryBooks(
                books = books,
                sortMode = sortMode,
                progressByUri = progressByUri,
                recents = recents,
                bookStatusByUri = bookStatusByUri,
            ),
        )
    }

}
