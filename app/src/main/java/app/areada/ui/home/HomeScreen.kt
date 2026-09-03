package app.areada.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.areada.R
import app.areada.data.BookStatus
import app.areada.data.BookNoteLink
import app.areada.data.reader.DocumentType
import app.areada.data.library.LibraryBookEntry
import app.areada.data.library.LibraryFileFilter
import app.areada.data.library.LibraryFolderEntry
import app.areada.data.library.LibraryFolderPickerEntry
import app.areada.data.library.LibraryRoot
import app.areada.data.library.LibrarySearchResult
import app.areada.data.library.LibrarySortMode
import app.areada.data.library.sortReadingBookmarks
import app.areada.data.library.sortRecentDocuments
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReadingBookmark
import app.areada.data.reader.ReadingProgress
import app.areada.data.reader.RecentDocument
import app.areada.data.effectiveBookStatus
import app.areada.data.hasBookNote
import app.areada.ui.reader.BookRow
import app.areada.ui.reader.FolderRow
import app.areada.ui.reader.archiveFormatLabel
import app.areada.ui.reader.ReaderSettingsSheet
import app.areada.ui.reader.CompactChoiceDialog
import app.areada.ui.reader.ConfirmDeleteDialog
import app.areada.ui.reader.EmptyStateCard
import app.areada.ui.reader.HeaderIconButton
import app.areada.ui.reader.LibraryScrollPosition
import app.areada.ui.reader.NotePopup
import kotlinx.coroutines.flow.collect

private data class GroupHeader(
    val title: String,
    val count: Int,
    val type: DocumentType?,
)

