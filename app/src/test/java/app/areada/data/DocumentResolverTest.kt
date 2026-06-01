package app.areada.data

import app.areada.data.reader.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentResolverTest {
    @Test
    fun detectsSupportedTypesFromExtensions() {
        assertEquals(DocumentType.EPUB, DocumentResolver.detectSupportedType(null, "Book.EPUB"))
        assertEquals(DocumentType.PDF, DocumentResolver.detectSupportedType(null, "Manual.pdf"))
        assertEquals(DocumentType.TXT, DocumentResolver.detectSupportedType(null, "Note.txt"))
        assertEquals(DocumentType.FB2, DocumentResolver.detectSupportedType(null, "Novel.fb2"))
        assertEquals(DocumentType.FB2, DocumentResolver.detectSupportedType(null, "Novel.fb2.zip"))
        assertEquals(DocumentType.FB2, DocumentResolver.detectSupportedType(null, "Novel.fbz"))
        assertEquals(DocumentType.FB2, DocumentResolver.detectSupportedType("application/octet-stream", "Novel.fbz"))
        assertEquals(DocumentType.ZIP, DocumentResolver.detectSupportedType(null, "archive.zip"))
    }

    @Test
    fun detectsSupportedTypesFromMimeTypes() {
        assertEquals(DocumentType.EPUB, DocumentResolver.detectSupportedType("application/epub+zip", "download"))
        assertEquals(DocumentType.PDF, DocumentResolver.detectSupportedType("application/pdf", "download"))
        assertEquals(DocumentType.TXT, DocumentResolver.detectSupportedType("text/plain; charset=utf-8", "download"))
        assertEquals(DocumentType.FB2, DocumentResolver.detectSupportedType("application/fb2+xml", "download"))
        assertEquals(DocumentType.FB2, DocumentResolver.detectSupportedType("application/x-fictionbook+xml", "download"))
        assertEquals(DocumentType.ZIP, DocumentResolver.detectSupportedType("application/zip", "download"))
        assertEquals(DocumentType.ZIP, DocumentResolver.detectSupportedType("application/x-zip-compressed", "download"))
    }

    @Test
    fun returnsNullForUnsupportedType() {
        assertNull(DocumentResolver.detectSupportedType("application/octet-stream", "archive.bin"))
    }
}
