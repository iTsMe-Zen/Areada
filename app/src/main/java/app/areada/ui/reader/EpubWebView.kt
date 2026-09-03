package app.areada.ui.reader

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.areada.R
import app.areada.data.reader.ReaderFontChoice
import app.areada.data.reader.ReaderNavigationMode
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReaderRenderPalette
import app.areada.data.reader.ReaderThemeMode
import app.areada.reader.epub.RenderedChapter
import java.io.ByteArrayInputStream
import kotlin.math.abs

@Composable
internal fun EpubWebView(
    chapter: RenderedChapter,
    currentChapterFileUrl: String,
    preferences: ReaderPreferences,
    navigationMode: ReaderNavigationMode,
    renderPalette: ReaderRenderPalette,
    initialScrollFraction: Float,
    scrollRequest: EpubScrollRequest?,
    modifier: Modifier = Modifier,
    onScrollProgressChange: (Float) -> Unit,
    onScrollabilityChange: (Boolean) -> Unit,
    onReaderTap: () -> Unit,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    onOpenLocalHref: (String) -> Boolean,
    onOpenExternalLink: (Uri) -> Unit,
    onNoteOpen: (String) -> Unit,
    scrollEventId: Int = 0,
    scrollEventPixels: Int = 0,
    searchQuery: String,
    searchRequest: Int,
    searchBackwards: Boolean,
    onSearchResult: (current: Int, count: Int) -> Unit,
) {
    val backgroundColor = AndroidColor.parseColor(renderPalette.backgroundHex)
    val latestScrollRequest by rememberUpdatedState(scrollRequest)
    val latestNavigationMode by rememberUpdatedState(navigationMode)
    val latestOnScrollProgressChange by rememberUpdatedState(onScrollProgressChange)
    val latestOnScrollabilityChange by rememberUpdatedState(onScrollabilityChange)
    val latestOnReaderTap by rememberUpdatedState(onReaderTap)
    val latestOnSwipePrevious by rememberUpdatedState(onSwipePrevious)
    val latestOnSwipeNext by rememberUpdatedState(onSwipeNext)
    val latestOnNoteOpen by rememberUpdatedState(onNoteOpen)
    val latestOnOpenExternalLink by rememberUpdatedState(onOpenExternalLink)
    val latestOnOpenLocalHref by rememberUpdatedState(onOpenLocalHref)
    val latestOnSearchResult by rememberUpdatedState(onSearchResult)
    val latestScrollEventId by rememberUpdatedState(scrollEventId)
    val latestScrollEventPixels by rememberUpdatedState(scrollEventPixels)
    val chapterSignature = "${chapter.baseUrl}#${chapter.html.hashCode()}"

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                var lastProgress = -1f
                var lastProgressAt = 0L
                var lastCanScroll: Boolean? = null
                var lastNoteOpenAt = 0L
                val openNote: (String) -> Unit = { note ->
                    lastNoteOpenAt = SystemClock.uptimeMillis()
                    latestOnNoteOpen(note)
                }
                fun publishScrollState(force: Boolean = false) {
                    val (progress, canScroll) = epubWebViewScrollState(this)
                    val now = SystemClock.uptimeMillis()
                    if (force || abs(progress - lastProgress) >= 0.002f || now - lastProgressAt >= 32L) {
                        lastProgress = progress
                        lastProgressAt = now
                        latestOnScrollProgressChange(progress)
                    }
                    if (force || lastCanScroll != canScroll) {
                        lastCanScroll = canScroll
                        latestOnScrollabilityChange(canScroll)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                } else {
                    setLayerType(View.LAYER_TYPE_NONE, null)
                }
                setBackgroundColor(backgroundColor)
                isVerticalScrollBarEnabled = false
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                isScrollbarFadingEnabled = false
                settings.javaScriptEnabled = true
                settings.loadsImagesAutomatically = true
                settings.blockNetworkLoads = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                settings.loadWithOverviewMode = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.setSupportZoom(true)
                addJavascriptInterface(NoteBridge(openNote), "AreadaNote")
                setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                    if (isDoneCounting) {
                        latestOnSearchResult(
                            if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0,
                            numberOfMatches,
                        )
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        restoreWebViewScroll(view, initialScrollFraction)
                        latestScrollRequest?.let { request ->
                            applyEpubScrollRequest(view, request.progress)
                        }
                        view.postDelayed({ publishScrollState(force = true) }, 170L)
                        view.postDelayed({ publishScrollState(force = true) }, 360L)
                        injectNoteHandler(view)
                        applyEpubChapterSearch(
                            webView = view,
                            query = searchQuery,
                            request = searchRequest,
                            backwards = searchBackwards,
                            onSearchResult = latestOnSearchResult,
                        )
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val targetUrl = request?.url ?: return false
                        val scheme = targetUrl.scheme?.lowercase()
                        if (scheme == "http" || scheme == "https") {
                            latestOnOpenExternalLink(targetUrl)
                            return true
                        }

                        val fragment = targetUrl.fragment
                        val targetUrlString = targetUrl.toString()
                        val baseTarget = targetUrlString.substringBefore('#')
                        val currentBase = currentChapterFileUrl.substringBefore('#')

                        if (baseTarget != currentBase && latestOnOpenLocalHref(targetUrlString)) {
                            return true
                        }

                        if (!fragment.isNullOrBlank() && view != null) {
                            openChapterNote(view, fragment, openNote)
                            return true
                        }

                        return true
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val scheme = request?.url?.scheme?.lowercase()
                        return if (scheme == "http" || scheme == "https") {
                            WebResourceResponse(
                                "text/plain",
                                "utf-8",
                                ByteArrayInputStream(ByteArray(0)),
                            )
                        } else {
                            super.shouldInterceptRequest(view, request)
                        }
                    }
                }

                setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    publishScrollState()
                }

                val gestureDetector = GestureDetector(
                    context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        var handledByUp = false

                        override fun onSingleTapUp(event: MotionEvent): Boolean {
                            if (SystemClock.uptimeMillis() - lastNoteOpenAt < 700L) {
                                return true
                            }
                            if (latestNavigationMode == ReaderNavigationMode.TAP) {
                                val viewWidth = this@apply.width.toFloat()
                                if (viewWidth > 0f) {
                                    val xRatio = event.x / viewWidth
                                    if (xRatio < 0.33f) {
                                        latestOnSwipePrevious()
                                        handledByUp = true
                                        return true
                                    }
                                    if (xRatio > 0.67f) {
                                        latestOnSwipeNext()
                                        handledByUp = true
                                        return true
                                    }
                                }
                            }
                            return false
                        }

                        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                            if (handledByUp) {
                                handledByUp = false
                                return true
                            }
                            if (SystemClock.uptimeMillis() - lastNoteOpenAt < 700L) {
                                return true
                            }
                            latestOnReaderTap()
                            return false
                        }

                        override fun onDoubleTap(event: MotionEvent): Boolean {
                            resetWebViewZoom(this@apply)
                            return true
                        }

                        override fun onFling(
                            downEvent: MotionEvent?,
                            upEvent: MotionEvent,
                            velocityX: Float,
                            velocityY: Float,
                        ): Boolean {
                            if (latestNavigationMode != ReaderNavigationMode.SWIPE) {
                                return false
                            }
                            val startEvent = downEvent ?: return false
                            val deltaX = upEvent.x - startEvent.x
                            val deltaY = upEvent.y - startEvent.y
                            val horizontal = abs(deltaX) > 90f &&
                                abs(deltaX) > abs(deltaY) * 1.25f &&
                                abs(velocityX) > 350f
                            if (!horizontal) {
                                return false
                            }

                            if (deltaX < 0f) {
                                latestOnSwipeNext()
                            } else {
                                latestOnSwipePrevious()
                            }
                            return true
                        }
                    },
                )

                setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                }

                tag = chapterSignature
                loadDataWithBaseURL(
                    chapter.baseUrl,
                    chapter.html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        update = { webView ->
            if (webView.tag != chapterSignature) {
                webView.tag = chapterSignature
                webView.loadDataWithBaseURL(
                    chapter.baseUrl,
                    chapter.html,
                    "text/html",
                    "utf-8",
                    null,
                )
                webView.post { restoreWebViewScroll(webView, initialScrollFraction) }
                webView.postDelayed({ restoreWebViewScroll(webView, initialScrollFraction) }, 180L)
            }

            scrollRequest?.let { request ->
                val previousRequestId = webView.getTag(R.id.tag_epub_scroll_request_id) as? Int
                if (previousRequestId != request.id) {
                    webView.setTag(R.id.tag_epub_scroll_request_id, request.id)
                    applyEpubScrollRequest(webView, request.progress)
                }
            }
            if (latestScrollEventId != 0) {
                val previousEventId = webView.getTag(R.id.tag_epub_scroll_event_id) as? Int
                if (previousEventId != latestScrollEventId) {
                    webView.setTag(R.id.tag_epub_scroll_event_id, latestScrollEventId)
                    webView.post { scrollByWebView(webView, 0, latestScrollEventPixels) }
                }
            }
            applyEpubChapterSearch(
                webView = webView,
                query = searchQuery,
                request = searchRequest,
                backwards = searchBackwards,
                onSearchResult = onSearchResult,
            )
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.setFindListener(null)
            webView.setOnTouchListener(null)
            webView.setOnScrollChangeListener(null)
            webView.webViewClient = WebViewClient()
            runCatching { webView.removeJavascriptInterface("AreadaNote") }
            webView.destroy()
        },
        modifier = modifier.fillMaxSize(),
    )
}

