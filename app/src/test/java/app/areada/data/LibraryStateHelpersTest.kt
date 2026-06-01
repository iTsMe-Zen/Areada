package app.areada.data

import app.areada.data.library.LibraryBookEntry
import app.areada.data.library.LibraryFolderEntry
import app.areada.data.library.LibraryRepository
import app.areada.data.library.LibraryRoot
import app.areada.data.library.LibrarySearchIndexEntry
import app.areada.data.library.LibrarySearchResult
import app.areada.data.library.LibrarySearchResultType
import app.areada.data.library.LibrarySortMode
import app.areada.data.library.folderDocumentTypesById
import app.areada.data.library.isReadingProgressCompleted
import app.areada.data.library.rootPickerEntries
import app.areada.data.library.sortLibraryBooks
import app.areada.data.library.sortLibraryFolders
import app.areada.data.reader.DocumentType
import app.areada.data.reader.ReadingProgress
import app.areada.data.reader.RecentDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStateHelpersTest {
    @Test
    fun sortsPinnedBooksBeforeNaturalNameOrder() {
        val books = listOf(
            book(id = "1", title = "Volume 10"),
            book(id = "2", title = "Volume 2", pinned = true),
            book(id = "3", title = "Volume 1"),
        )

        val sorted = sortLibraryBooks(books, LibrarySortMode.NAME_ASC)

        assertEquals(listOf("Volume 2", "Volume 1", "Volume 10"), sorted.map { it.title })
    }

    @Test
    fun sortsFoldersByNewestAddedWithPinnedFirst() {
        val folders = listOf(
            folder(id = "old", name = "Old", addedAt = 1L),
            folder(id = "new", name = "New", addedAt = 3L),
            folder(id = "pinned", name = "Pinned", addedAt = 2L, pinned = true),
        )

        val sorted = sortLibraryFolders(folders, LibrarySortMode.DATE_ADDED_DESC)

        assertEquals(listOf("Pinned", "New", "Old"), sorted.map { it.name })
    }

    @Test
    fun sortsBooksByRecentlyOpenedProgressAndFileType() {
        val books = listOf(
            book(id = "epub", title = "Alpha", type = DocumentType.EPUB),
            book(id = "pdf", title = "Beta", type = DocumentType.PDF),
            book(id = "txt", title = "Gamma", type = DocumentType.TXT),
        )
        val recents = listOf(
            RecentDocument("content://book/pdf", "Beta", DocumentType.PDF, lastOpenedAt = 30L),
            RecentDocument("content://book/epub", "Alpha", DocumentType.EPUB, lastOpenedAt = 10L),
        )
        val progress = mapOf(
            "content://book/epub" to ReadingProgress("content://book/epub", DocumentType.EPUB, epubChapterIndex = 1, epubChapterCount = 2),
            "content://book/pdf" to ReadingProgress("content://book/pdf", DocumentType.PDF, pdfPageIndex = 0, pdfPageCount = 10),
        )

        assertEquals(
            listOf("Beta", "Alpha", "Gamma"),
            sortLibraryBooks(books, LibrarySortMode.RECENTLY_OPENED, recents = recents).map { it.title },
        )
        assertEquals(
            listOf("Alpha", "Beta", "Gamma"),
            sortLibraryBooks(books, LibrarySortMode.READING_PROGRESS, progressByUri = progress).map { it.title },
        )
        assertEquals(
            listOf("Alpha", "Beta", "Gamma"),
            sortLibraryBooks(books.reversed(), LibrarySortMode.FILE_TYPE).map { it.title },
        )
    }

    @Test
    fun mapsDocumentTypesToAncestorFolders() {
        val root = LibraryRoot(treeUriString = "content://root", name = "Root")
        val index = listOf(
            bookIndex(root, "Fiction/Series/Book.pdf", DocumentType.PDF),
            bookIndex(root, "Fiction/Story.epub", DocumentType.EPUB),
            folderIndex(root, "Fiction"),
        )

        val typesByFolder = folderDocumentTypesById(index)

        assertEquals(
            setOf(DocumentType.PDF, DocumentType.EPUB),
            typesByFolder[LibraryRepository.folderId(root, "Fiction")],
        )
        assertEquals(
            setOf(DocumentType.PDF),
            typesByFolder[LibraryRepository.folderId(root, "Fiction/Series")],
        )
        assertFalse(typesByFolder.containsKey(LibraryRepository.folderId(root, "")))
    }

    @Test
    fun detectsCompletedProgressOnlyForFinalEpubAndPdfPositions() {
        assertTrue(
            isReadingProgressCompleted(
                ReadingProgress(
                    uriString = "epub",
                    type = DocumentType.EPUB,
                    epubChapterIndex = 4,
                    epubChapterCount = 5,
                    epubScrollFraction = 0.98f,
                ),
            ),
        )
        assertTrue(
            isReadingProgressCompleted(
                ReadingProgress(
                    uriString = "pdf",
                    type = DocumentType.PDF,
                    pdfPageIndex = 2,
                    pdfPageCount = 3,
                ),
            ),
        )
        assertFalse(
            isReadingProgressCompleted(
                ReadingProgress(
                    uriString = "txt",
                    type = DocumentType.TXT,
                    txtScrollFraction = 1f,
                ),
            ),
        )
    }

    @Test
    fun createsRootPickerEntriesFromLibraryRoots() {
        val entries = rootPickerEntries(
            listOf(
                LibraryRoot(treeUriString = "content://one", name = "One"),
                LibraryRoot(treeUriString = "content://two", name = "Two"),
            ),
        )

        assertEquals(listOf("One", "Two"), entries.map { it.name })
        assertEquals(listOf("", ""), entries.map { it.relativePath })
        assertEquals(listOf(0, 0), entries.map { it.depth })
    }

    private fun book(
        id: String,
        title: String,
        type: DocumentType = DocumentType.EPUB,
        addedAt: Long = 0L,
        pinned: Boolean = false,
    ): LibraryBookEntry =
        LibraryBookEntry(
            id = id,
            uriString = "content://book/$id",
            fileName = "$title.epub",
            title = title,
            type = type,
            addedAt = addedAt,
            pinned = pinned,
        )

    private fun folder(
        id: String,
        name: String,
        addedAt: Long,
        pinned: Boolean = false,
    ): LibraryFolderEntry =
        LibraryFolderEntry(
            id = id,
            relativePath = name,
            name = name,
            addedAt = addedAt,
            pinned = pinned,
        )

    private fun bookIndex(
        root: LibraryRoot,
        relativePath: String,
        type: DocumentType,
    ): LibrarySearchIndexEntry =
        LibrarySearchIndexEntry(
            result = LibrarySearchResult(
                id = "content://$relativePath",
                rootUriString = root.treeUriString,
                rootName = root.name,
                relativePath = relativePath,
                title = relativePath.substringAfterLast('/'),
                type = LibrarySearchResultType.BOOK,
                documentType = type,
                uriString = "content://$relativePath",
            ),
            searchText = relativePath.lowercase(),
        )

    private fun folderIndex(
        root: LibraryRoot,
        relativePath: String,
    ): LibrarySearchIndexEntry =
        LibrarySearchIndexEntry(
            result = LibrarySearchResult(
                id = LibraryRepository.folderId(root, relativePath),
                rootUriString = root.treeUriString,
                rootName = root.name,
                relativePath = relativePath,
                title = relativePath.substringAfterLast('/'),
                type = LibrarySearchResultType.FOLDER,
            ),
            searchText = relativePath.lowercase(),
        )
}
