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
    val txtScrollFraction: Float = 0f,
    val updatedAt: Long = System.currentTimeMillis(),
)
