package app.areada.ui.reader

import app.areada.data.reader.ReadingProgress

internal fun ReaderScreen.withLatestProgress(
    progressByUri: Map<String, ReadingProgress>,
): ReaderScreen {
    val progress = when (this) {
        is ReaderScreen.Epub -> progressByUri[document.uriString]
        is ReaderScreen.Fb2 -> progressByUri[document.uriString]
        is ReaderScreen.Pdf -> progressByUri[document.uriString]
        is ReaderScreen.Text -> progressByUri[document.uriString]
        is ReaderScreen.Markdown -> progressByUri[document.uriString]
        ReaderScreen.Home -> null
    } ?: return this

    return when (this) {
        is ReaderScreen.Epub -> copy(
            initialChapterIndex = progress.epubChapterIndex.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0)),
            initialScrollFraction = progress.epubScrollFraction.coerceIn(0f, 1f),
        )
        is ReaderScreen.Fb2 -> copy(
            initialChapterIndex = progress.epubChapterIndex.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0)),
            initialScrollFraction = progress.epubScrollFraction.coerceIn(0f, 1f),
        )
        is ReaderScreen.Pdf -> copy(
            initialPageIndex = progress.pdfPageIndex.coerceAtLeast(0),
            initialZoomScale = progress.pdfZoomScale.coerceAtLeast(1f),
            initialExtractedTextEnabled = progress.pdfExtractedTextEnabled,
            initialExtractedTextPageIndex = progress.pdfExtractedTextPageIndex.coerceAtLeast(0),
            initialExtractedTextScrollMode = progress.pdfExtractedTextScrollMode,
        )
        is ReaderScreen.Text -> copy(
            initialScrollFraction = progress.txtScrollFraction.coerceIn(0f, 1f),
        )
        is ReaderScreen.Markdown -> copy(
            initialChapterIndex = progress.epubChapterIndex.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0)),
            initialScrollFraction = progress.epubScrollFraction.coerceIn(0f, 1f),
        )
        ReaderScreen.Home -> this
    }
}