@Composable
internal fun HomeScreen(
    roots: List<LibraryRoot>,
    folderPickerEntries: List<LibraryFolderPickerEntry>,
    selectedRootUriString: String?,
    currentRelativePath: String,
    folders: List<LibraryFolderEntry>,
    books: List<LibraryBookEntry>,
    searchQuery: String,
    searchResults: List<LibrarySearchResult>,
    isSearching: Boolean,
    recents: List<RecentDocument>,
    bookmarks: List<ReadingBookmark>,
    preferences: ReaderPreferences,
    sortMode: LibrarySortMode,
    fileFilter: LibraryFileFilter,
    selectedHomeTabName: String,
    folderDocumentTypesById: Map<String, Set<DocumentType>>,
    progressByUri: Map<String, ReadingProgress>,
    bookStatusByUri: Map<String, BookStatus>,
    bookNoteLinksByUri: Map<String, BookNoteLink>,
    pinnedLibraryItemIds: Set<String>,
    libraryScrollPositions: MutableMap<String, LibraryScrollPosition>,
    onChooseFolder: () -> Unit,
    onOpenFile: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRoot: (LibraryRoot) -> Unit,
    onRemoveRoot: (LibraryRoot) -> Unit,
    onOpenPickerEntry: (LibraryFolderPickerEntry) -> Unit,
    onCreateTextNote: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenSearchResult: (LibrarySearchResult) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenBook: (LibraryBookEntry) -> Unit,
    onOpenRecent: (RecentDocument) -> Unit,
    onOpenBookmark: (ReadingBookmark) -> Unit,
    onRemoveBookmark: (ReadingBookmark) -> Unit,
    onRemoveRecent: (RecentDocument) -> Unit,
    onMoveBookmark: (ReadingBookmark, Int) -> Unit,
    onMoveRecent: (RecentDocument, Int) -> Unit,
    onRenameBookmark: (ReadingBookmark, String) -> Unit,
    onSortModeChange: (LibrarySortMode) -> Unit,
    onFileFilterChange: (LibraryFileFilter) -> Unit,
    onHomeTabChange: (String) -> Unit,
    onDeleteFolder: (LibraryFolderEntry) -> Unit,
    onDeleteBook: (LibraryBookEntry) -> Unit,
    onRenameFolder: (LibraryFolderEntry, String) -> Unit,
    onRenameBook: (LibraryBookEntry, String) -> Unit,
    onTogglePinFolder: (LibraryFolderEntry) -> Unit,
    onTogglePinBook: (LibraryBookEntry) -> Unit,
    onTogglePinDocument: (String) -> Unit,
    onUpdateBookStatus: (String, BookStatus) -> Unit,
    onPreferencesChange: (ReaderPreferences) -> Unit,
) {
    var showSettings by rememberSaveable {
        mutableStateOf(false)
    }
    var showManageFolders by rememberSaveable {
        mutableStateOf(false)
    }
    var showFolderPicker by rememberSaveable {
        mutableStateOf(false)
    }
    var showFileFilter by rememberSaveable {
        mutableStateOf(false)
    }
    var showSortMenu by rememberSaveable {
        mutableStateOf(false)
    }
    var searchFocused by rememberSaveable {
        mutableStateOf(false)
    }
    var selectedHomeTab by rememberSaveable {
        mutableStateOf(homeTabFromName(selectedHomeTabName))
    }
    var scrollToTopRequest by rememberSaveable {
        mutableIntStateOf(0)
    }
    var showTutorialPrompt by rememberSaveable {
        mutableStateOf(false)
    }
    var actionTarget by remember {
        mutableStateOf<LibraryActionTarget?>(null)
    }
    var renameTarget by remember {
        mutableStateOf<LibraryActionTarget?>(null)
    }
    var deleteTarget by remember {
        mutableStateOf<LibraryActionTarget?>(null)
    }
    var bookInfoTarget by remember {
        mutableStateOf<LibraryBookEntry?>(null)
    }
    var bookmarkActionTarget by remember {
        mutableStateOf<ReadingBookmark?>(null)
    }
    var recentActionTarget by remember {
        mutableStateOf<RecentDocument?>(null)
    }
    var bookmarkRemovalTarget by remember {
        mutableStateOf<ReadingBookmark?>(null)
    }
    var bookmarkGroupActionTarget by remember {
        mutableStateOf<String?>(null)
    }
    var bookmarkGroupRemovalTarget by remember {
        mutableStateOf<String?>(null)
    }
    var bookmarkRenameTarget by remember {
        mutableStateOf<ReadingBookmark?>(null)
    }
    var bookmarkRenameText by rememberSaveable {
        mutableStateOf("")
    }
    var recentRemovalTarget by remember {
        mutableStateOf<RecentDocument?>(null)
    }
    var renameText by rememberSaveable {
        mutableStateOf("")
    }
    val selectedItemIds = remember {
        mutableStateMapOf<String, Boolean>()
    }
    var batchConfirmAction by remember {
        mutableStateOf<String?>(null)
    }
    val isSelectionMode by remember {
        derivedStateOf { selectedItemIds.isNotEmpty() }
    }

    BackHandler(enabled = showFolderPicker) {
        showFolderPicker = false
    }

    BackHandler(enabled = showFileFilter) {
        showFileFilter = false
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    fun clearSearchFocus() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        searchFocused = false
    }

    BackHandler(enabled = showSortMenu) {
        showSortMenu = false
    }
    BackHandler(enabled = searchFocused) {
        clearSearchFocus()
    }
    BackHandler(enabled = isSelectionMode) {
        selectedItemIds.clear()
    }

    fun batchUpdateBookStatus(status: BookStatus) {
        selectedItemIds.keys.forEach { key ->
            when (selectedHomeTab) {
                HomeTab.Reading -> onUpdateBookStatus(key, status)
                HomeTab.Bookmarks -> {
                    val bookmark = bookmarks.firstOrNull { "bm:${it.id}" == key }
                    bookmark?.let { onUpdateBookStatus(it.uriString, status) }
                }
                HomeTab.Collection -> {
                    val book = books.firstOrNull { "book:${it.id}" == key }
                    book?.let { onUpdateBookStatus(it.uriString, status) }
                }
            }
        }
    }

    fun batchTogglePin() {
        selectedItemIds.keys.forEach { key ->
            when (selectedHomeTab) {
                HomeTab.Reading -> onTogglePinDocument(key)
                HomeTab.Bookmarks -> {
                    val bookmark = bookmarks.firstOrNull { "bm:${it.id}" == key }
                    bookmark?.let { onTogglePinDocument(it.id) }
                }
                HomeTab.Collection -> {
                    val folder = folders.firstOrNull { "folder:${it.id}" == key }
                    if (folder != null) { onTogglePinFolder(folder); return@forEach }
                    val book = books.firstOrNull { "book:${it.id}" == key }
                    book?.let { onTogglePinBook(it) }
                }
            }
        }
    }

    fun batchRemove() {
        selectedItemIds.keys.forEach { key ->
            when (selectedHomeTab) {
                HomeTab.Reading -> {
                    val recent = recents.firstOrNull { it.uriString == key }
                    recent?.let { onRemoveRecent(it) }
                }
                HomeTab.Bookmarks -> {
                    val bookmark = bookmarks.firstOrNull { "bm:${it.id}" == key }
                    bookmark?.let { onRemoveBookmark(it) }
                }
                else -> {}
            }
        }
    }

    fun batchDelete() {
        selectedItemIds.keys.forEach { key ->
            val folder = folders.firstOrNull { "folder:${it.id}" == key }
            if (folder != null) { onDeleteFolder(folder); return@forEach }
            val book = books.firstOrNull { "book:${it.id}" == key }
            book?.let { onDeleteBook(it) }
        }
    }

    LaunchedEffect(selectedRootUriString, currentRelativePath) {
        clearSearchFocus()
    }

    if (showSettings) {
        ReaderSettingsSheet(
            preferences = preferences,
            showReadingControls = false,
            showLanguageSelector = true,
            showGuideIconToggle = true,
            onDismiss = { showSettings = false },
            onPreferencesChange = onPreferencesChange,
        )
    }

    if (showManageFolders) {
        ManageFoldersSheet(
            roots = roots,
            selectedRootUriString = selectedRootUriString,
            onDismiss = { showManageFolders = false },
            onRemoveRoot = onRemoveRoot,
        )
    }

    val currentActionTarget = actionTarget?.let { target ->
        when (target) {
            is LibraryActionTarget.Folder -> folders
                .firstOrNull { folder -> folder.id == target.folder.id }
                ?.let { folder -> LibraryActionTarget.Folder(folder) }
                ?: target

            is LibraryActionTarget.Book -> books
                .firstOrNull { book -> book.id == target.book.id }
                ?.let { book -> LibraryActionTarget.Book(book) }
                ?: target
        }
    }

    currentActionTarget?.let { target ->
        val targetBookStatus = (target as? LibraryActionTarget.Book)?.book?.uriString?.let { uriString ->
            effectiveBookStatus(bookStatusByUri[uriString], progressByUri[uriString])
        }
        LibraryActionSheet(
            target = target,
            bookStatus = targetBookStatus,
            onDismiss = { actionTarget = null },
            onDelete = {
                deleteTarget = target
                actionTarget = null
            },
            onRename = {
                renameTarget = target
                renameText = target.displayName
                actionTarget = null
            },
            onTogglePin = {
                when (target) {
                    is LibraryActionTarget.Folder -> onTogglePinFolder(target.folder)
                    is LibraryActionTarget.Book -> onTogglePinBook(target.book)
                }
                actionTarget = null
            },
            onShowInfo = if (target is LibraryActionTarget.Book) {
                {
                    bookInfoTarget = target.book
                    actionTarget = null
                }
            } else {
                null
            },
            onMarkBookStatus = if (target is LibraryActionTarget.Book && target.book.type != DocumentType.ARCHIVE) {
                { status ->
                    onUpdateBookStatus(target.book.uriString, status)
                    actionTarget = null
                }
            } else {
                null
            },
        )
    }

    bookInfoTarget?.let { targetBook ->
        val currentBook = books.firstOrNull { book -> book.id == targetBook.id } ?: targetBook
        val progress = progressByUri[currentBook.uriString]
        BookInfoSheet(
            book = currentBook,
            progress = progress,
            status = effectiveBookStatus(bookStatusByUri[currentBook.uriString], progress),
            recent = recents.firstOrNull { recent -> recent.uriString == currentBook.uriString },
            onDismiss = { bookInfoTarget = null },
            onMarkStatus = { status ->
                onUpdateBookStatus(currentBook.uriString, status)
                bookInfoTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            onDismiss = { deleteTarget = null },
            onConfirm = {
                when (target) {
                    is LibraryActionTarget.Folder -> onDeleteFolder(target.folder)
                    is LibraryActionTarget.Book -> onDeleteBook(target.book)
                }
                deleteTarget = null
            },
        )
    }

    recentActionTarget?.let { recent ->
        val index = recents.indexOfFirst { item -> item.uriString == recent.uriString }
        DocumentListActionSheet(
            title = recent.title,
            pinned = recent.uriString in pinnedLibraryItemIds,
            canMoveUp = index > 0,
            canMoveDown = index >= 0 && index < recents.lastIndex,
            bookStatus = effectiveBookStatus(bookStatusByUri[recent.uriString], progressByUri[recent.uriString]),
            onMarkBookStatus = { status ->
                onUpdateBookStatus(recent.uriString, status)
                recentActionTarget = null
            },
            onDismiss = { recentActionTarget = null },
            onTogglePin = {
                onTogglePinDocument(recent.uriString)
                recentActionTarget = null
            },
            onMoveUp = {
                onMoveRecent(recent, -1)
                recentActionTarget = null
            },
            onMoveDown = {
                onMoveRecent(recent, 1)
                recentActionTarget = null
            },
            onRemove = {
                recentRemovalTarget = recent
                recentActionTarget = null
            },
        )
    }

    bookmarkActionTarget?.let { bookmark ->
        val index = bookmarks.indexOfFirst { item -> item.id == bookmark.id }
        DocumentListActionSheet(
            title = bookmark.title,
            pinned = bookmark.id in pinnedLibraryItemIds,
            canMoveUp = index > 0,
            canMoveDown = index >= 0 && index < bookmarks.lastIndex,
            bookStatus = effectiveBookStatus(bookStatusByUri[bookmark.uriString], progressByUri[bookmark.uriString]),
            onMarkBookStatus = { status ->
                onUpdateBookStatus(bookmark.uriString, status)
                bookmarkActionTarget = null
            },
            onDismiss = { bookmarkActionTarget = null },
            onTogglePin = {
                onTogglePinDocument(bookmark.id)
                bookmarkActionTarget = null
            },
            onMoveUp = {
                onMoveBookmark(bookmark, -1)
                bookmarkActionTarget = null
            },
            onMoveDown = {
                onMoveBookmark(bookmark, 1)
                bookmarkActionTarget = null
            },
            onRemove = {
                bookmarkRemovalTarget = bookmark
                bookmarkActionTarget = null
            },
            onRename = {
                bookmarkRenameText = bookmark.customName ?: bookmark.positionLabel
                bookmarkRenameTarget = bookmark
                bookmarkActionTarget = null
            },
        )
    }

    bookmarkRemovalTarget?.let { bookmark ->
        CompactChoiceDialog(
            question = stringResource(R.string.remove_question),
            onDismiss = { bookmarkRemovalTarget = null },
            onYes = {
                onRemoveBookmark(bookmark)
                bookmarkRemovalTarget = null
            },
        )
    }

    recentRemovalTarget?.let { recent ->
        CompactChoiceDialog(
            question = stringResource(R.string.remove_question),
            onDismiss = { recentRemovalTarget = null },
            onYes = {
                onRemoveRecent(recent)
                recentRemovalTarget = null
            },
        )
    }

    bookmarkRenameTarget?.let { bookmark ->
        RenameDialog(
            name = bookmarkRenameText,
            onNameChange = { bookmarkRenameText = it },
            onDismiss = { bookmarkRenameTarget = null },
            onConfirm = {
                onRenameBookmark(bookmark, bookmarkRenameText)
                bookmarkRenameTarget = null
            },
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            name = renameText,
            onNameChange = { renameText = it },
            onDismiss = { renameTarget = null },
            onConfirm = {
                when (target) {
                    is LibraryActionTarget.Folder -> onRenameFolder(target.folder, renameText)
                    is LibraryActionTarget.Book -> onRenameBook(target.book, renameText)
                }
                renameTarget = null
            },
        )
    }

    val visibleBooks = remember(books, fileFilter) {
        books.filterBooksByLibraryFileFilter(fileFilter)
    }
    val duplicateVisibleBookTitleKeys = remember(visibleBooks) {
        visibleBooks
            .groupingBy { book -> book.title.trim().lowercase() }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
    }
    val visibleFolders = remember(folders, fileFilter, folderDocumentTypesById) {
        folders.filterFoldersByLibraryFileFilter(fileFilter, folderDocumentTypesById)
    }
    val visibleSearchResults = remember(searchResults, fileFilter, folderDocumentTypesById) {
        searchResults.filterSearchResultsByLibraryFileFilter(fileFilter, folderDocumentTypesById)
    }
    val visibleBookmarks = remember(bookmarks, fileFilter, sortMode, pinnedLibraryItemIds) {
        bookmarks
            .filterBookmarksByLibraryFileFilter(fileFilter)
            .let { sortReadingBookmarks(it, sortMode) }
            .sortedByDescending { it.id in pinnedLibraryItemIds }
    }
    val visibleRecents = remember(recents, fileFilter, sortMode, progressByUri, pinnedLibraryItemIds) {
        recents
            .filterRecentsByLibraryFileFilter(fileFilter)
            .let { sortRecentDocuments(it, sortMode, progressByUri) }
            .sortedByDescending { it.uriString in pinnedLibraryItemIds }
    }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && selectedHomeTab != HomeTab.Collection) {
            selectedHomeTab = HomeTab.Collection
        }
    }
    LaunchedEffect(selectedHomeTabName) {
        val savedTab = homeTabFromName(selectedHomeTabName)
        if (searchQuery.isBlank() && selectedHomeTab != savedTab) {
            selectedHomeTab = savedTab
        }
    }
    val collectionFallback = stringResource(R.string.collection)
    val collectionTitle = remember(currentRelativePath, collectionFallback) {
        currentRelativePath
            .trim()
            .replace('\\', '/')
            .split('/')
            .lastOrNull { segment -> segment.isNotBlank() }
            ?: collectionFallback
    }
    val libraryScrollKey = remember(
        selectedRootUriString,
        currentRelativePath,
        fileFilter,
        sortMode,
        selectedHomeTab,
    ) {
        listOf(
            selectedRootUriString.orEmpty(),
            currentRelativePath,
            fileFilter.name,
            sortMode.name,
            selectedHomeTab.name,
        ).joinToString(separator = "\u001F")
    }

    key(libraryScrollKey) {
        val savedScrollPosition = libraryScrollPositions[libraryScrollKey]
        val libraryListState = rememberLazyListState(
            initialFirstVisibleItemIndex = savedScrollPosition?.firstVisibleItemIndex ?: 0,
            initialFirstVisibleItemScrollOffset = savedScrollPosition?.firstVisibleItemScrollOffset ?: 0,
        )
        val estimatedLazyItemCount = remember(
            selectedHomeTab,
            visibleRecents,
            visibleBookmarks,
            visibleSearchResults,
            searchQuery,
            selectedRootUriString,
            roots,
            visibleFolders,
            visibleBooks,
        ) {
            var count = 2
            when (selectedHomeTab) {
                HomeTab.Collection -> {
                    if (searchQuery.isNotBlank()) {
                        if (selectedRootUriString != null && roots.isNotEmpty()) {
                            count += 1
                        }
                        count += if (visibleSearchResults.isEmpty()) 1 else visibleSearchResults.size.coerceAtMost(41)
                    } else if (selectedRootUriString != null && roots.isNotEmpty()) {
                        count += 1
                        count += visibleFolders.size
                        count += visibleBooks.size
                        if (visibleFolders.isEmpty() && visibleBooks.isEmpty()) {
                            count += 1
                        }
                    }
                }

                HomeTab.Reading -> {
                    count += if (visibleRecents.isEmpty()) 1 else visibleRecents.size
                }

                HomeTab.Bookmarks -> {
                    count += if (visibleBookmarks.isEmpty()) 1 else visibleBookmarks.size.coerceAtMost(20)
                }
            }
            count.coerceAtLeast(1)
        }

        DisposableEffect(libraryScrollKey, libraryListState) {
            onDispose {
                libraryScrollPositions[libraryScrollKey] = LibraryScrollPosition(
                    firstVisibleItemIndex = libraryListState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = libraryListState.firstVisibleItemScrollOffset,
                )
            }
        }

        LaunchedEffect(libraryScrollKey, estimatedLazyItemCount) {
            if (libraryListState.firstVisibleItemIndex >= estimatedLazyItemCount) {
                libraryListState.scrollToItem(estimatedLazyItemCount - 1)
            }
        }

        LaunchedEffect(scrollToTopRequest) {
            if (scrollToTopRequest > 0) {
                libraryListState.animateScrollToItem(0)
            }
        }

        LaunchedEffect(libraryListState, searchFocused) {
            snapshotFlow { libraryListState.isScrollInProgress }
                .collect { isScrolling ->
                    if (isScrolling && searchFocused) {
                        clearSearchFocus()
                    }
                }
        }

    val homeBackground = MaterialTheme.colorScheme.background

    val groupedBookmarks = remember(visibleBookmarks) {
        visibleBookmarks.take(20).groupBy { it.title }
    }
    val expandedBookmarkGroups = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        containerColor = homeBackground,
    ) { paddingValues ->
    val flatBookmarkItems by remember(visibleBookmarks) {
        derivedStateOf {
            val result = mutableListOf<Any>()
            groupedBookmarks.forEach { (bookTitle, bookBookmarks) ->
                val isExpanded = expandedBookmarkGroups[bookTitle] ?: false
                result.add(GroupHeader(bookTitle, bookBookmarks.size, bookBookmarks.firstOrNull()?.type))
                if (isExpanded) {
                    result.addAll(bookBookmarks)
                }
            }
            result
        }
    }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(homeBackground),
        ) {
            LazyColumn(
                state = libraryListState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(homeBackground)
                    .padding(paddingValues)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
            item {
                val headerActionColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (roots.isNotEmpty()) {
                            HeaderIconButton(onClick = onRefresh) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = stringResource(R.string.refresh_library),
                                    modifier = Modifier.size(24.dp),
                                    tint = headerActionColor,
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (selectedRootUriString != null) {
                            Text(
                                text = stringResource(R.string.add_note),
                                modifier = Modifier
                                    .offset(y = 5.dp)
                                    .clickable(onClick = onCreateTextNote)
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = headerActionColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (preferences.showGuideIcon) {
                            HeaderIconButton(onClick = { showTutorialPrompt = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = stringResource(R.string.quick_guide_title),
                                    modifier = Modifier.size(24.dp),
                                    tint = headerActionColor,
                                )
                            }
                        }
                        HeaderIconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.settings),
                                modifier = Modifier.size(24.dp),
                                tint = headerActionColor,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SwipeActionBox(
                        actionLabel = stringResource(R.string.open_file),
                        onSwipe = onOpenFile,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        actionContainerColor = MaterialTheme.colorScheme.surface,
                        actionContentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Button(
                            onClick = onChooseFolder,
                            modifier = Modifier.fillMaxSize(),
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CreateNewFolder,
                                    contentDescription = stringResource(R.string.choose_folder),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.choose_folder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    SwipeActionBox(
                        actionLabel = stringResource(R.string.manage),
                        onSwipe = { showManageFolders = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        actionContainerColor = MaterialTheme.colorScheme.primary,
                        actionContentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        FolderPickerDropdown(
                            entries = folderPickerEntries,
                            selectedRootUriString = selectedRootUriString,
                            currentRelativePath = currentRelativePath,
                            expanded = showFolderPicker,
                            onToggleExpanded = {
                                if (folderPickerEntries.isEmpty()) {
                                    onChooseFolder()
                                } else {
                                    showFolderPicker = !showFolderPicker
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (showFolderPicker && folderPickerEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FolderPickerInlinePanel(
                        entries = folderPickerEntries,
                        selectedRootUriString = selectedRootUriString,
                        currentRelativePath = currentRelativePath,
                        onSelectEntry = { entry ->
                            clearSearchFocus()
                            showFolderPicker = false
                            onOpenPickerEntry(entry)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchBar(
                        query = searchQuery,
                        isSearching = isSearching,
                        onQueryChange = onSearchQueryChange,
                        onFocusChanged = { focused -> searchFocused = focused },
                        modifier = Modifier.weight(1f),
                    )
                    LibrarySortButton(
                        sortMode = sortMode,
                        expanded = showSortMenu,
                        onClick = { showSortMenu = !showSortMenu },
                    )
                    LibraryFilterButton(
                        filter = fileFilter,
                        expanded = showFileFilter,
                        onClick = { showFileFilter = !showFileFilter },
                    )
                }
                if (showSortMenu) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LibrarySortInlinePanel(
                        selectedSortMode = sortMode,
                        onSelectSortMode = { selectedSortMode ->
                            onSortModeChange(selectedSortMode)
                            showSortMenu = false
                        },
                    )
                }
                if (showFileFilter) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LibraryFilterInlinePanel(
                        selectedFilter = fileFilter,
                        onSelectFilter = { filter ->
                            onFileFilterChange(filter)
                            showFileFilter = false
                        },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HomeTabRow(
                    selectedTab = selectedHomeTab,
                    readingCount = visibleRecents.size,
                    bookmarkCount = visibleBookmarks.size,
                    collectionLabel = collectionTitle,
                    onSelectTab = { tab ->
                        if (selectedHomeTab == tab) {
                            scrollToTopRequest += 1
                        } else {
                            selectedItemIds.clear()
                            selectedHomeTab = tab
                            onHomeTabChange(tab.name)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (selectedHomeTab == HomeTab.Reading) {
                if (visibleRecents.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = stringResource(R.string.no_reading_title),
                            body = stringResource(R.string.no_reading_body),
                        )
                    }
                } else {
                    items(
                        items = visibleRecents,
                        key = { recent -> "recent:${recent.uriString}" },
                    ) { recent ->
                        val recentKey = recent.uriString
                        val isSelected = recentKey in selectedItemIds
                        SwipeActionBox(
                            actionLabel = stringResource(R.string.actions),
                            onSwipe = { recentActionTarget = recent },
                            onSwipeEndToStart = {
                                if (isSelected) {
                                    selectedItemIds.remove(recentKey)
                                } else {
                                    selectedItemIds[recentKey] = true
                                }
                            },
                        ) {
                            BookRow(
                                title = recent.title,
                                type = recent.type,
                                typeLabel = if (recent.type == DocumentType.ARCHIVE) {
                                    archiveFormatLabel(recent.uriString.substringAfterLast('/'))
                                } else null,
                                progressLabel = bookRowProgressLabel(
                                    type = recent.type,
                                    progress = progressByUri[recent.uriString],
                                    status = bookStatusByUri[recent.uriString],
                                ),
                                pinned = recent.uriString in pinnedLibraryItemIds,
                                hasNote = hasBookNote(recent.uriString, bookNoteLinksByUri),
                                selected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedItemIds.remove(recentKey)
                                    } else {
                                        onOpenRecent(recent)
                                    }
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            if (selectedHomeTab == HomeTab.Bookmarks) {
                if (visibleBookmarks.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = stringResource(R.string.no_bookmarks_title),
                            body = stringResource(R.string.no_bookmarks_body),
                        )
                    }
                } else {
                    items(
                        items = flatBookmarkItems,
                        key = { item ->
                            when (item) {
                                is GroupHeader -> "group:${item.title}"
                                is ReadingBookmark -> "bookmark:${item.id}"
                                else -> "unknown"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is GroupHeader -> {
                                val isExpanded = expandedBookmarkGroups[item.title] ?: false
                                val groupBookmarks = groupedBookmarks[item.title] ?: emptyList()
                                val groupKey = "group:${item.title}"
                                val isGroupSelected = groupKey in selectedItemIds
                                SwipeActionBox(
                                    actionLabel = stringResource(R.string.actions),
                                    onSwipe = { bookmarkGroupActionTarget = item.title },
                                    onSwipeEndToStart = {
                                        if (isGroupSelected) {
                                            selectedItemIds.remove(groupKey)
                                            groupBookmarks.forEach { bm ->
                                                selectedItemIds.remove("bm:${bm.id}")
                                            }
                                        } else {
                                            selectedItemIds[groupKey] = true
                                            groupBookmarks.forEach { bm ->
                                                selectedItemIds["bm:${bm.id}"] = true
                                            }
                                        }
                                    },
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedBookmarkGroups[item.title] = !isExpanded
                                            },
                                        shape = RectangleShape,
                                        color = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = when (item.type) {
                                                    DocumentType.EPUB -> Icons.Outlined.ImportContacts
                                                    DocumentType.PDF -> Icons.Outlined.PictureAsPdf
                                                    DocumentType.TXT -> Icons.Outlined.Description
                                                    DocumentType.MARKDOWN -> Icons.Outlined.Description
                                                    else -> Icons.AutoMirrored.Outlined.LibraryBooks
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = item.title,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${item.count}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Icon(
                                                imageVector = if (isExpanded) {
                                                    Icons.Outlined.KeyboardArrowUp
                                                } else {
                                                    Icons.Outlined.KeyboardArrowDown
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            is ReadingBookmark -> {
                                val bookmark = item
                                val bookmarkKey = "bm:${bookmark.id}"
                                val isSelected = bookmarkKey in selectedItemIds
                                BookmarkRow(
                                    bookmark = bookmark,
                                    pinned = bookmark.id in pinnedLibraryItemIds,
                                    hasNote = hasBookNote(bookmark.uriString, bookNoteLinksByUri),
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedItemIds.remove(bookmarkKey)
                                        } else {
                                            onOpenBookmark(bookmark)
                                        }
                                    },
                                    onActions = { bookmarkActionTarget = bookmark },
                                    onSelect = {
                                        if (isSelected) {
                                            selectedItemIds.remove(bookmarkKey)
                                        } else {
                                            selectedItemIds[bookmarkKey] = true
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            if (selectedHomeTab == HomeTab.Collection) {
                if (searchQuery.isNotBlank()) {
                    item {
                        SearchResults(
                            results = visibleSearchResults,
                            isSearching = isSearching,
                            onOpenResult = onOpenSearchResult,
                        )
                    }
                } else if (selectedRootUriString == null || roots.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = stringResource(R.string.no_folder_selected_title),
                            body = stringResource(R.string.no_folder_selected_body),
                            actionLabel = stringResource(R.string.choose_folder),
                            onAction = onChooseFolder,
                        )
                    }
                } else {
                    if (visibleFolders.isNotEmpty()) {
                        items(
                            items = visibleFolders,
                            key = { folder -> "folder:${folder.id}" },
                        ) { folder ->
                            val folderKey = "folder:${folder.id}"
                            val isSelected = folderKey in selectedItemIds
                            SwipeActionBox(
                                actionLabel = stringResource(R.string.actions),
                                onSwipe = { actionTarget = LibraryActionTarget.Folder(folder) },
                                onSwipeEndToStart = {
                                    if (isSelected) {
                                        selectedItemIds.remove(folderKey)
                                    } else {
                                        selectedItemIds[folderKey] = true
                                    }
                                },
                            ) {
                                FolderRow(
                                    name = folder.name,
                                    pinned = folder.pinned,
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedItemIds.remove(folderKey)
                                        } else {
                                            onOpenFolder(folder.relativePath)
                                        }
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (visibleBooks.isNotEmpty()) {
                        items(
                            items = visibleBooks,
                            key = { book -> "book:${book.id}" },
                        ) { book ->
                            val bookKey = "book:${book.id}"
                            val isSelected = bookKey in selectedItemIds
                            SwipeActionBox(
                                actionLabel = stringResource(R.string.actions),
                                onSwipe = { actionTarget = LibraryActionTarget.Book(book) },
                                onSwipeEndToStart = {
                                    if (isSelected) {
                                        selectedItemIds.remove(bookKey)
                                    } else {
                                        selectedItemIds[bookKey] = true
                                    }
                                },
                            ) {
                                BookRow(
                                    title = book.title,
                                    type = book.type,
                                    typeLabel = if (book.type == DocumentType.ARCHIVE) {
                                        archiveFormatLabel(book.fileName)
                                    } else null,
                                    progressLabel = bookRowProgressLabel(
                                        type = book.type,
                                        progress = progressByUri[book.uriString],
                                        status = bookStatusByUri[book.uriString],
                                    ),
                                    pinned = book.pinned,
                                    hasNote = hasBookNote(book.uriString, bookNoteLinksByUri),
                                    selected = isSelected,
                                    extraMetadata = if (book.title.trim().lowercase() in duplicateVisibleBookTitleKeys) {
                                        stringResource(R.string.duplicate_name)
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedItemIds.remove(bookKey)
                                        } else {
                                            onOpenBook(book)
                                        }
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else if (visibleFolders.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = stringResource(R.string.no_books_found_title),
                                body = fileFilter.emptyLibraryMessage(),
                            )
                        }
                    }
                }
            }
        }

            if (showTutorialPrompt) {
                val rawGuide = stringResource(R.string.home_tutorial_note)
                val tutorialNote = remember(rawGuide) {
                    buildAnnotatedString {
                        val lines = rawGuide.split("\n")
                        for (i in lines.indices) {
                            val line = lines[i]
                            if (line.startsWith("##")) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(line.removePrefix("##"))
                                }
                            } else {
                                var remaining = line
                                while (true) {
                                    val start = remaining.indexOf("**")
                                    if (start == -1) {
                                        append(remaining)
                                        break
                                    }
                                    if (start > 0) {
                                        append(remaining.substring(0, start))
                                    }
                                    val end = remaining.indexOf("**", start + 2)
                                    if (end == -1) {
                                        append(remaining.substring(start))
                                        break
                                    }
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(remaining.substring(start + 2, end))
                                    }
                                    remaining = remaining.substring(end + 2)
                                }
                            }
                            if (i < lines.lastIndex) append("\n")
                        }
                    }
                }
                NotePopup(
                    title = stringResource(R.string.quick_guide_title),
                    note = tutorialNote,
                    onClose = { showTutorialPrompt = false },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp),
                )
            }
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
            ) {
                val hasMarkableItems = when (selectedHomeTab) {
                    HomeTab.Collection -> selectedItemIds.keys.all { key ->
                        if (!key.startsWith("book:")) return@all false
                        val bookId = key.removePrefix("book:")
                        val book = books.firstOrNull { it.id == bookId }
                        book != null && book.type != DocumentType.ZIP && book.type != DocumentType.ARCHIVE
                    }
                    HomeTab.Reading -> selectedItemIds.keys.any { key ->
                        bookStatusByUri[key] != BookStatus.Finished
                    }
                    HomeTab.Bookmarks -> false
                }
                BatchActionBar(
                    selectedCount = selectedItemIds.size,
                    isCollectionTab = selectedHomeTab == HomeTab.Collection,
                    onClearSelection = { selectedItemIds.clear() },
                    onBatchMarkFinished = if (hasMarkableItems) {
                        { batchUpdateBookStatus(BookStatus.Finished); selectedItemIds.clear() }
                    } else null,
                    onBatchMarkReading = if (hasMarkableItems && selectedHomeTab == HomeTab.Collection) {
                        { batchUpdateBookStatus(BookStatus.Reading); selectedItemIds.clear() }
                    } else null,
                    onBatchPin = {
                        batchTogglePin()
                        selectedItemIds.clear()
                    },
                    onBatchRemove = if (selectedHomeTab != HomeTab.Collection) {
                        { batchConfirmAction = "remove" }
                    } else null,
                    onBatchDelete = if (selectedHomeTab == HomeTab.Collection) {
                        { batchConfirmAction = "delete" }
                    } else null,
                )
            }
        }
        when (batchConfirmAction) {
            "delete" -> ConfirmDeleteDialog(
                onDismiss = { batchConfirmAction = null },
                onConfirm = {
                    batchDelete()
                    selectedItemIds.clear()
                    batchConfirmAction = null
                },
            )
            "remove" -> CompactChoiceDialog(
                question = stringResource(R.string.remove_question),
                onDismiss = { batchConfirmAction = null },
                onYes = {
                    batchRemove()
                    selectedItemIds.clear()
                    batchConfirmAction = null
                },
            )
        }
    }

    bookmarkGroupActionTarget?.let { groupTitle ->
        val groupBookmarks = groupedBookmarks[groupTitle] ?: emptyList()
        val anyPinned = groupBookmarks.any { it.id in pinnedLibraryItemIds }
        DocumentListActionSheet(
            title = groupTitle,
            pinned = anyPinned,
            canMoveUp = false,
            canMoveDown = false,
            onDismiss = { bookmarkGroupActionTarget = null },
            onTogglePin = {
                groupBookmarks.forEach { bm ->
                    onTogglePinDocument(bm.id)
                }
                bookmarkGroupActionTarget = null
            },
            onMoveUp = {},
            onMoveDown = {},
            onRemove = {
                bookmarkGroupRemovalTarget = groupTitle
                bookmarkGroupActionTarget = null
            },
        )
    }

    bookmarkGroupRemovalTarget?.let { groupTitle ->
        val groupBookmarks = groupedBookmarks[groupTitle] ?: emptyList()
        CompactChoiceDialog(
            question = stringResource(R.string.remove_question),
            onDismiss = { bookmarkGroupRemovalTarget = null },
            onYes = {
                groupBookmarks.forEach { bm ->
                    onRemoveBookmark(bm)
                }
                bookmarkGroupRemovalTarget = null
            },
        )
    }
}
}
