package app.areada.data.reader

import kotlin.math.roundToInt

data class ReadingBookmark(
    val id: String,
    val uriString: String,
    val title: String,
    val type: DocumentType,
    val positionLabel: String,
    val customName: String? = null,
    val epubChapterIndex: Int = 0,
    val epubChapterCount: Int = 0,
    val epubChapterTitle: String = "",
    val epubScrollFraction: Float = 0f,
    val pdfPageIndex: Int = 0,
    val pdfPageCount: Int = 0,
    val pdfExtractedTextSectionIndex: Int = 0,
    val pdfExtractedTextSectionCount: Int = 0,
    val pdfExtractedTextScrollFraction: Float = 0f,
    val txtScrollFraction: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

fun epubBookmarkId(
    uriString: String,
    chapterIndex: Int,
    scrollFraction: Float,
): String = "epub|$uriString|${chapterIndex.coerceAtLeast(0)}|${scrollBucket(scrollFraction)}"

fun pdfBookmarkId(
    uriString: String,
    pageIndex: Int,
): String = "pdf|$uriString|${pageIndex.coerceAtLeast(0)}"

fun pdfExtractedTextBookmarkId(
    uriString: String,
    sectionIndex: Int,
    scrollFraction: Float,
): String = "pdf-extract-text|$uriString|${sectionIndex.coerceAtLeast(0)}|${scrollBucket(scrollFraction)}"

fun txtBookmarkId(
    uriString: String,
    scrollFraction: Float,
): String = "txt|$uriString|${scrollBucket(scrollFraction)}"

private fun scrollBucket(scrollFraction: Float): Int =
    (scrollFraction.coerceIn(0f, 1f) * 20f).roundToInt().coerceIn(0, 20)
