package app.areada.data.reader

import app.areada.data.library.moveListItem

internal object ReadingBookmarkActions {
    fun createEpubBookmark(
        document: ReaderDocument,
        chapterIndex: Int,
        chapterCount: Int,
        scrollFraction: Float,
        chapterTitle: String,
        timestamp: Long = System.currentTimeMillis(),
    ): ReadingBookmark {
        val safeIndex = chapterIndex.coerceAtLeast(0)
        val safeCount = chapterCount.coerceAtLeast(0)
        val safeScroll = scrollFraction.coerceIn(0f, 1f)
        val id = epubBookmarkId(document.uriString, safeIndex, safeScroll)
        return ReadingBookmark(
            id = id,
            uriString = document.uriString,
            title = document.title,
            type = document.type,
            positionLabel = if (safeCount > 0) {
                "Section ${safeIndex + 1} of $safeCount"
            } else {
                "Section ${safeIndex + 1}"
            },
            epubChapterIndex = safeIndex,
            epubChapterCount = safeCount,
            epubChapterTitle = chapterTitle,
            epubScrollFraction = safeScroll,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    fun createPdfBookmark(
        document: ReaderDocument,
        pageIndex: Int,
        pageCount: Int,
        timestamp: Long = System.currentTimeMillis(),
    ): ReadingBookmark {
        val safeIndex = pageIndex.coerceAtLeast(0)
        val safeCount = pageCount.coerceAtLeast(0)
        val id = pdfBookmarkId(document.uriString, safeIndex)
        return ReadingBookmark(
            id = id,
            uriString = document.uriString,
            title = document.title,
            type = document.type,
            positionLabel = if (safeCount > 0) {
                "Page ${safeIndex + 1} of $safeCount"
            } else {
                "Page ${safeIndex + 1}"
            },
            pdfPageIndex = safeIndex,
            pdfPageCount = safeCount,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    fun createTextBookmark(
        document: ReaderDocument,
        scrollFraction: Float,
        timestamp: Long = System.currentTimeMillis(),
    ): ReadingBookmark {
        val safeScroll = scrollFraction.coerceIn(0f, 1f)
        val id = txtBookmarkId(document.uriString, safeScroll)
        return ReadingBookmark(
            id = id,
            uriString = document.uriString,
            title = document.title,
            type = document.type,
            positionLabel = "${document.type.name} ${(safeScroll * 100f).toInt().coerceIn(0, 100)}%",
            txtScrollFraction = safeScroll,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    fun toggled(
        bookmarks: List<ReadingBookmark>,
        bookmark: ReadingBookmark,
    ): List<ReadingBookmark> =
        if (bookmarks.any { item -> item.id == bookmark.id }) {
            bookmarks.filterNot { item -> item.id == bookmark.id }
        } else {
            listOf(bookmark) + bookmarks
        }.sortedByDescending { item -> item.updatedAt }

    fun moved(
        bookmarks: List<ReadingBookmark>,
        bookmark: ReadingBookmark,
        offset: Int,
    ): List<ReadingBookmark> {
        val index = bookmarks.indexOfFirst { item -> item.id == bookmark.id }
        return moveListItem(bookmarks, index, offset)
    }

    fun toProgress(bookmark: ReadingBookmark): ReadingProgress =
        ReadingProgress(
            uriString = bookmark.uriString,
            type = bookmark.type,
            epubChapterIndex = bookmark.epubChapterIndex,
            epubChapterCount = bookmark.epubChapterCount,
            epubScrollFraction = bookmark.epubScrollFraction,
            pdfPageIndex = bookmark.pdfPageIndex,
            pdfPageCount = bookmark.pdfPageCount,
            txtScrollFraction = bookmark.txtScrollFraction,
            updatedAt = bookmark.updatedAt,
        )

    fun withDocument(
        bookmark: ReadingBookmark,
        document: ReaderDocument,
        timestamp: Long = System.currentTimeMillis(),
    ): ReadingBookmark {
        val newId = when (bookmark.type) {
            DocumentType.EPUB -> epubBookmarkId(document.uriString, bookmark.epubChapterIndex, bookmark.epubScrollFraction)
            DocumentType.PDF -> pdfBookmarkId(document.uriString, bookmark.pdfPageIndex)
            DocumentType.TXT,
            DocumentType.FB2,
            DocumentType.ZIP,
            DocumentType.ARCHIVE -> txtBookmarkId(document.uriString, bookmark.txtScrollFraction)
        }
        return bookmark.copy(
            id = newId,
            uriString = document.uriString,
            title = document.title,
            updatedAt = timestamp,
        )
    }
}
