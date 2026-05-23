package app.areada.ui.reader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import app.areada.R
import app.areada.data.reader.ReaderNavigationMode
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReadingBookmark
import app.areada.data.reader.pdfBookmarkId
import app.areada.reader.pdf.PdfLinkLayer
import app.areada.reader.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max

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
    onSaveProgress: (pageIndex: Int, pageCount: Int, zoomScale: Float) -> Unit,
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
                    message = rendererResult?.exceptionOrNull()?.let { displayError(it, unableOpenPdfMessage) }
                        ?: unableOpenPdfMessage,
                )
            }
        }
        return
    }
    val pageCount = renderer.pageCount

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
    val currentBookmarked = bookmarks.any { it.id == pdfBookmarkId(screen.document.uriString, pageIndex) }
    val tocEntries = List(pageCount.coerceAtLeast(0)) { index ->
        ReaderTocEntry(
            index = index,
            label = stringResource(R.string.page_label, index + 1),
        )
    }

    DisposableEffect(screen.document.uriString, pageIndex) {
        onDispose {
            onSaveProgress(pageIndex, pageCount, zoomScale)
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(
            preferences = preferences,
            showPdfNote = true,
            onBookNoteClick = onOpenBookNote,
            onDismiss = { showSettings = false },
            onPreferencesChange = onPreferencesChange,
        )
    }
    if (showGoToPage) {
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
    KeepReaderScreenAwake(enabled = preferences.keepScreenOn)
    LaunchedEffect(isImmersiveMode) {
        immersiveControlsVisible = false
    }
    val showReaderChrome = !isImmersiveMode || immersiveControlsVisible

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

    VolumePageTurnEffect(
        enabled = preferences.volumeButtonsTurnPages && !showSettings && !showGoToPage && !showToc,
        inverted = preferences.invertVolumeButtons,
        onPrevious = ::goToPreviousPage,
        onNext = ::goToNextPage,
    )

    LaunchedEffect(screen.document.uriString, pageIndex) {
        pdfLinkLayer = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { _ ->
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
                            renderError = displayError(throwable, unableRenderPageMessage)
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
                        )
                    }
                }
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
                    )
                }
            }
            if (showReaderChrome && showToc) {
                ReaderTocOverlay(
                    title = stringResource(R.string.table_of_contents),
                    entries = tocEntries,
                    currentIndex = pageIndex,
                    onDismiss = { showToc = false },
                    onSelect = { nextIndex ->
                        onSaveProgress(pageIndex, pageCount, zoomScale)
                        pageIndex = nextIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                        showToc = false
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}
