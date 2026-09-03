package app.areada.data.reader

data class ReadingProgress(
    val uriString: String,
    val type: DocumentType,
    val epubChapterIndex: Int = 0,
    val epubChapterCount: Int = 0,
    val epubScrollFraction: Float = 0f,
    val pdfPageIndex: Int = 0,
    val pdfPageCount: Int = 0,
    val pdfZoomScale: Float = 1f,
    val pdfExtractedTextEnabled: Boolean = false,
    val pdfExtractedTextPageIndex: Int = 0,
    val pdfExtractedTextScrollMode: Boolean = false,
    val pdfExtractedTextSectionIndex: Int = 0,
    val pdfExtractedTextSectionCount: Int = 0,
    val pdfExtractedTextScrollFraction: Float = 0f,
    val txtScrollFraction: Float = 0f,
    val updatedAt: Long = System.currentTimeMillis(),
)
