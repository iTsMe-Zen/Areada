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
    RECENTLY_OPENED_ASC("Least recently opened"),
    READING_PROGRESS("Reading progress"),
    READING_PROGRESS_ASC("Reading progress"),
    FILE_TYPE("File type"),
    FILE_TYPE_DESC("File type Z-A"),
    ;

    val baseOption: LibrarySortMode get() = when (this) {
        NAME_ASC, NAME_DESC -> NAME_ASC
        DATE_ADDED_ASC, DATE_ADDED_DESC -> DATE_ADDED_ASC
        RECENTLY_OPENED, RECENTLY_OPENED_ASC -> RECENTLY_OPENED
        READING_PROGRESS, READING_PROGRESS_ASC -> READING_PROGRESS
        FILE_TYPE, FILE_TYPE_DESC -> FILE_TYPE
    }

    val isDirectional: Boolean get() = true

    fun toggled(): LibrarySortMode = when (this) {
        NAME_ASC -> NAME_DESC
        NAME_DESC -> NAME_ASC
        DATE_ADDED_ASC -> DATE_ADDED_DESC
        DATE_ADDED_DESC -> DATE_ADDED_ASC
        READING_PROGRESS -> READING_PROGRESS_ASC
        READING_PROGRESS_ASC -> READING_PROGRESS
        FILE_TYPE -> FILE_TYPE_DESC
        FILE_TYPE_DESC -> FILE_TYPE
        RECENTLY_OPENED -> RECENTLY_OPENED_ASC
        RECENTLY_OPENED_ASC -> RECENTLY_OPENED
    }

    val isAscending: Boolean get() = when (this) {
        NAME_ASC, DATE_ADDED_ASC, READING_PROGRESS_ASC, FILE_TYPE, RECENTLY_OPENED_ASC -> true
        else -> false
    }

    companion object {
        val baseOptions = listOf(
            NAME_ASC, DATE_ADDED_ASC, RECENTLY_OPENED, READING_PROGRESS, FILE_TYPE,
        )
    }
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
