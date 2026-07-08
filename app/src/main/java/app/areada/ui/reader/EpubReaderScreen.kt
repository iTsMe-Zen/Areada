package app.areada.ui.reader

import android.graphics.Color as AndroidColor
import android.annotation.SuppressLint
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.areada.R
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReadingBookmark
import app.areada.data.reader.epubBookmarkId
import app.areada.reader.epub.EpubEngine
import app.areada.reader.epub.RenderedChapter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun EpubReaderScreen(
    screen: ReaderScreen.Epub,
    preferences: ReaderPreferences,
    bookmarks: List<ReadingBookmark>,
    onBack: () -> Unit,
    onPreferencesChange: (ReaderPreferences) -> Unit,
    onOpenBookNote: () -> Unit,
    onToggleBookmark: (chapterIndex: Int, chapterCount: Int, scrollFraction: Float, chapterTitle: String) -> Unit,
    onSaveProgress: (chapterIndex: Int, chapterCount: Int, scrollFraction: Float) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val renderSectionErrorMessage = stringResource(R.string.unable_render_section)
    var showSettings by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var showToc by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var isImmersiveMode by remember(screen.document.uriString) {
        mutableStateOf(true)
    }
    var immersiveControlsVisible by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var noteText by rememberSaveable(screen.document.uriString) {
        mutableStateOf<String?>(null)
    }
    var pendingExternalLink by remember(screen.document.uriString) {
        mutableStateOf<Uri?>(null)
    }
    var showGoToChapter by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var showChapterSearch by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var chapterSearchQuery by rememberSaveable(screen.document.uriString) {
        mutableStateOf("")
    }
    var chapterSearchCurrent by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var chapterSearchCount by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var chapterSearchRequest by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var chapterSearchBackwards by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var chapterIndex by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(
            screen.initialChapterIndex.coerceIn(
                0,
                screen.book.chapters.lastIndex.coerceAtLeast(0),
            ),
        )
    }
    var scrollFraction by rememberSaveable(screen.document.uriString) {
        mutableFloatStateOf(screen.initialScrollFraction.coerceIn(0f, 1f))
    }
    var sectionScrollable by remember(screen.document.uriString, chapterIndex) {
        mutableStateOf(false)
    }
    var scrollRequestId by remember(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var sectionScrollRequest by remember(screen.document.uriString, chapterIndex) {
        mutableStateOf<EpubScrollRequest?>(null)
    }
    var ignoreScrollCallbacksUntil by remember(screen.document.uriString) {
        mutableStateOf(0L)
    }
    var renderedChapter by remember(screen.document.uriString, chapterIndex) {
        mutableStateOf<RenderedChapter?>(null)
    }
    var chapterError by remember(screen.document.uriString, chapterIndex) {
        mutableStateOf<String?>(null)
    }
    val renderPalette = rememberReaderRenderPalette(preferences.themeMode)
    val sectionLabel = stringResource(R.string.section_count_label, chapterIndex + 1, screen.book.chapters.size)
    val topSubtitle = renderedChapter
        ?.title
        ?.ifBlank { null }
        ?: screen.book.chapters.getOrNull(chapterIndex)?.title?.ifBlank { null }
        ?: sectionLabel
    val tocEntries = screen.book.chapters.mapIndexed { index, chapter ->
        ReaderTocEntry(
            index = index,
            label = chapter.title.ifBlank { stringResource(R.string.section_fallback, index + 1) },
        )
    }
    val currentBookmarkId = epubBookmarkId(screen.document.uriString, chapterIndex, scrollFraction)
    val currentBookmarked = bookmarks.any { it.id == currentBookmarkId }
    val latestChapterIndex by rememberUpdatedState(chapterIndex)
    val latestScrollFraction by rememberUpdatedState(scrollFraction)
    val latestSectionScrollable by rememberUpdatedState(sectionScrollable)
    val renderedChapterCache = remember(screen.document.uriString) {
        mutableStateMapOf<EpubRenderCacheKey, RenderedChapter>()
    }
    val renderCacheKey = remember(
        chapterIndex,
        preferences.themeMode,
        preferences.fontChoice,
        preferences.fontSizeSp,
        preferences.pageMargin,
        preferences.lineSpacing,
        preferences.openPreviousChapterAtEnd,
    ) {
        EpubRenderCacheKey(
            chapterIndex = chapterIndex,
            themeMode = preferences.themeMode,
            fontChoice = preferences.fontChoice,
            fontSizeSp = preferences.fontSizeSp,
            pageMargin = preferences.pageMargin,
            lineSpacingBucket = (preferences.lineSpacing * 100f).roundToInt(),
            scrollToEnd = preferences.openPreviousChapterAtEnd,
        )
    }

    fun switchToChapter(nextIndex: Int) {
        if (nextIndex !in screen.book.chapters.indices || nextIndex == chapterIndex) {
            return
        }

        sectionScrollRequest = null
        sectionScrollable = false
        val targetFraction = if (nextIndex < chapterIndex && preferences.openPreviousChapterAtEnd) {
            1f
        } else {
            0f
        }
        scrollFraction = targetFraction
        chapterIndex = nextIndex

        chapterSearchQuery = ""
        chapterSearchCurrent = 0
        chapterSearchCount = 0
        onSaveProgress(nextIndex, screen.book.chapters.size, targetFraction)
    }

    val hapticFeedback = LocalHapticFeedback.current

    fun goToPreviousChapter() {
        if (chapterIndex <= 0) {
            return
        }
        if (preferences.vibrateOnPageTurn) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        switchToChapter(chapterIndex - 1)
    }

    fun goToNextChapter() {
        if (chapterIndex >= screen.book.chapters.lastIndex) {
            return
        }
        if (preferences.vibrateOnPageTurn) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        switchToChapter(chapterIndex + 1)
    }

    fun scrubCurrentSection(progress: Float) {
        val cleanProgress = progress.coerceIn(0f, 1f)

        ignoreScrollCallbacksUntil = SystemClock.uptimeMillis() + 220L
        scrollFraction = cleanProgress

        scrollRequestId += 1
        sectionScrollRequest = EpubScrollRequest(
            id = scrollRequestId,
            progress = cleanProgress,
        )
    }

    fun openLocalChapterLink(urlString: String): Boolean {
        val nextIndex = screen.book.chapters.indexOfFirst { chapter ->
            chapter.matchesLocalHref(urlString)
        }
        if (nextIndex < 0) {
            return false
        }
        switchToChapter(nextIndex)
        return true
    }

    DisposableEffect(screen.document.uriString) {
        onDispose {
            val chapterCount = screen.book.chapters.size
            val effectiveFraction = if (latestChapterIndex >= chapterCount - 1 && !latestSectionScrollable) {
                1f
            } else {
                latestScrollFraction
            }
            onSaveProgress(latestChapterIndex, chapterCount, effectiveFraction)
        }
    }

    LaunchedEffect(screen.document.uriString, renderCacheKey, renderPalette) {
        ignoreScrollCallbacksUntil = SystemClock.uptimeMillis() + 650L

        renderedChapterCache[renderCacheKey]?.let { cachedChapter ->
            renderedChapter = cachedChapter
            chapterError = null
            return@LaunchedEffect
        }

        renderedChapter = null
        chapterError = null
        runCatching {
            EpubEngine.render(
                book = screen.book,
                chapterIndex = chapterIndex,
                preferences = preferences,
                paletteOverride = renderPalette,
                scrollToEnd = preferences.openPreviousChapterAtEnd,
            )
        }
            .onSuccess { chapter ->
                renderedChapterCache[renderCacheKey] = chapter
                trimEpubRenderCache(renderedChapterCache, renderCacheKey)
                renderedChapter = chapter
            }
            .onFailure { throwable ->
                chapterError = displayError(throwable, renderSectionErrorMessage)
            }
    }

    LaunchedEffect(screen.document.uriString, renderCacheKey, renderedChapter) {
        if (renderedChapter == null) {
            return@LaunchedEffect
        }
        delay(80)
        coroutineScope {
            listOf(chapterIndex + 1, chapterIndex - 1)
                .filter { index -> index in screen.book.chapters.indices }
                .map { neighborIndex ->
                    val neighborKey = renderCacheKey.copy(chapterIndex = neighborIndex)
                    if (renderedChapterCache[neighborKey] != null) {
                        return@map null
                    }
                    async {
                        runCatching {
                            EpubEngine.render(
                                book = screen.book,
                                chapterIndex = neighborIndex,
                                preferences = preferences,
                                paletteOverride = renderPalette,
                            )
                        }.onSuccess { chapter ->
                            renderedChapterCache[neighborKey] = chapter
                            trimEpubRenderCache(renderedChapterCache, renderCacheKey)
                        }
                    }
                }
                .filterNotNull()
                .awaitAll()
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(
            preferences = preferences,
            onBookNoteClick = onOpenBookNote,
            showOpenPreviousChapterAtEnd = true,
            onDismiss = { showSettings = false },
            onPreferencesChange = onPreferencesChange,
        )
    }
    if (showGoToChapter) {
        GoToPositionDialog(
            label = "Section",
            currentIndex = chapterIndex,
            total = screen.book.chapters.size,
            title = stringResource(R.string.go_to_section),
            onDismiss = { showGoToChapter = false },
            onConfirm = { nextIndex ->
                switchToChapter(nextIndex)
                showGoToChapter = false
            },
        )
    }
    pendingExternalLink?.let { uri ->
        OpenLinkDialog(
            onDismiss = { pendingExternalLink = null },
            onOpen = {
                pendingExternalLink = null
                openExternalLinkWithChooser(context, uri)
            },
        )
    }
    BackHandler(enabled = showToc) {
        showToc = false
    }
    BackHandler(enabled = showChapterSearch) {
        showChapterSearch = false
        chapterSearchQuery = ""
    }
    KeepReaderScreenAwake(enabled = preferences.keepScreenOn)
    VolumePageTurnEffect(
        enabled = preferences.volumeButtonsTurnPages &&
            !showSettings &&
            !showGoToChapter &&
            !showChapterSearch,
        inverted = preferences.invertVolumeButtons,
        onPrevious = ::goToPreviousChapter,
        onNext = ::goToNextChapter,
    )
    LaunchedEffect(isImmersiveMode) {
        immersiveControlsVisible = false
    }
    val showReaderChrome = !isImmersiveMode || immersiveControlsVisible
    ReaderStatusBarHidden(hidden = !showReaderChrome)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                chapterError != null -> {
                    ReaderMessage(
                        message = chapterError ?: "",
                        onRetry = {
                            val cacheKey = renderCacheKey
                            renderedChapterCache.remove(cacheKey)
                            chapterError = null
                        },
                    )
                }
                renderedChapter == null -> LoadingState(label = stringResource(R.string.rendering_section))
                else -> {
                    val chapter = renderedChapter ?: return@Box
                    key(screen.document.uriString) {
                        val renderedIndex = chapterIndex
                            EpubWebView(
                                chapter = chapter,
                                currentChapterFileUrl = screen.book.chapters[chapterIndex].file.toURI().toString(),
                                preferences = preferences,
                                navigationMode = preferences.navigationMode,
                                renderPalette = renderPalette,
                            initialScrollFraction = scrollFraction,
                            scrollRequest = sectionScrollRequest,
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding()
                                .zIndex(0f),
                            onScrollProgressChange = { progress ->
                                if (chapterIndex == renderedIndex) {
                                    val now = SystemClock.uptimeMillis()
                                    if (now >= ignoreScrollCallbacksUntil) {
                                        scrollFraction = progress
                                    }
                                }
                            },
                            onScrollabilityChange = { canScroll ->
                                if (chapterIndex == renderedIndex) {
                                    sectionScrollable = canScroll
                                }
                            },
                            onReaderTap = {
                                if (isImmersiveMode) {
                                    immersiveControlsVisible = !immersiveControlsVisible
                                }
                            },
                            onSwipePrevious = ::goToPreviousChapter,
                            onSwipeNext = ::goToNextChapter,
                            onOpenLocalHref = ::openLocalChapterLink,
                            onOpenExternalLink = { uri -> pendingExternalLink = uri },
                            onNoteOpen = { note ->
                                noteText = note
                            },
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
                        .zIndex(2f)
                        .fillMaxHeight()
                        .width(18.dp)
                        .padding(
                            top = if (showReaderChrome) 86.dp else 18.dp,
                            bottom = if (showReaderChrome) 118.dp else 18.dp,
                            end = 4.dp,
                        ),
                )
            }

            if (showReaderChrome) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(3f),
                ) {
                    ReaderTopBar(
                        title = screen.document.title,
                        subtitle = topSubtitle,
                        onBack = onBack,
                        onSettings = { showSettings = true },
                        onSearch = { showChapterSearch = !showChapterSearch },
                        onTableOfContents = { showToc = !showToc },
                        onBookmarkToggle = {
                            onToggleBookmark(
                                chapterIndex,
                                screen.book.chapters.size,
                                scrollFraction,
                                renderedChapter?.title ?: screen.book.chapters[chapterIndex].title,
                            )
                        },
                        isBookmarked = currentBookmarked,
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .zIndex(3f),
                ) {
                    key(screen.document.uriString, chapterIndex) {
                        ReaderFooter(
                            leftLabel = stringResource(R.string.previous),
                            rightLabel = stringResource(R.string.next),
                            centerLabel = sectionLabel,
                            leftEnabled = chapterIndex > 0,
                            rightEnabled = chapterIndex < screen.book.chapters.lastIndex,
                            onLeft = ::goToPreviousChapter,
                            onCenter = {
                                showGoToChapter = true
                            },
                            onRight = ::goToNextChapter,
                            progressFraction = scrollFraction,
                            progressPercentFraction = (
                                (chapterIndex + if (chapterIndex >= screen.book.chapters.size - 1 && !sectionScrollable) {
                                    1f
                                } else {
                                    scrollFraction.coerceIn(0f, 1f)
                                }) / screen.book.chapters.size.toFloat()
                            ).coerceIn(0f, 1f),
                            progressKey = "${screen.document.uriString}#$chapterIndex",
                            onProgressScrubbed = ::scrubCurrentSection,
                        )
                    }
                }
            }

            if (showReaderChrome && showToc) {
                ReaderTocOverlay(
                    title = stringResource(R.string.table_of_contents),
                    entries = tocEntries,
                    currentIndex = chapterIndex,
                    onDismiss = { showToc = false },
                    onSelect = { nextIndex ->
                        switchToChapter(nextIndex)
                        showToc = false
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
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

            noteText?.let { note ->
                NotePopup(
                    title = stringResource(R.string.note),
                    note = AnnotatedString(note),
                    onClose = { noteText = null },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp),
                )
            }
        }
    }
}
