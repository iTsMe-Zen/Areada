package app.areada.ui

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.areada.R
import app.areada.data.BookStatus
import app.areada.data.ZipBookEntry
import app.areada.data.reader.ReaderLanguageMode
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReaderStateStore
import app.areada.ui.home.HomeScreen
import app.areada.ui.reader.AppWindowBackgroundEffect
import app.areada.ui.reader.EpubReaderScreen
import app.areada.ui.reader.Fb2ReaderScreen
import app.areada.ui.reader.ErrorBanner
import app.areada.ui.reader.ExitPromptDialog
import app.areada.ui.reader.LibraryScrollPosition
import app.areada.ui.reader.LoadingOverlay
import app.areada.ui.reader.PdfReaderScreen
import app.areada.ui.reader.ReaderComfortOverlay
import app.areada.ui.reader.ReaderOrientationEffect
import app.areada.ui.reader.ReaderScreen
import app.areada.ui.reader.ReaderViewModel
import app.areada.ui.reader.TextReaderScreen
import app.areada.ui.reader.ZipEntryPickerDialog
import app.areada.ui.theme.ReaderTheme
import app.areada.ui.reader.findActivity
import java.util.Locale

@Composable
fun AreadaApp(
    externalOpenUri: Uri? = null,
    onExternalOpenHandled: () -> Unit = {},
    viewModel: ReaderViewModel = viewModel(),
) {
    val localContext = LocalContext.current
    val context = localContext.applicationContext
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitPrompt by rememberSaveable {
        mutableStateOf(false)
    }
    var openFileLaunchToken by remember {
        mutableIntStateOf(0)
    }
    var filePickerInFlight by rememberSaveable {
        mutableStateOf(false)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { pickedUri ->
            viewModel.addLibraryRoot(context, pickedUri)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        filePickerInFlight = false
        uri?.let { pickedUri ->
            viewModel.openPickedDocument(context, pickedUri)
        }
    }

    LaunchedEffect(openFileLaunchToken) {
        if (openFileLaunchToken <= 0 || filePickerInFlight) {
            return@LaunchedEffect
        }
        filePickerInFlight = true
        runCatching {
            filePicker.launch(SupportedOpenFileMimeTypes)
        }.onFailure {
            filePickerInFlight = false
        }
    }

    LaunchedEffect(viewModel, context, externalOpenUri) {
        viewModel.initialize(context, externalOpenUri)
        if (externalOpenUri != null) {
            onExternalOpenHandled()
        }
    }

    BackHandler {
        if (showExitPrompt) {
            showExitPrompt = false
            return@BackHandler
        }

        when (uiState.currentScreen) {
            ReaderScreen.Home -> {
                val handled = viewModel.goBackInLibrary(context)
                if (!handled) {
                    showExitPrompt = true
                }
            }

            else -> viewModel.closeReader()
        }
    }
    ReaderOrientationEffect(
        mode = uiState.preferences.orientationMode,
        enabled = uiState.currentScreen != ReaderScreen.Home,
    )

    val startupPreferences = remember(context) {
        ReaderStateStore.loadPreferences(context)
    }
    val startupSafePreferences = remember(startupPreferences, uiState.preferences) {
        if (uiState.preferences == ReaderPreferences()) startupPreferences else uiState.preferences
    }
    val libraryScrollPositions = remember {
        mutableMapOf<String, LibraryScrollPosition>()
    }
    var launchContentSettled by rememberSaveable {
        mutableStateOf(false)
    }
    val launchContentAlpha by animateFloatAsState(
        targetValue = if (launchContentSettled) 1f else 0.96f,
        animationSpec = tween(durationMillis = 90),
        label = "launchContentAlpha",
    )
    LaunchedEffect(Unit) {
        launchContentSettled = true
    }

    val localizedContext = remember(localContext, startupSafePreferences.languageMode) {
        localContext.withLanguage(startupSafePreferences.languageMode)
    }
    val localizedConfiguration = remember(localizedContext) {
        Configuration(localizedContext.resources.configuration)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
    key(startupSafePreferences.languageMode) {
    ReaderTheme(mode = startupSafePreferences.themeMode) {
        AppWindowBackgroundEffect()

        if (showExitPrompt) {
            ExitPromptDialog(
                onDismiss = { showExitPrompt = false },
                onExit = {
                    showExitPrompt = false
                    localContext.findActivity()?.finish()
                },
            )
        }

        val appBackground = MaterialTheme.colorScheme.background

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(appBackground)
                .graphicsLayer(alpha = launchContentAlpha),
            color = appBackground,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appBackground),
            ) {
                when (val screen = uiState.currentScreen) {
                    ReaderScreen.Home -> HomeScreen(
                        roots = uiState.libraryRoots,
                        folderPickerEntries = uiState.libraryFolderPickerEntries,
                        selectedRootUriString = uiState.selectedRootUriString,
                        currentRelativePath = uiState.currentRelativePath,
                        folders = uiState.currentFolders,
                        books = uiState.currentBooks,
                        searchQuery = uiState.searchQuery,
                        searchResults = uiState.searchResults,
                        isSearching = uiState.isSearching,
                        recents = uiState.recents,
                        bookmarks = uiState.bookmarks,
                        preferences = uiState.preferences,
                        sortMode = uiState.librarySortMode,
                        fileFilter = uiState.libraryFileFilter,
                        selectedHomeTabName = uiState.selectedHomeTabName,
                        folderDocumentTypesById = uiState.folderDocumentTypesById,
                        progressByUri = uiState.progressByUri,
                        bookStatusByUri = uiState.bookStatusByUri,
                        bookNoteLinksByUri = uiState.bookNoteLinksByUri,
                        pinnedLibraryItemIds = uiState.pinnedLibraryItemIds,
                        libraryScrollPositions = libraryScrollPositions,
                        onChooseFolder = { folderPicker.launch(null) },
                        onOpenFile = {
                            if (!filePickerInFlight) {
                                openFileLaunchToken += 1
                            }
                        },
                        onRefresh = { viewModel.refreshCurrentFolder(context, showLoading = true) },
                        onSelectRoot = { root -> viewModel.selectLibraryRoot(context, root) },
                        onRemoveRoot = { root -> viewModel.removeLibraryRoot(context, root) },
                        onOpenPickerEntry = { entry -> viewModel.openLibraryPickerEntry(context, entry) },
                        onCreateTextNote = { viewModel.createTextNote(context) },
                        onSearchQueryChange = { query -> viewModel.updateSearchQuery(context, query) },
                        onOpenSearchResult = { result -> viewModel.openSearchResult(context, result) },
                        onOpenFolder = { relativePath -> viewModel.openLibraryFolder(context, relativePath) },
                        onOpenBook = { book -> viewModel.openLibraryBook(context, book) },
                        onOpenRecent = { recent -> viewModel.reopenRecent(context, recent) },
                        onOpenBookmark = { bookmark -> viewModel.openBookmark(context, bookmark) },
                        onRemoveBookmark = { bookmark -> viewModel.removeBookmark(context, bookmark) },
                        onRemoveRecent = { recent -> viewModel.removeRecent(context, recent) },
                        onMoveBookmark = { bookmark, offset -> viewModel.moveBookmark(context, bookmark, offset) },
                        onMoveRecent = { recent, offset -> viewModel.moveRecent(context, recent, offset) },
                        onSortModeChange = { sortMode -> viewModel.updateLibrarySortMode(context, sortMode) },
                        onFileFilterChange = { filter -> viewModel.updateLibraryFileFilter(context, filter) },
                        onHomeTabChange = { tabName -> viewModel.updateHomeTab(context, tabName) },
                        onDeleteFolder = { folder -> viewModel.deleteLibraryFolder(context, folder) },
                        onDeleteBook = { book -> viewModel.deleteLibraryBook(context, book) },
                        onRenameFolder = { folder, name -> viewModel.renameLibraryFolder(context, folder, name) },
                        onRenameBook = { book, name -> viewModel.renameLibraryBook(context, book, name) },
                        onTogglePinFolder = { folder -> viewModel.togglePinFolder(context, folder) },
                        onTogglePinBook = { book -> viewModel.togglePinBook(context, book) },
                        onTogglePinDocument = { uriString -> viewModel.togglePinDocument(context, uriString) },
                        onUpdateBookStatus = { uriString, status ->
                            viewModel.updateBookStatus(context, uriString, status)
                        },
                        onPreferencesChange = { preferences ->
                            viewModel.updatePreferences(context, preferences)
                        },
                    )

                    is ReaderScreen.Epub -> EpubReaderScreen(
                        screen = screen,
                        preferences = uiState.preferences,
                        bookmarks = uiState.bookmarks,
                        onBack = viewModel::closeReader,
                        onPreferencesChange = { preferences ->
                            viewModel.updatePreferences(context, preferences)
                        },
                        onOpenBookNote = { viewModel.openBookNote(context, screen.document) },
                        onToggleBookmark = { chapterIndex, chapterCount, scrollFraction, chapterTitle ->
                            viewModel.toggleEpubBookmark(
                                context = context,
                                document = screen.document,
                                chapterIndex = chapterIndex,
                                chapterCount = chapterCount,
                                scrollFraction = scrollFraction,
                                chapterTitle = chapterTitle,
                            )
                        },
                        onSaveProgress = { chapterIndex, chapterCount, scrollFraction ->
                            viewModel.saveEpubProgress(
                                context = context,
                                document = screen.document,
                                chapterIndex = chapterIndex,
                                chapterCount = chapterCount,
                                scrollFraction = scrollFraction,
                            )
                        },
                    )

                    is ReaderScreen.Fb2 -> Fb2ReaderScreen(
                        screen = screen,
                        preferences = uiState.preferences,
                        bookmarks = uiState.bookmarks,
                        onBack = viewModel::closeReader,
                        onPreferencesChange = { preferences ->
                            viewModel.updatePreferences(context, preferences)
                        },
                        onOpenBookNote = { viewModel.openBookNote(context, screen.document) },
                        onToggleBookmark = { chapterIndex, chapterCount, scrollFraction, chapterTitle ->
                            viewModel.toggleEpubBookmark(
                                context = context,
                                document = screen.document,
                                chapterIndex = chapterIndex,
                                chapterCount = chapterCount,
                                scrollFraction = scrollFraction,
                                chapterTitle = chapterTitle,
                            )
                        },
                        onSaveProgress = { chapterIndex, chapterCount, scrollFraction ->
                            viewModel.saveEpubProgress(
                                context = context,
                                document = screen.document,
                                chapterIndex = chapterIndex,
                                chapterCount = chapterCount,
                                scrollFraction = scrollFraction,
                            )
                        },
                    )

                    is ReaderScreen.Pdf -> PdfReaderScreen(
                        screen = screen,
                        preferences = uiState.preferences,
                        bookmarks = uiState.bookmarks,
                        onBack = viewModel::closeReader,
                        onPreferencesChange = { preferences ->
                            viewModel.updatePreferences(context, preferences)
                        },
                        onOpenBookNote = { viewModel.openBookNote(context, screen.document) },
                        onToggleBookmark = { pageIndex, pageCount ->
                            viewModel.togglePdfBookmark(
                                context = context,
                                document = screen.document,
                                pageIndex = pageIndex,
                                pageCount = pageCount,
                            )
                        },
                        onSaveProgress = { pageIndex, pageCount, zoomScale ->
                            viewModel.savePdfProgress(
                                context = context,
                                document = screen.document,
                                pageIndex = pageIndex,
                                pageCount = pageCount,
                                zoomScale = zoomScale,
                            )
                        },
                    )

                    is ReaderScreen.Text -> TextReaderScreen(
                        screen = screen,
                        preferences = uiState.preferences,
                        bookmarks = uiState.bookmarks,
                        onBack = viewModel::closeReader,
                        onPreferencesChange = { preferences ->
                            viewModel.updatePreferences(context, preferences)
                        },
                        onSaveText = { text ->
                            viewModel.saveTextDocument(context, screen.document, text)
                        },
                        onSaveProgress = { scrollFraction ->
                            viewModel.saveTextProgress(context, screen.document, scrollFraction)
                        },
                        onToggleBookmark = { scrollFraction ->
                            viewModel.toggleTextBookmark(context, screen.document, scrollFraction)
                        },
                        onDiscardText = {
                            viewModel.discardTextDocument(context, screen.document, screen.deleteOnDiscard)
                        },
                        onRenameText = { name, text ->
                            viewModel.renameTextDocument(context, screen.document, name, text)
                        },
                        onNoteSectionSelected = { sectionTitle ->
                            viewModel.updateLastNoteSection(context, screen.document.uriString, sectionTitle)
                        },
                        onOpenBookNote = if (screen.sectionedNote) {
                            null
                        } else {
                            { viewModel.openBookNote(context, screen.document) }
                        },
                    )
                }

                uiState.errorMessage?.let { message ->
                    val localizedFolderAccessLost = stringResource(R.string.folder_access_lost_body)
                    val isFolderAccessLost = message == context.getString(R.string.folder_access_lost_body) ||
                        message == localizedFolderAccessLost
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = viewModel::dismissError,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                )
                                .padding(horizontal = 16.dp),
                        ) {
                            ErrorBanner(
                                message = message,
                                onDismiss = viewModel::dismissError,
                                actionLabel = if (isFolderAccessLost) stringResource(R.string.choose_folder) else null,
                                onAction = if (isFolderAccessLost) {
                                    {
                                        viewModel.dismissError()
                                        folderPicker.launch(null)
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }

                if (uiState.isLoading) {
                    LoadingOverlay()
                }

                if (uiState.zipEntriesToChoose.isNotEmpty()) {
                    ZipEntryPickerDialog(
                        entries = uiState.zipEntriesToChoose,
                        onDismiss = viewModel::dismissZipEntries,
                        onOpenEntry = { entry -> viewModel.openZipEntry(context, entry) },
                    )
                }

                ReaderComfortOverlay(
                    readingRuler = uiState.currentScreen != ReaderScreen.Home && uiState.preferences.readingRuler,
                    readingRulerPosition = uiState.preferences.readingRulerPosition,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    }
    }
}

private val SupportedOpenFileMimeTypes = arrayOf(
    "application/epub+zip",
    "application/x-epub",
    "application/epub",
    "application/pdf",
    "text/plain",
    "application/x-fictionbook+xml",
    "application/fb2+xml",
    "application/zip",
    "application/x-zip-compressed",
    "application/x-7z-compressed",
    "application/x-tar",
    "application/x-rar-compressed",
    "application/gzip",
    "*/*",
)

private fun Context.withLanguage(
    mode: ReaderLanguageMode,
): Context {
    val localeTag = mode.localeTag ?: return this

    val locale = Locale.forLanguageTag(localeTag)

    val configuration = Configuration(resources.configuration)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.setLocales(LocaleList(locale))
    }

    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)

    return createConfigurationContext(configuration)
}
