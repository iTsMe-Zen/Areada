package app.areada.ui

import app.areada.ui.reader.pdfRenderScaleBucket
import app.areada.ui.reader.pdfRenderScaleForZoom
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfReaderStateTest {
    @Test
    fun mapsZoomToRenderScaleBuckets() {
        assertEquals(1f, pdfRenderScaleForZoom(1.15f), 0f)
        assertEquals(1.5f, pdfRenderScaleForZoom(1.16f), 0f)
        assertEquals(2f, pdfRenderScaleForZoom(2.5f), 0f)
        assertEquals(3f, pdfRenderScaleForZoom(2.51f), 0f)
    }

    @Test
    fun convertsRenderScaleToIntegerBucket() {
        assertEquals(100, pdfRenderScaleBucket(1f))
        assertEquals(150, pdfRenderScaleBucket(1.5f))
        assertEquals(300, pdfRenderScaleBucket(3f))
    }
}
