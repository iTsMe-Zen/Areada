package app.areada.data

import app.areada.data.reader.DocumentType
import app.areada.data.reader.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookStatusTest {
    @Test
    fun statusRestoresFromPersistedName() {
        assertEquals(BookStatus.Unread, bookStatusFromName("Unread"))
        assertEquals(BookStatus.Reading, bookStatusFromName("Reading"))
        assertEquals(BookStatus.Finished, bookStatusFromName("Finished"))
        assertNull(bookStatusFromName("Done"))
    }

    @Test
    fun effectiveStatusUsesSavedStatusBeforeProgressInference() {
        val progress = ReadingProgress(uriString = "book", type = DocumentType.EPUB)

        assertEquals(BookStatus.Unread, effectiveBookStatus(null, null))
        assertEquals(BookStatus.Reading, effectiveBookStatus(null, progress))
        assertEquals(BookStatus.Finished, effectiveBookStatus(BookStatus.Finished, progress))
    }

    @Test
    fun readingProgressPercentClampsByDocumentType() {
        assertEquals(
            45,
            readingProgressPercent(
                ReadingProgress(
                    uriString = "epub",
                    type = DocumentType.EPUB,
                    epubChapterIndex = 4,
                    epubChapterCount = 10,
                    epubScrollFraction = 0.5f,
                ),
            ),
        )
        assertEquals(
            50,
            readingProgressPercent(
                ReadingProgress(
                    uriString = "pdf",
                    type = DocumentType.PDF,
                    pdfPageIndex = 4,
                    pdfPageCount = 10,
                ),
            ),
        )
        assertEquals(
            75,
            readingProgressPercent(
                ReadingProgress(
                    uriString = "fb2",
                    type = DocumentType.FB2,
                    txtScrollFraction = 0.75f,
                ),
            ),
        )
    }
}
