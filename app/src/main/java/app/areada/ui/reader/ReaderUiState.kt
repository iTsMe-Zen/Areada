package app.areada.ui.reader

import app.areada.data.reader.DocumentType
import app.areada.data.BookStatus
import app.areada.data.BookNoteLink
import app.areada.data.library.LibraryBookEntry
import app.areada.data.library.LibraryFileFilter
import app.areada.data.library.LibraryFolderEntry
import app.areada.data.library.LibraryFolderPickerEntry
import app.areada.data.library.LibraryPathSegment
import app.areada.data.library.LibraryRoot
import app.areada.data.library.LibrarySearchResult
import app.areada.data.library.LibrarySortMode
import app.areada.data.reader.ReaderDocument
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReadingBookmark
import app.areada.data.reader.ReadingProgress
import app.areada.data.reader.RecentDocument
import app.areada.data.ZipBookEntry
import app.areada.reader.epub.EpubBook
import app.areada.reader.fb2.Fb2Book

data class ReaderUiState(
    val isLoading: Boolean = false,
    val recents: List<RecentDocument> = emptyList(),
    val bookmarks: List<ReadingBookmark> = emptyList(),
    val preferences: ReaderPreferences = ReaderPreferences(),
    val progressByUri: Map<String, ReadingProgress> = emptyMap(),
    val bookStatusByUri: Map<String, BookStatus> = emptyMap(),
    val bookNoteLinksByUri: Map<String, BookNoteLink> = emptyMap(),
    val lastNoteSectionByUri: Map<String, String> = emptyMap(),
    val libraryRoots: List<LibraryRoot> = emptyList(),
    val libraryFolderPickerEntries: List<LibraryFolderPickerEntry> = emptyList(),
    val selectedRootUriString: String? = null,
    val currentRelativePath: String = "",
    val currentPathSegments: List<LibraryPathSegment> = emptyList(),
    val currentFolders: List<LibraryFolderEntry> = emptyList(),
    val currentBooks: List<LibraryBookEntry> = emptyList(),
    val folderDocumentTypesById: Map<String, Set<DocumentType>> = emptyMap(),
    val searchQuery: String = "",
    val searchResults: List<LibrarySearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val librarySortMode: LibrarySortMode = LibrarySortMode.NAME_ASC,
    val libraryFileFilter: LibraryFileFilter = LibraryFileFilter.ALL,
    val selectedHomeTabName: String = "Collection",
    val pinnedLibraryItemIds: Set<String> = emptySet(),
    val libraryAddedAtById: Map<String, Long> = emptyMap(),
    val currentScreen: ReaderScreen = ReaderScreen.Home,
    val zipEntriesToChoose: List<ZipBookEntry> = emptyList(),
    val errorMessage: String? = null,
)

sealed interface ReaderScreen {
    data object Home : ReaderScreen

    data class Epub(
        val document: ReaderDocument,
        val book: EpubBook,
        val initialChapterIndex: Int,
        val initialScrollFraction: Float,
    ) : ReaderScreen

    data class Fb2(
        val document: ReaderDocument,
        val book: Fb2Book,
        val initialChapterIndex: Int,
        val initialScrollFraction: Float,
    ) : ReaderScreen

    data class Pdf(
        val document: ReaderDocument,
        val initialPageIndex: Int,
        val initialZoomScale: Float,
    ) : ReaderScreen

    data class Text(
        val document: ReaderDocument,
        val initialText: String,
        val initialScrollFraction: Float = 0f,
        val deleteOnDiscard: Boolean = false,
        val editable: Boolean = true,
        val sectionedNote: Boolean = false,
        val initialNoteSectionTitle: String? = null,
    ) : ReaderScreen
}
