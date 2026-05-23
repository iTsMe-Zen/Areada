package app.areada.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.areada.R
import app.areada.data.BookStatus
import app.areada.data.reader.DocumentType
import app.areada.data.library.LibraryBookEntry
import app.areada.data.library.LibraryFileFilter
import app.areada.data.library.LibraryFolderEntry
import app.areada.data.library.LibrarySearchResult
import app.areada.data.library.LibrarySearchResultType
import app.areada.data.reader.ReadingBookmark
import app.areada.data.reader.ReadingProgress
import app.areada.data.reader.RecentDocument
import app.areada.data.readingProgressPercent

@Composable
internal fun bookRowProgressLabel(
    type: DocumentType,
    progress: ReadingProgress?,
    status: BookStatus?,
): String? =
    if (status == BookStatus.Finished) {
        stringResource(R.string.finished)
    } else {
        readingProgressPercent(progress)?.let { percent -> "$percent%" }
    }

internal fun List<LibraryBookEntry>.filterBooksByLibraryFileFilter(filter: LibraryFileFilter): List<LibraryBookEntry> =
    filter.documentTypes?.let { types -> this.filter { book -> book.type in types } } ?: this

internal fun List<ReadingBookmark>.filterBookmarksByLibraryFileFilter(filter: LibraryFileFilter): List<ReadingBookmark> =
    filter.documentTypes?.let { types -> this.filter { bookmark -> bookmark.type in types } } ?: this

internal fun List<RecentDocument>.filterRecentsByLibraryFileFilter(filter: LibraryFileFilter): List<RecentDocument> =
    filter.documentTypes?.let { types -> this.filter { recent -> recent.type in types } } ?: this

internal fun List<LibraryFolderEntry>.filterFoldersByLibraryFileFilter(
    filter: LibraryFileFilter,
    folderDocumentTypesById: Map<String, Set<DocumentType>>,
): List<LibraryFolderEntry> {
    val types = filter.documentTypes ?: return this
    if (folderDocumentTypesById.isEmpty()) {
        return this
    }
    return filter { folder -> folderDocumentTypesById[folder.id]?.let { folderTypes -> types.any { it in folderTypes } } == true }
}

internal fun List<LibrarySearchResult>.filterSearchResultsByLibraryFileFilter(
    filter: LibraryFileFilter,
    folderDocumentTypesById: Map<String, Set<DocumentType>>,
): List<LibrarySearchResult> {
    val types = filter.documentTypes ?: return this
    if (folderDocumentTypesById.isEmpty()) {
        return filter { result ->
            result.type == LibrarySearchResultType.FOLDER || result.documentType in types
        }
    }
    return filter { result ->
        when (result.type) {
            LibrarySearchResultType.BOOK -> result.documentType in types
            LibrarySearchResultType.FOLDER -> folderDocumentTypesById[result.id]?.let { folderTypes -> types.any { it in folderTypes } } == true
        }
    }
}
