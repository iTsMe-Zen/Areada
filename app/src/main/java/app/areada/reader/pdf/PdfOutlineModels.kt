package app.areada.reader.pdf

data class PdfOutlineEntry(
    val title: String,
    val pageNumber: Int,
    val children: List<PdfOutlineEntry>,
)

data class PdfSection(
    val title: String,
    val startPage: Int,
    val endPage: Int,
)

fun flattenPdfOutline(
    entries: List<PdfOutlineEntry>,
    totalCount: Int,
): List<PdfSection> {
    val flat = mutableListOf<PdfOutlineEntry>()
    fun traverse(nodes: List<PdfOutlineEntry>) {
        for (node in nodes) {
            flat.add(node)
            traverse(node.children)
        }
    }
    traverse(entries)
    if (flat.isEmpty()) return emptyList()
    flat.sortBy { it.pageNumber }
    return flat.mapIndexed { index, entry ->
        val start = entry.pageNumber.coerceAtLeast(0)
        val end = if (index < flat.lastIndex) {
            flat[index + 1].pageNumber.coerceAtMost(totalCount)
        } else {
            totalCount
        }
        PdfSection(
            title = entry.title.ifBlank { "Section ${index + 1}" },
            startPage = start,
            endPage = end.coerceAtLeast(start + 1),
        )
    }
}
