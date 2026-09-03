package app.areada.data

import app.areada.data.reader.DocumentType
import app.areada.data.reader.ReadingProgress
import kotlin.math.roundToInt

enum class BookStatus(val label: String) {
    Unread("Unread"),
    Reading("Reading"),
    Finished("Finished"),
}

fun bookStatusFromName(name: String?): BookStatus? =
    BookStatus.entries.firstOrNull { status -> status.name == name }

fun effectiveBookStatus(
    savedStatus: BookStatus?,
    progress: ReadingProgress?,
): BookStatus =
    savedStatus ?: if (progress == null) BookStatus.Unread else BookStatus.Reading

fun readingProgressPercent(progress: ReadingProgress?): Int? {
    if (progress == null) {
        return null
    }

    val fraction = when (progress.type) {
        DocumentType.EPUB -> {
            val chapterCount = progress.epubChapterCount.takeIf { it > 0 } ?: return null
            (progress.epubChapterIndex.coerceIn(0, chapterCount - 1) + progress.epubScrollFraction.coerceIn(0f, 1f)) /
                chapterCount.toFloat()
        }

        DocumentType.PDF -> {
            val pageCount = progress.pdfPageCount.takeIf { it > 0 } ?: return null
            (progress.pdfPageIndex.coerceIn(0, pageCount - 1) + 1) / pageCount.toFloat()
        }

        DocumentType.TXT,
        DocumentType.FB2,
        DocumentType.MARKDOWN -> progress.txtScrollFraction.coerceIn(0f, 1f)

        DocumentType.ZIP,
        DocumentType.ARCHIVE -> return null
    }

    return (fraction.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
}