internal data class EpubSearchViewState(
    val query: String,
    val request: Int,
)

internal data class EpubScrollRequest(
    val id: Int,
    val progress: Float,
)

internal data class EpubRenderCacheKey(
    val chapterIndex: Int,
    val themeMode: ReaderThemeMode,
    val fontChoice: ReaderFontChoice,
    val fontSizeSp: Int,
    val lineSpacingBucket: Int,
    val scrollToEnd: Boolean,
)

internal fun trimEpubRenderCache(
    cache: MutableMap<EpubRenderCacheKey, RenderedChapter>,
    centerKey: EpubRenderCacheKey,
) {
    val keepRange = (centerKey.chapterIndex - 1)..(centerKey.chapterIndex + 1)
    cache.keys
        .filterNot { key ->
            key.themeMode == centerKey.themeMode &&
                key.fontChoice == centerKey.fontChoice &&
                key.fontSizeSp == centerKey.fontSizeSp &&
                key.lineSpacingBucket == centerKey.lineSpacingBucket &&
                key.chapterIndex in keepRange
        }
        .forEach { key -> cache.remove(key) }
}

internal fun applyEpubChapterSearch(
    webView: WebView,
    query: String,
    request: Int,
    backwards: Boolean,
    onSearchResult: (current: Int, count: Int) -> Unit,
) {
    val cleanQuery = query.trim()
    val previous = webView.getTag(R.id.tag_epub_search_state) as? EpubSearchViewState

    if (cleanQuery.isBlank()) {
        if (previous?.query?.isNotBlank() == true) {
            webView.clearMatches()
            onSearchResult(0, 0)
        }
        webView.setTag(R.id.tag_epub_search_state, EpubSearchViewState("", request))
        return
    }

    if (previous?.query != cleanQuery) {
        webView.findAllAsync(cleanQuery)
    } else if (previous.request != request) {
        webView.findNext(backwards)
    }

    webView.setTag(R.id.tag_epub_search_state, EpubSearchViewState(cleanQuery, request))
}
