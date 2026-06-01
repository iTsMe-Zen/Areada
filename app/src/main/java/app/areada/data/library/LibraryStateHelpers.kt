package app.areada.data.library

import app.areada.data.BookStatus
import app.areada.data.reader.DocumentType
import app.areada.data.reader.ReadingBookmark
import app.areada.data.reader.ReadingProgress
import app.areada.data.reader.RecentDocument
import app.areada.data.readingProgressPercent

internal fun List<LibraryRoot>.libraryRootSignature(): String =
    joinToString(separator = "|") { root -> root.treeUriString }

internal fun rootPickerEntries(roots: List<LibraryRoot>): List<LibraryFolderPickerEntry> =
    roots.map { root ->
        LibraryFolderPickerEntry(
            rootUriString = root.treeUriString,
            relativePath = "",
            name = root.name,
            depth = 0,
        )
    }

internal fun sameLibraryFolders(
    left: List<LibraryFolderEntry>,
    right: List<LibraryFolderEntry>,
): Boolean =
    left.size == right.size && left.zip(right).all { (leftFolder, rightFolder) ->
        leftFolder.id == rightFolder.id &&
            leftFolder.name == rightFolder.name &&
            leftFolder.pinned == rightFolder.pinned
    }

internal fun sameLibraryBooks(
    left: List<LibraryBookEntry>,
    right: List<LibraryBookEntry>,
): Boolean =
    left.size == right.size && left.zip(right).all { (leftBook, rightBook) ->
        leftBook.id == rightBook.id &&
            leftBook.title == rightBook.title &&
            leftBook.fileName == rightBook.fileName &&
            leftBook.type == rightBook.type &&
            leftBook.pinned == rightBook.pinned
    }

internal fun folderDocumentTypesById(
    index: List<LibrarySearchIndexEntry>,
): Map<String, Set<DocumentType>> {
    val typesByFolder = linkedMapOf<String, MutableSet<DocumentType>>()
    index.forEach { entry ->
        val result = entry.result
        if (result.type != LibrarySearchResultType.BOOK) {
            return@forEach
        }
        val documentType = result.documentType ?: return@forEach
        val pathSegments = result.relativePath
            .split('/')
            .filter { segment -> segment.isNotBlank() }
        if (pathSegments.size <= 1) {
            return@forEach
        }

        var folderPath = ""
        pathSegments.dropLast(1).forEach { segment ->
            folderPath = if (folderPath.isBlank()) segment else "$folderPath/$segment"
            val folderId = LibraryRepository.folderId(
                root = LibraryRoot(
                    treeUriString = result.rootUriString,
                    name = result.rootName,
                ),
                relativePath = folderPath,
            )
            typesByFolder.getOrPut(folderId) { linkedSetOf() } += documentType
        }
    }
    return typesByFolder.mapValues { (_, types) -> types.toSet() }
}

internal fun sortLibraryFolders(
    folders: List<LibraryFolderEntry>,
    sortMode: LibrarySortMode,
): List<LibraryFolderEntry> =
    sortLibraryEntries(
        items = folders,
        sortMode = sortMode,
        title = { it.name },
        addedAt = { it.addedAt },
        pinned = { it.pinned },
    )

internal fun sortLibraryBooks(
    books: List<LibraryBookEntry>,
    sortMode: LibrarySortMode,
    progressByUri: Map<String, ReadingProgress> = emptyMap(),
    recents: List<RecentDocument> = emptyList(),
    bookStatusByUri: Map<String, BookStatus> = emptyMap(),
): List<LibraryBookEntry> =
    sortLibraryEntries(
        items = books,
        sortMode = sortMode,
        title = { it.title },
        addedAt = { it.addedAt },
        pinned = { it.pinned },
        typeOrdinal = { it.type.ordinal },
        recentlyOpenedAt = { book ->
            recents.firstOrNull { recent -> recent.uriString == book.uriString }?.lastOpenedAt ?: 0L
        },
        progressPercent = { book ->
            when (bookStatusByUri[book.uriString]) {
                BookStatus.Finished -> 100
                else -> readingProgressPercent(progressByUri[book.uriString]) ?: -1
            }
        },
    )

