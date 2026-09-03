package app.areada.ui.reader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.areada.R
import app.areada.data.reader.ReaderNavigationMode
import app.areada.data.reader.ReaderPreferences

import app.areada.data.reader.ReadingBookmark
import app.areada.data.reader.pdfBookmarkId
import app.areada.data.reader.pdfExtractedTextBookmarkId
import app.areada.reader.pdf.PdfLinkLayer
import app.areada.reader.pdf.PdfPageRenderer
import app.areada.reader.pdf.PdfSection
import app.areada.reader.pdf.PdfStructuredParagraph
import app.areada.reader.pdf.flattenPdfOutline
import app.areada.reader.pdf.isPdfExtractedTextSupported
import app.areada.reader.pdf.processPdfTextRuns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun PdfReaderScreen(
    screen: ReaderScreen.Pdf,
    preferences: ReaderPreferences,
    bookmarks: List<ReadingBookmark>,
    onBack: () -> Unit,
    onPreferencesChange: (ReaderPreferences) -> Unit,
    onOpenBookNote: () -> Unit,
    onToggleBookmark: (pageIndex: Int, pageCount: Int) -> Unit,
    onToggleExtractedTextBookmark: (sectionIndex: Int, sectionCount: Int, scrollFraction: Float) -> Unit = { _, _, _ -> },
    onSaveProgress: (pageIndex: Int, pageCount: Int, zoomScale: Float) -> Unit,
    onSaveExtractedTextState: (extractTextEnabled: Boolean, extractedPageIndex: Int, extractedScrollMode: Boolean) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current.applicationContext
    val unableOpenPdfMessage = stringResource(R.string.unable_open_pdf)
    val unableRenderPageMessage = stringResource(R.string.unable_render_page)
    var rendererResult by remember(screen.document.uriString) {
        mutableStateOf<Result<PdfPageRenderer>?>(null)
    }
    LaunchedEffect(screen.document.uriString) {
        rendererResult = null
        rendererResult = withContext(Dispatchers.IO) {
            runCatching { PdfPageRenderer(context, screen.document.uri) }
        }
    }
    val renderer = rendererResult?.getOrNull()

    DisposableEffect(renderer) {
        onDispose {
            renderer?.close()
        }
    }

    if (rendererResult == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LoadingState(label = stringResource(R.string.opening_pdf))
        }
        return
    }

    if (renderer == null) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ReaderTopBar(
                    title = screen.document.title,
                    subtitle = "PDF",
                    onBack = onBack,
                    onSettings = null,
                )
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                ReaderMessage(
                    message = rendererResult?.exceptionOrNull()?.let { displayError(context, it, unableOpenPdfMessage) }
                        ?: unableOpenPdfMessage,
                )
            }
        }
        return
    }
    val pageCount = renderer.pageCount

    var sections by remember(screen.document.uriString) {
        mutableStateOf<List<PdfSection>>(emptyList())
    }
    var sectionIndex by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var sectionText by remember(screen.document.uriString) {
        mutableStateOf<String?>(null)
    }
    var isExtractingSection by remember(screen.document.uriString) {
        mutableStateOf(false)
    }
    var extractedStructuredParagraphs by remember(screen.document.uriString) {
        mutableStateOf<List<PdfStructuredParagraph>?>(null)
    }
    var sectionStructuredParagraphs by remember(screen.document.uriString) {
        mutableStateOf<List<PdfStructuredParagraph>?>(null)
    }

    LaunchedEffect(screen.document.uriString) {
        val outline = withContext(Dispatchers.IO) {
            runCatching { renderer.getOutline() }.getOrDefault(emptyList())
        }
        sections = flattenPdfOutline(outline, pageCount)
    }

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
    var showGoToPage by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var pageIndex by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(
            screen.initialPageIndex.coerceIn(
                0,
                max(pageCount - 1, 0),
            ),
        )
    }
    var zoomScale by rememberSaveable(screen.document.uriString) {
        mutableFloatStateOf(screen.initialZoomScale.coerceIn(1f, 5f))
    }
    var pdfLinkLayer by remember(screen.document.uriString, pageIndex) {
        mutableStateOf<PdfLinkLayer?>(null)
    }
    var pendingExternalLink by remember(screen.document.uriString) {
        mutableStateOf<Uri?>(null)
    }
    var pdfViewResetToken by remember(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var pendingPage by remember(screen.document.uriString) {
        mutableIntStateOf(-1)
    }
    var scrubRequestId by remember(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var isExtractedTextMode by rememberSaveable(screen.document.uriString) {
        mutableStateOf(screen.initialExtractedTextEnabled && isPdfExtractedTextSupported())
    }
    var extractedText by remember(screen.document.uriString) {
        mutableStateOf<String?>(null)
    }
    var isExtractingText by remember(screen.document.uriString) {
        mutableStateOf(false)
    }
    var showExtractedTextError by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var extractedPageIndex by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(screen.initialExtractedTextPageIndex.coerceAtLeast(0))
    }
    var extractedTotalPages by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var extractedScrollMode by rememberSaveable(screen.document.uriString) {
        mutableStateOf(screen.initialExtractedTextScrollMode)
    }
    var extractedScrollFraction by rememberSaveable(screen.document.uriString) {
        mutableFloatStateOf(0f)
    }
    var extractedScrollRequestId by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var extractedScrollRequestFraction by remember(screen.document.uriString) {
        mutableStateOf<Float?>(null)
    }
    var extractedScrollEventCounter by remember(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var extractedScrollEventPixels by remember(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var showExtractedTextSearch by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    var extractedTextSearchQuery by rememberSaveable(screen.document.uriString) {
        mutableStateOf("")
    }
    var extractedTextSearchCurrent by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var extractedTextSearchCount by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var extractedTextSearchRequest by rememberSaveable(screen.document.uriString) {
        mutableIntStateOf(0)
    }
    var extractedTextSearchBackwards by rememberSaveable(screen.document.uriString) {
        mutableStateOf(false)
    }
    val extractedTextSupported = remember { isPdfExtractedTextSupported() }
    val currentBookmarked = bookmarks.any { it.id == pdfBookmarkId(screen.document.uriString, pageIndex) }
    val currentExtractedTextBookmarked = if (sections.isNotEmpty()) {
        bookmarks.any { it.id == pdfExtractedTextBookmarkId(screen.document.uriString, sectionIndex, extractedScrollFraction) }
    } else {
        false
    }
    val tocEntries = if (sections.isNotEmpty()) {
        sections.mapIndexed { index, section ->
            ReaderTocEntry(
                index = index,
                label = section.title,
            )
        }
    } else {
        List(pageCount.coerceAtLeast(0)) { index ->
            ReaderTocEntry(
                index = index,
                label = stringResource(R.string.page_label, index + 1),
            )
        }
    }

    DisposableEffect(screen.document.uriString, pageIndex) {
        onDispose {
            onSaveProgress(pageIndex, pageCount, zoomScale)
            onSaveExtractedTextState(isExtractedTextMode, extractedPageIndex, extractedScrollMode)
        }
    }

    BackHandler(enabled = showToc) {
        showToc = false
    }
    BackHandler(enabled = showExtractedTextSearch) {
        showExtractedTextSearch = false
        extractedTextSearchQuery = ""
        extractedTextSearchCurrent = 0
    }
    KeepReaderScreenAwake(enabled = preferences.keepScreenOn)
    LaunchedEffect(isImmersiveMode) {
        immersiveControlsVisible = false
    }
    val showReaderChrome = !isImmersiveMode || immersiveControlsVisible
    ReaderStatusBarHidden(hidden = !showReaderChrome)

    fun resetPdfView() {
        zoomScale = 1f
        pdfViewResetToken += 1
    }

    fun goToPreviousPage() {
        if (pageIndex <= 0) {
            return
        }
        onSaveProgress(pageIndex, pageCount, zoomScale)
        resetPdfView()
        pageIndex -= 1
    }

    fun goToNextPage() {
        if (pageIndex >= pageCount - 1) {
            return
        }
        onSaveProgress(pageIndex, pageCount, zoomScale)
        resetPdfView()
        pageIndex += 1
    }

    fun switchToSection(nextIndex: Int) {
        if (nextIndex !in sections.indices || nextIndex == sectionIndex) return
        sectionIndex = nextIndex
        sectionText = null
        extractedPageIndex = 0
        extractedScrollFraction = 0f
    }

    fun goToPreviousSection() {
        if (sectionIndex <= 0) return
        switchToSection(sectionIndex - 1)
    }

    fun goToNextSection() {
        if (sectionIndex >= sections.lastIndex) return
        switchToSection(sectionIndex + 1)
    }

    LaunchedEffect(scrubRequestId) {
        if (scrubRequestId > 0) {
            delay(150)
            if (pendingPage >= 0 && pendingPage != pageIndex) {
                onSaveProgress(pageIndex, pageCount, zoomScale)
                resetPdfView()
                pageIndex = pendingPage
            }
        }
    }

    VolumePageTurnEffect(
        enabled = preferences.volumeButtonsTurnPages && !showSettings && !showGoToPage && !showToc,
        inverted = preferences.invertVolumeButtons,
        onPrevious = if (isExtractedTextMode && sections.isNotEmpty()) ::goToPreviousSection else ::goToPreviousPage,
        onNext = if (isExtractedTextMode && sections.isNotEmpty()) ::goToNextSection else ::goToNextPage,
    )

    LaunchedEffect(screen.document.uriString, pageIndex) {
        pdfLinkLayer = null
    }

    LaunchedEffect(screen.document.uriString, isExtractedTextMode) {
        if (!extractedTextSupported) return@LaunchedEffect
        if (isExtractedTextMode && extractedText == null && !isExtractingText) {
            isExtractingText = true
            if (sections.isEmpty()) {
                val limitPages = minOf(pageCount, 120)
                val result = withContext(Dispatchers.IO) {
                    try {
                        renderer.extractStructuredTextForPageRange(0, limitPages).let { runs ->
                            val processed = processPdfTextRuns(runs)
                            val plainRaw = runs.joinToString("\n") { it.text }.ifBlank { null }
                            val plainText = plainRaw?.let {
                                val capped = if (it.length > 350_000) it.take(350_000) + "\n\n…[truncated for extracted text]" else it
                                if (pageCount > limitPages) capped + "\n\n[Showing first $limitPages of $pageCount pages]"
                                else capped
                            }
                            Pair(plainText, processed)
                        }
                    } catch (_: Throwable) {
                        val text = renderer.extractTextForPageRange(0, limitPages)?.let { if (it.length > 350_000) it.take(350_000) else it }
                        Pair(text, null)
                    }
                }
                val (text, structured) = result
                if (text != null) {
                    extractedText = text
                    extractedStructuredParagraphs = structured
                    showExtractedTextError = false
                } else {
                    isExtractedTextMode = false
                    showExtractedTextError = true
                }
            }
            isExtractingText = false
        }
    }

    LaunchedEffect(screen.document.uriString, isExtractedTextMode, sectionIndex) {
        if (isExtractedTextMode && sections.isNotEmpty() && !isExtractingSection) {
            val section = sections.getOrNull(sectionIndex) ?: return@LaunchedEffect
            isExtractingSection = true
            val result = withContext(Dispatchers.IO) {
                try {
                    val runs = renderer.extractStructuredTextForPageRange(section.startPage, section.endPage)
                    val processed = processPdfTextRuns(runs)
                    val plainText = runs.joinToString("\n") { it.text }
                    Pair(plainText, processed)
                } catch (_: Throwable) {
                    val text = renderer.extractTextForPageRange(section.startPage, section.endPage)
                    Pair(text, null)
                }
            }
            val (text, structured) = result
            sectionText = text
            sectionStructuredParagraphs = structured
            isExtractingSection = false
        }
    }

    val renderPalette = rememberReaderRenderPalette(preferences.themeMode)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            val currentExtractedText = if (sections.isNotEmpty()) sectionText else extractedText
            val currentExtractedParagraphs = if (sections.isNotEmpty()) sectionStructuredParagraphs else extractedStructuredParagraphs
            if (isExtractedTextMode && currentExtractedText != null) {
                key(screen.document.uriString, sectionIndex) {
                    PdfExtractedTextContent(
                        text = currentExtractedText,
                        paragraphs = currentExtractedParagraphs,
                        preferences = preferences,
                        renderPalette = renderPalette,
                        initialScrollFraction = extractedScrollFraction,
                        scrollRequest = extractedScrollRequestFraction?.let { fraction ->
                            EpubScrollRequest(extractedScrollRequestId, fraction)
                        },
                        scrollEventId = extractedScrollEventCounter,
                        scrollEventPixels = extractedScrollEventPixels,
                        onScrollProgressChange = { progress ->
                            extractedScrollFraction = progress
                        },
                        onPreviousSection = ::goToPreviousSection,
                        onNextSection = ::goToNextSection,
                        onOpenExternalLink = { uri -> pendingExternalLink = uri },
                        onReaderTap = {
                            if (isImmersiveMode) {
                                immersiveControlsVisible = !immersiveControlsVisible
                            }
                        },
                        searchQuery = extractedTextSearchQuery,
                        searchRequest = extractedTextSearchRequest,
                        searchBackwards = extractedTextSearchBackwards,
                        onSearchResult = { current, count ->
                            extractedTextSearchCurrent = current
                            extractedTextSearchCount = count
                        },
                        navigationMode = preferences.navigationMode,
                    )
                }
                if (showReaderChrome) {
                    val extractedSubtitle = if (sections.isNotEmpty()) {
                        val section = sections.getOrNull(sectionIndex)
                        section?.title?.ifBlank { null }
                            ?: stringResource(R.string.section_count_label, sectionIndex + 1, sections.size)
                    } else {
                        stringResource(R.string.extract_text)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                    ) {
                        ReaderTopBar(
                            title = screen.document.title,
                            subtitle = extractedSubtitle,
                            onBack = onBack,
                            onSettings = { showSettings = true },
                            onTableOfContents = if (sections.isNotEmpty()) {
                                { showToc = !showToc }
                            } else {
                                null
                            },
                            onBookmarkToggle = {
                                onToggleExtractedTextBookmark(sectionIndex, sections.size, extractedScrollFraction)
                            },
                            isBookmarked = currentExtractedTextBookmarked,
                            onSearch = { showExtractedTextSearch = !showExtractedTextSearch },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    ) {
                        if (sections.isNotEmpty()) {
                            val overallProgress = (
                                (sectionIndex + extractedScrollFraction.coerceIn(0f, 1f)) / sections.size.toFloat()
                            ).coerceIn(0f, 1f)
                            ReaderFooter(
                                leftLabel = stringResource(R.string.previous),
                                rightLabel = stringResource(R.string.next),
                                centerLabel = stringResource(
                                    R.string.section_count_label,
                                    sectionIndex + 1,
                                    sections.size,
                                ),
                                leftEnabled = sectionIndex > 0,
                                rightEnabled = sectionIndex < sections.lastIndex,
                                onLeft = ::goToPreviousSection,
                                onCenter = { showToc = true },
                                onRight = ::goToNextSection,
                                progressFraction = overallProgress,
                                progressKey = "${screen.document.uriString}#extracted-text-section",
                                onProgressScrubbed = { progress ->
                                    val totalSections = sections.size.toFloat()
                                    val targetSection = (progress * totalSections).toInt()
                                        .coerceIn(0, sections.lastIndex.coerceAtLeast(0))
                                    if (targetSection != sectionIndex) {
                                        switchToSection(targetSection)
                                    } else {
                                        val sectionProgress = (progress * totalSections) - targetSection
                                        extractedScrollRequestId += 1
                                        extractedScrollRequestFraction = sectionProgress.coerceIn(0f, 1f)
                                    }
                                },
                            )
                        } else {
                            ReaderFooter(
                                leftLabel = stringResource(R.string.previous),
                                rightLabel = stringResource(R.string.next),
                                centerLabel = stringResource(R.string.extract_text),
                                leftEnabled = false,
                                rightEnabled = false,
                                onLeft = {},
                                onCenter = null,
                                onRight = {},
                                progressFraction = extractedScrollFraction,
                                progressKey = "${screen.document.uriString}#extracted-text",
                                onProgressScrubbed = { progress ->
                                    extractedScrollRequestId += 1
                                    extractedScrollRequestFraction = progress.coerceIn(0f, 1f)
                                },
                            )
                        }
                    }
                }
                if (!showReaderChrome && isExtractedTextMode && sections.isNotEmpty() && preferences.navigationMode == ReaderNavigationMode.BUTTONS) {
                    DirectionalButtons(
                        onPageNext = ::goToNextSection,
                        onPagePrevious = ::goToPreviousSection,
                        onScrollChange = { delta ->
                            val pixelDelta = (delta * 1000f).roundToInt()
                            extractedScrollEventPixels = pixelDelta
                            extractedScrollEventCounter += 1
                        },
                        invertScrolling = preferences.invertScrolling,
                        buttonLayout = preferences.buttonLayout,
                        modifier = Modifier.align(Alignment.BottomCenter).zIndex(100f),
                    )
                }
                if (showReaderChrome && showExtractedTextSearch) {
                    ReaderChapterSearchOverlay(
                        query = extractedTextSearchQuery,
                        current = extractedTextSearchCurrent,
                        count = extractedTextSearchCount,
                        onQueryChange = { query ->
                            extractedTextSearchQuery = query.take(80)
                            extractedTextSearchCurrent = 0
                            extractedTextSearchCount = 0
                        },
                        onPrevious = {
                            extractedTextSearchBackwards = true
                            extractedTextSearchRequest += 1
                        },
                        onNext = {
                            extractedTextSearchBackwards = false
                            extractedTextSearchRequest += 1
                        },
                        onDismiss = {
                            showExtractedTextSearch = false
                            extractedTextSearchQuery = ""
                        },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
                if (showReaderChrome && showToc && sections.isNotEmpty()) {
                    ReaderTocOverlay(
                        title = stringResource(R.string.table_of_contents),
                        entries = tocEntries,
                        currentIndex = sectionIndex,
                        onDismiss = { showToc = false },
                        onSelect = { nextIndex ->
                            switchToSection(nextIndex)
                            showToc = false
                        },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
                if (isExtractingText || isExtractingSection) {
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        LoadingState(label = stringResource(R.string.rendering_section))
                    }
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val density = LocalDensity.current
                    val horizontalPaddingPx = with(density) { 32.dp.roundToPx() }
                    val widthPx = (constraints.maxWidth - horizontalPaddingPx).coerceAtLeast(1)
                    val desiredRenderScale = pdfRenderScaleForZoom(zoomScale)
                    val desiredBitmapKey = PdfPageBitmapKey(pageIndex, pdfRenderScaleBucket(desiredRenderScale))
                    val pageBitmapCache = remember(screen.document.uriString, widthPx) {
                        mutableStateMapOf<PdfPageBitmapKey, Bitmap>()
                    }
                    var renderError by remember(screen.document.uriString, pageIndex, widthPx, desiredBitmapKey) {
                        mutableStateOf<String?>(null)
                    }
                    val cachedBitmap = pageBitmapCache[desiredBitmapKey] ?: pageBitmapCache.bestPdfBitmapForPage(pageIndex)

                    DisposableEffect(pageBitmapCache) {
                        onDispose {
                            pageBitmapCache.values.forEach { bitmap ->
                                if (!bitmap.isRecycled) {
                                    bitmap.recycle()
                                }
                            }
                            pageBitmapCache.clear()
                        }
                    }

                    LaunchedEffect(screen.document.uriString, desiredBitmapKey, widthPx) {
                        if (pageBitmapCache[desiredBitmapKey] != null) {
                            renderError = null
                            return@LaunchedEffect
                        }
                        renderError = null

                        runCatching {
                            withContext(Dispatchers.IO) {
                                renderer.renderPage(pageIndex, widthPx, desiredRenderScale)
                            }
                        }
                            .onSuccess { rendered ->
                                pageBitmapCache[desiredBitmapKey] = rendered
                                trimPdfBitmapCache(pageBitmapCache, desiredBitmapKey)
                            }
                            .onFailure { throwable ->
                                if (pageBitmapCache.bestPdfBitmapForPage(pageIndex) == null) {
                                    renderError = displayError(context, throwable, unableRenderPageMessage)
                                }
                            }
                    }

                    LaunchedEffect(screen.document.uriString, pageIndex, widthPx, cachedBitmap) {
                        if (cachedBitmap == null) {
                            return@LaunchedEffect
                        }
                        delay(120)
                        listOf(pageIndex + 1, pageIndex - 1)
                            .map { index -> PdfPageBitmapKey(index, PdfBaseRenderScaleBucket) }
                            .filter { key -> key.pageIndex in 0 until pageCount && pageBitmapCache[key] == null }
                            .forEach { neighborIndex ->
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        renderer.renderPage(neighborIndex.pageIndex, widthPx, PdfBaseRenderScale)
                                    }
                                }.onSuccess { rendered ->
                                    pageBitmapCache[neighborIndex] = rendered
                                    trimPdfBitmapCache(pageBitmapCache, desiredBitmapKey)
                                }
                            }
                    }

                    LaunchedEffect(screen.document.uriString, pageIndex, renderer, cachedBitmap) {
                        if (cachedBitmap == null) {
                            return@LaunchedEffect
                        }
                        pdfLinkLayer = withContext(Dispatchers.IO) {
                            runCatching { renderer.loadLinkLayer(pageIndex) }.getOrNull()
                        }
                    }

                    when {
                        renderError != null -> ReaderMessage(message = renderError ?: "")
                        cachedBitmap == null -> LoadingState(label = stringResource(R.string.rendering_page))
                        else -> {
                                val pageBitmap = cachedBitmap
                                Box(modifier = Modifier.fillMaxSize()) {
                                    ZoomablePage(
                                        bitmap = pageBitmap,
                                        pageKey = pageIndex,
                                        resetToken = pdfViewResetToken,
                                        backgroundColor = MaterialTheme.colorScheme.background,
                                        initialScale = zoomScale,
                                        navigationMode = preferences.navigationMode,
                                        onScaleChange = { scale ->
                                            zoomScale = scale
                                        },
                                        onReaderTap = {
                                            if (isImmersiveMode) {
                                                immersiveControlsVisible = !immersiveControlsVisible
                                            }
                                        },
                                        onTapPrevious = ::goToPreviousPage,
                                        onTapNext = ::goToNextPage,
                                        onSwipePrevious = ::goToPreviousPage,
                                        onSwipeNext = ::goToNextPage,
                                        linkLayer = pdfLinkLayer,
                                        onPdfLink = { target ->
                                            target.pageIndex?.let { targetPage ->
                                                val safePage = targetPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                                onSaveProgress(pageIndex, pageCount, zoomScale)
                                                resetPdfView()
                                                if (safePage != pageIndex) {
                                                    pageIndex = safePage
                                                }
                                                return@ZoomablePage
                                            }
                                            target.uri?.let { uri ->
                                                pendingExternalLink = uri
                                            }
                                        },
                                        onResetZoom = ::resetPdfView,
                                    )
                                }
                            }
                        }
                    }
                    if (!showReaderChrome && preferences.navigationMode == ReaderNavigationMode.BUTTONS) {
                        DirectionalButtons(
                            onPageNext = ::goToNextPage,
                            onPagePrevious = ::goToPreviousPage,
                            onScrollChange = {},
                            invertScrolling = preferences.invertScrolling,
                            buttonLayout = preferences.buttonLayout,
                            modifier = Modifier.align(Alignment.BottomCenter).zIndex(100f),
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
                                subtitle = stringResource(R.string.page_of_count, pageIndex + 1, pageCount),
                                onBack = onBack,
                                onSettings = { showSettings = true },
                                onTableOfContents = { showToc = !showToc },
                                onBookmarkToggle = { onToggleBookmark(pageIndex, pageCount) },
                                isBookmarked = currentBookmarked,
                                onSearch = null,
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
                                centerLabel = stringResource(R.string.page_short_label, pageIndex + 1),
                                leftEnabled = pageIndex > 0,
                                rightEnabled = pageIndex < pageCount - 1,
                                onLeft = ::goToPreviousPage,
                                onCenter = { showGoToPage = true },
                                onRight = ::goToNextPage,
                                progressFraction = pageIndex.toFloat() / (pageCount - 1).coerceAtLeast(1),
                                progressKey = "${screen.document.uriString}#$pageIndex",
                                onProgressScrubbed = { progress ->
                                    scrubRequestId++
                                    pendingPage = (progress * (pageCount - 1).coerceAtLeast(1)).roundToInt()
                                        .coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                },
                            )
                        }
                    }
                    if (showReaderChrome && showToc) {
                        val currentTocIndex = if (sections.isNotEmpty()) {
                            sections.indexOfFirst { it.startPage <= pageIndex }.coerceAtLeast(0)
                        } else {
                            pageIndex
                        }
                        ReaderTocOverlay(
                            title = stringResource(R.string.table_of_contents),
                            entries = tocEntries,
                            currentIndex = currentTocIndex,
                            onDismiss = { showToc = false },
                            onSelect = { nextIndex ->
                                onSaveProgress(pageIndex, pageCount, zoomScale)
                                val targetPage = if (sections.isNotEmpty()) {
                                    sections.getOrNull(nextIndex)?.startPage?.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                        ?: nextIndex
                                } else {
                                    nextIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                }
                                resetPdfView()
                                pageIndex = targetPage
                                showToc = false
                            },
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }
        // Disable extracted text on legacy devices where text extraction is unavailable (keeps app light & fast)
        LaunchedEffect(extractedTextSupported) {
            if (!extractedTextSupported && isExtractedTextMode) {
                isExtractedTextMode = false
            }
        }
        if (showSettings) {
            ReaderSettingsSheet(
                preferences = preferences,
                onBookNoteClick = onOpenBookNote,
                showExtractedTextToggle = extractedTextSupported,
                pdfExtractedTextEnabled = isExtractedTextMode,
                pdfExtractedTextScrollMode = extractedScrollMode,
                onExtractedTextToggle = { enabled ->
                    if (enabled) {
                        isExtractedTextMode = true
                        extractedText = null
                        extractedStructuredParagraphs = null
                    } else {
                        isExtractedTextMode = false
                        extractedText = null
                        extractedStructuredParagraphs = null
                        showExtractedTextError = false
                    }
                    onSaveExtractedTextState(isExtractedTextMode, extractedPageIndex, extractedScrollMode)
                },
                onExtractedTextScrollToggle = { scrollEnabled ->
                    extractedScrollMode = scrollEnabled
                    onSaveExtractedTextState(isExtractedTextMode, extractedPageIndex, scrollEnabled)
                },
                onDismiss = { showSettings = false },
                onPreferencesChange = onPreferencesChange,
            )
        }
        if (showGoToPage && !isExtractedTextMode) {
            GoToPositionDialog(
                label = stringResource(R.string.page),
                currentIndex = pageIndex,
                total = pageCount,
                onDismiss = { showGoToPage = false },
                onConfirm = { nextIndex ->
                    onSaveProgress(pageIndex, pageCount, zoomScale)
                    zoomScale = 1f
                    pdfViewResetToken += 1
                    pageIndex = nextIndex
                    showGoToPage = false
                },
            )
        }
        if (showExtractedTextError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showExtractedTextError = false },
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
                        message = "Cannot extract text from this PDF",
                        onDismiss = { showExtractedTextError = false },
                    )
                }
            }
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
    }
