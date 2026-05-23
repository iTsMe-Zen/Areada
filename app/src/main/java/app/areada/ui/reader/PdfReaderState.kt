package app.areada.ui.reader

import android.graphics.Bitmap
import kotlin.math.roundToInt

internal const val PdfBaseRenderScale = 1f
internal const val PdfBaseRenderScaleBucket = 100

internal data class PdfPageBitmapKey(
    val pageIndex: Int,
    val renderScaleBucket: Int,
)

internal data class LibraryScrollPosition(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

internal fun pdfRenderScaleForZoom(zoomScale: Float): Float =
    when {
        zoomScale <= 1.15f -> 1f
        zoomScale <= 1.75f -> 1.5f
        zoomScale <= 2.5f -> 2f
        else -> 3f
    }

internal fun pdfRenderScaleBucket(renderScale: Float): Int =
    (renderScale * 100f).roundToInt()

internal fun Map<PdfPageBitmapKey, Bitmap>.bestPdfBitmapForPage(pageIndex: Int): Bitmap? =
    entries
        .asSequence()
        .filter { entry -> entry.key.pageIndex == pageIndex }
        .maxByOrNull { entry -> entry.key.renderScaleBucket }
        ?.value
