package app.areada.data

import app.areada.data.reader.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZipBookContainerTest {
    @Test
    fun detectsSupportedInnerBookTypes() {
        assertEquals(DocumentType.EPUB, supportedZipEntryType("Books/Story.epub"))
        assertEquals(DocumentType.PDF, supportedZipEntryType("Manual.PDF"))
        assertEquals(DocumentType.TXT, supportedZipEntryType("notes.txt"))
        assertEquals(DocumentType.FB2, supportedZipEntryType("fiction/book.fb2"))
        assertNull(supportedZipEntryType("nested/book.fb2.zip"))
        assertNull(supportedZipEntryType("image.png"))
    }

    @Test
    fun rejectsUnsafeOrJunkZipEntryNames() {
        assertEquals("Folder/Book.epub", safeZipEntryName("Folder\\Book.epub"))
        assertNull(safeZipEntryName("../Book.epub"))
        assertNull(safeZipEntryName("/Book.epub"))
        assertNull(safeZipEntryName("__MACOSX/Book.epub"))
        assertNull(safeZipEntryName("Folder/.DS_Store"))
        assertNull(safeZipEntryName("Thumbs.db"))
    }

    @Test
    fun buildsStableZipEntryUriKeys() {
        val archiveUri = "content://library/archive.zip"
        val entryName = "Folder/Book.epub"
        val uriString = zipBookUriString(archiveUri, entryName)
        val parsed = parseZipBookUriString(uriString)

        assertEquals(archiveUri, parsed?.archiveUriString)
        assertEquals(entryName, parsed?.entryName)
    }
}
