package app.areada.ui.reader

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.areada.R
import app.areada.data.reader.ReaderNavigationMode
import app.areada.data.reader.ReaderPreferences

import app.areada.data.reader.ReadingBookmark
import app.areada.data.reader.epubBookmarkId
import app.areada.data.reader.renderPalette
import app.areada.reader.epub.RenderedChapter
import app.areada.reader.markdown.MarkdownBook
import app.areada.reader.markdown.MarkdownEngine
import kotlin.math.roundToInt

@Composable
internal fun MarkdownReaderScreen(
    screen: ReaderScreen.Markdown,
    preferences: ReaderPreferences,
    bookmarks: List<ReadingBookmark>,
    onBack: () -> Unit,
    onPreferencesChange: (ReaderPreferences) -> Unit,
    onOpenBookNote: () -> Unit,
    onToggleBookmark: (chapterIndex: Int, chapterCount: Int, scrollFraction: Float, chapterTitle: String) -> Unit,
    onSaveProgress: (chapterIndex: Int, chapterCount: Int, scrollFraction: Float) -> Unit,
) {
    val renderSectionErrorMessage = stringResource(R.string.unable_render_section)
    val context = LocalContext.current.applicationContext
    var showSettings by rememberSaveable(screen.document.uriString) { mutableStateOf(false) }
    var isImmersiveMode by remember(screen.document.uriString) { mutableStateOf(true) }
    var immersiveControlsVisible by rememberSaveable(screen.document.uriString) { mutableStateOf(false) }
    var noteText by rememberSaveable(screen.document.uriString) { mutableStateOf<String?>(null) }
    var scrollFraction by rememberSaveable(screen.document.uriString) {
        mutableFloatStateOf(screen.initialScrollFraction.coerceIn(0f, 1f))
    }
    var sectionScrollable by remember(screen.document.uriString) { mutableStateOf(false) }
    var scrollRequestId by remember(screen.document.uriString) { mutableIntStateOf(0) }
    var sectionScrollRequest by remember(screen.document.uriString) { mutableStateOf<EpubScrollRequest?>(null) }
    var ignoreScrollCallbacksUntil by remember(screen.document.uriString) { mutableStateOf(0L) }
    var scrollEventCounter by remember(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var scrollEventPixels by remember(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var renderedChapter by remember(screen.document.uriString) { mutableStateOf<RenderedChapter?>(null) }
    var chapterError by remember(screen.document.uriString) { mutableStateOf<String?>(null) }
    var showChapterSearch by rememberSaveable(screen.document.uriString) { mutableStateOf(false) }
    var chapterSearchQuery by rememberSaveable(screen.document.uriString) { mutableStateOf("") }
    var chapterSearchCurrent by rememberSaveable(screen.document.uriString) { mutableIntStateOf(0) }
    var chapterSearchCount by rememberSaveable(screen.document.uriString) { mutableIntStateOf(0) }
    var chapterSearchRequest by rememberSaveable(screen.document.uriString) { mutableIntStateOf(0) }
    var chapterSearchBackwards by rememberSaveable(screen.document.uriString) { mutableStateOf(false) }
    val renderPalette = rememberReaderRenderPalette(preferences.themeMode)
    val chapterTitle = screen.book.title
    val latestScrollFraction by remember { mutableFloatStateOf(scrollFraction) }
    val currentBookmarkId = epubBookmarkId(screen.document.uriString, 0, scrollFraction)
    val currentBookmarked = bookmarks.any { it.id == currentBookmarkId }

    DisposableEffect(screen.document.uriString) {
        onDispose {
            val effectiveFraction = if (!sectionScrollable) 1f else latestScrollFraction
            onSaveProgress(0, 1, effectiveFraction)
        }
    }

    LaunchedEffect(screen.document.uriString, preferences, renderPalette) {
        ignoreScrollCallbacksUntil = SystemClock.uptimeMillis() + 650L
        renderedChapter = null
        chapterError = null
        val baseUrl = runCatching {
            val uri = android.net.Uri.parse(screen.document.uriString)
            val path = uri.path ?: return@runCatching ""
            val parentPath = path.substringBeforeLast("/", "")
            if (parentPath.isNotBlank()) {
                uri.buildUpon().path(parentPath).build().toString() + "/"
            } else ""
        }.getOrDefault("")
        runCatching {
            MarkdownEngine.render(
                book = screen.book,
                chapterIndex = 0,
                preferences = preferences,
                paletteOverride = renderPalette,
                scrollToEnd = false,
                baseUrl = baseUrl,
            )
        }
            .onSuccess { chapter -> renderedChapter = chapter }
            .onFailure { throwable -> chapterError = displayError(context, throwable, renderSectionErrorMessage) }
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showChapterSearch) {
        showChapterSearch = false
        chapterSearchQuery = ""
    }
    KeepReaderScreenAwake(enabled = preferences.keepScreenOn)
    VolumePageTurnEffect(
        enabled = preferences.volumeButtonsTurnPages && !showSettings && !showChapterSearch,
        inverted = preferences.invertVolumeButtons,
        onPrevious = {},
        onNext = {},
    )
    LaunchedEffect(isImmersiveMode) { immersiveControlsVisible = false }
    val showReaderChrome = !isImmersiveMode || immersiveControlsVisible
    ReaderStatusBarHidden(hidden = !showReaderChrome)

    if (showSettings) {
        ReaderSettingsSheet(
            preferences = preferences,
            onBookNoteClick = onOpenBookNote,
            onDismiss = { showSettings = false },
            onPreferencesChange = onPreferencesChange,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            chapterError != null -> {
                ReaderMessage(message = chapterError ?: "", onRetry = { chapterError = null })
            }
            renderedChapter == null -> LoadingState(label = stringResource(R.string.rendering_section))
            else -> {
                val chapter = renderedChapter ?: return@Box
                key(screen.document.uriString) {
                    EpubWebView(
                        chapter = chapter,
                        currentChapterFileUrl = "",
                        preferences = preferences,
                        navigationMode = preferences.navigationMode,
                        renderPalette = renderPalette,
                        initialScrollFraction = scrollFraction,
                        scrollRequest = sectionScrollRequest,
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        onScrollProgressChange = { progress ->
                            val now = SystemClock.uptimeMillis()
                            if (now >= ignoreScrollCallbacksUntil) {
                                scrollFraction = progress
                            }
                        },
                        onScrollabilityChange = { canScroll -> sectionScrollable = canScroll },
                        onReaderTap = {
                            if (isImmersiveMode) {
                                immersiveControlsVisible = !immersiveControlsVisible
                            }
                        },
                        onSwipePrevious = {},
                        onSwipeNext = {},
                        onOpenLocalHref = { false },
                        onOpenExternalLink = {},
                        onNoteOpen = { note -> noteText = note },
                        scrollEventId = scrollEventCounter,
                        scrollEventPixels = scrollEventPixels,
                        searchQuery = chapterSearchQuery,
                        searchRequest = chapterSearchRequest,
                        searchBackwards = chapterSearchBackwards,
                        onSearchResult = { current, count ->
                            chapterSearchCurrent = current
                            chapterSearchCount = count
                        },
                    )
                }
            }
        }

        if (renderedChapter != null) {
            EpubSectionScrollThumb(
                progressFraction = scrollFraction,
                thumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(8.dp)
                    .padding(vertical = 48.dp)
                    .padding(end = 4.dp),
            )
        }

        if (showReaderChrome) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            ) {
                ReaderTopBar(
                    title = screen.document.title,
                    subtitle = chapterTitle,
                    onBack = onBack,
                    onSettings = { showSettings = true },
                    onSearch = { showChapterSearch = !showChapterSearch },
                    onTableOfContents = null,
                    onBookmarkToggle = {
                        onToggleBookmark(0, 1, scrollFraction, chapterTitle)
                    },
                    isBookmarked = currentBookmarked,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                ReaderFooter(
                    leftLabel = stringResource(R.string.previous),
                    rightLabel = stringResource(R.string.next),
                    centerLabel = stringResource(R.string.page_short_label, 1),
                    leftEnabled = false,
                    rightEnabled = false,
                    onLeft = {},
                    onCenter = null,
                    onRight = {},
                    progressFraction = scrollFraction,
                    progressPercentFraction = scrollFraction,
                    progressKey = screen.document.uriString,
                    onProgressScrubbed = { progress ->
                        ignoreScrollCallbacksUntil = SystemClock.uptimeMillis() + 220L
                        scrollFraction = progress
                        scrollRequestId += 1
                        sectionScrollRequest = EpubScrollRequest(id = scrollRequestId, progress = progress)
                    },
                )
            }
        }

        if (showReaderChrome && showChapterSearch) {
            ReaderChapterSearchOverlay(
                query = chapterSearchQuery,
                current = chapterSearchCurrent,
                count = chapterSearchCount,
                onQueryChange = { query ->
                    chapterSearchQuery = query.take(80)
                    chapterSearchCurrent = 0
                    chapterSearchCount = 0
                },
                onPrevious = {
                    chapterSearchBackwards = true
                    chapterSearchRequest += 1
                },
                onNext = {
                    chapterSearchBackwards = false
                    chapterSearchRequest += 1
                },
                onDismiss = {
                    showChapterSearch = false
                    chapterSearchQuery = ""
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        if (!showReaderChrome && preferences.navigationMode == ReaderNavigationMode.BUTTONS) {
            DirectionalButtons(
                onPageNext = {},
                onPagePrevious = {},
                onScrollChange = { delta ->
                    val pixelDelta = (delta * 1000f).roundToInt()
                    scrollEventPixels = pixelDelta
                    scrollEventCounter += 1
                },
                invertScrolling = preferences.invertScrolling,
                buttonLayout = preferences.buttonLayout,
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(100f),
                )
            }
        }
    }