private fun <T> sortLibraryEntries(
    items: List<T>,
    sortMode: LibrarySortMode,
    title: (T) -> String,
    addedAt: (T) -> Long,
    pinned: (T) -> Boolean,
    typeOrdinal: (T) -> Int = { 0 },
    recentlyOpenedAt: (T) -> Long = { 0L },
    progressPercent: (T) -> Int = { -1 },
): List<T> {
    val sorted = when (sortMode) {
        LibrarySortMode.NAME_ASC -> items.sortedWith(NaturalSort.comparator(title))
        LibrarySortMode.NAME_DESC -> items.sortedWith(reverseComparator(NaturalSort.comparator(title)))
        LibrarySortMode.DATE_ADDED_ASC -> items.sortedBy { addedAt(it) }
        LibrarySortMode.DATE_ADDED_DESC -> items.sortedByDescending { addedAt(it) }
        LibrarySortMode.RECENTLY_OPENED -> items
            .sortedWith(compareByDescending<T> { recentlyOpenedAt(it) }.then(NaturalSort.comparator(title)))
        LibrarySortMode.RECENTLY_OPENED_ASC -> items
            .sortedWith(compareBy<T> { recentlyOpenedAt(it) }.then(NaturalSort.comparator(title)))
        LibrarySortMode.READING_PROGRESS -> items
            .sortedWith(compareByDescending<T> { progressPercent(it) }.then(NaturalSort.comparator(title)))
        LibrarySortMode.READING_PROGRESS_ASC -> items
            .sortedWith(compareBy<T> { progressPercent(it) }.then(NaturalSort.comparator(title)))
        LibrarySortMode.FILE_TYPE -> items
            .sortedWith(compareBy<T> { typeOrdinal(it) }.then(NaturalSort.comparator(title)))
        LibrarySortMode.FILE_TYPE_DESC -> items
            .sortedWith(compareByDescending<T> { typeOrdinal(it) }.then(NaturalSort.comparator(title)))
    }

    return sorted.sortedByDescending { pinned(it) }
}

private fun <T> reverseComparator(comparator: Comparator<T>): Comparator<T> =
    Comparator { left, right -> comparator.compare(right, left) }

internal fun isReadingProgressCompleted(progress: ReadingProgress): Boolean =
    when (progress.type) {
        DocumentType.EPUB -> progress.epubChapterCount > 0 &&
            progress.epubChapterIndex >= progress.epubChapterCount - 1 &&
            progress.epubScrollFraction >= 0.98f

        DocumentType.PDF -> progress.pdfPageCount > 0 &&
            progress.pdfPageIndex >= progress.pdfPageCount - 1

        DocumentType.TXT,
        DocumentType.FB2,
        DocumentType.ZIP,
        DocumentType.ARCHIVE -> false
    }

internal fun sortRecentDocuments(
    recents: List<RecentDocument>,
    sortMode: LibrarySortMode,
    progressByUri: Map<String, ReadingProgress> = emptyMap(),
): List<RecentDocument> = when (sortMode) {
    LibrarySortMode.NAME_ASC -> recents.sortedWith(NaturalSort.comparator<RecentDocument> { it.title })
    LibrarySortMode.NAME_DESC -> recents.sortedWith(reverseComparator(NaturalSort.comparator { it.title }))
    LibrarySortMode.DATE_ADDED_ASC,
    LibrarySortMode.RECENTLY_OPENED_ASC -> recents.sortedBy { it.lastOpenedAt }
    LibrarySortMode.DATE_ADDED_DESC,
    LibrarySortMode.RECENTLY_OPENED -> recents.sortedByDescending { it.lastOpenedAt }
    LibrarySortMode.READING_PROGRESS_ASC -> recents.sortedWith(
        compareBy<RecentDocument> { readingProgressPercent(progressByUri[it.uriString]) ?: -1 }
            .then(NaturalSort.comparator { it.title }),
    )
    LibrarySortMode.READING_PROGRESS -> recents.sortedWith(
        compareByDescending<RecentDocument> { readingProgressPercent(progressByUri[it.uriString]) ?: -1 }
            .then(NaturalSort.comparator { it.title }),
    )
    LibrarySortMode.FILE_TYPE -> recents.sortedWith(
        compareBy<RecentDocument> { it.type.ordinal }.then(NaturalSort.comparator { it.title }),
    )
    LibrarySortMode.FILE_TYPE_DESC -> recents.sortedWith(
        compareByDescending<RecentDocument> { it.type.ordinal }.then(NaturalSort.comparator { it.title }),
    )
}

internal fun sortReadingBookmarks(
    bookmarks: List<ReadingBookmark>,
    sortMode: LibrarySortMode,
): List<ReadingBookmark> = when (sortMode) {
    LibrarySortMode.NAME_ASC -> bookmarks.sortedWith(NaturalSort.comparator<ReadingBookmark> { it.title })
    LibrarySortMode.NAME_DESC -> bookmarks.sortedWith(reverseComparator(NaturalSort.comparator { it.title }))
    LibrarySortMode.DATE_ADDED_ASC -> bookmarks.sortedBy { it.createdAt }
    LibrarySortMode.DATE_ADDED_DESC -> bookmarks.sortedByDescending { it.createdAt }
    LibrarySortMode.RECENTLY_OPENED,
    LibrarySortMode.RECENTLY_OPENED_ASC -> bookmarks.sortedByDescending { it.updatedAt }
    LibrarySortMode.READING_PROGRESS,
    LibrarySortMode.READING_PROGRESS_ASC -> bookmarks.sortedWith(NaturalSort.comparator { it.title })
    LibrarySortMode.FILE_TYPE -> bookmarks.sortedWith(
        compareBy<ReadingBookmark> { it.type.ordinal }.then(NaturalSort.comparator { it.title }),
    )
    LibrarySortMode.FILE_TYPE_DESC -> bookmarks.sortedWith(
        compareByDescending<ReadingBookmark> { it.type.ordinal }.then(NaturalSort.comparator { it.title }),
    )
}
