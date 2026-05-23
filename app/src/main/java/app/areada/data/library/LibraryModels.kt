package app.areada.data.library
import app.areada.data.reader.DocumentType

data class LibraryRoot(
    val treeUriString: String,
    val name: String,
)

data class LibraryPathSegment(
    val relativePath: String,
    val name: String,
)

data class LibraryFolderPickerEntry(
    val rootUriString: String,
    val relativePath: String,
    val name: String,
    val depth: Int,
)

enum class LibrarySortMode(val label: String) {
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    DATE_ADDED_ASC("Oldest added"),
    DATE_ADDED_DESC("Newest added"),
    RECENTLY_OPENED("Recently opened"),
    READING_PROGRESS("Reading progress"),
    FILE_TYPE("File type"),
}

enum class LibraryFileFilter(
    val label: String,
    val documentTypes: Set<DocumentType>?,
) {
    ALL("All", null),
    EPUB("EPUB", setOf(DocumentType.EPUB)),
    PDF("PDF", setOf(DocumentType.PDF)),
    TXT("TXT", setOf(DocumentType.TXT)),
    FB2("FB2", setOf(DocumentType.FB2)),
    ARCHIVE("Archives", setOf(DocumentType.ZIP, DocumentType.ARCHIVE)),
}

data class LibraryFolderEntry(
    val id: String,
    val relativePath: String,
    val name: String,
    val addedAt: Long = 0L,
    val pinned: Boolean = false,
)

data class LibraryBookEntry(
    val id: String,
    val uriString: String,
    val fileName: String,
    val title: String,
    val type: DocumentType,
    val addedAt: Long = 0L,
    val pinned: Boolean = false,
)

data class LibraryBookLocation(
    val root: LibraryRoot,
    val folderRelativePath: String,
)

enum class LibrarySearchResultType {
    FOLDER,
    BOOK,
}

data class LibrarySearchResult(
    val id: String,
    val rootUriString: String,
    val rootName: String,
    val relativePath: String,
    val title: String,
    val type: LibrarySearchResultType,
    val documentType: DocumentType? = null,
    val uriString: String? = null,
)

data class LibrarySearchIndexEntry(
    val result: LibrarySearchResult,
    val searchText: String,
)

data class LibraryFolderState(
    val root: LibraryRoot,
    val currentRelativePath: String,
    val pathSegments: List<LibraryPathSegment>,
    val folders: List<LibraryFolderEntry>,
    val books: List<LibraryBookEntry>,
)
