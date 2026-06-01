package app.areada.data

import app.areada.data.reader.epubBookmarkId
import app.areada.data.reader.pdfBookmarkId
import app.areada.data.reader.txtBookmarkId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingBookmarkTest {
    @Test
    fun epubBookmarkIdClampsNegativeChapterAndBucketsScroll() {
        assertEquals("epub|content://book|0|5", epubBookmarkId("content://book", -3, 0.24f))
    }

    @Test
    fun pdfBookmarkIdClampsNegativePage() {
        assertEquals("pdf|content://book|0", pdfBookmarkId("content://book", -9))
    }

    @Test
    fun textBookmarkIdClampsScrollBucket() {
        assertEquals("txt|content://note|0", txtBookmarkId("content://note", -1f))
        assertEquals("txt|content://note|20", txtBookmarkId("content://note", 2f))
    }
}
