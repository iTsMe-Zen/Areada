package app.areada.reader.pdf

import android.graphics.RectF

data class PdfTextRun(
    val text: String,
    val bounds: List<RectF>?,
)

data class PdfStructuredParagraph(
    val text: String,
    val isHeading: Boolean,
)

fun processPdfTextRuns(runs: List<PdfTextRun>): List<PdfStructuredParagraph> {
    if (runs.isEmpty()) return emptyList()

    val paragraphs = mutableListOf<String>()
    val paragraphHeights = mutableListOf<Float>()
    val currentLine = StringBuilder()
    val currentHeights = mutableListOf<Float>()

    fun avgHeightForRun(run: PdfTextRun): Float {
        val b = run.bounds ?: return 0f
        if (b.isEmpty()) return 0f
        return b.map { it.height() }.average().toFloat()
    }

    for (i in runs.indices) {
        val run = runs[i]
        val trimmed = run.text.trim()
        if (trimmed.isBlank()) continue

        val prevRun = runs.getOrNull(i - 1)

        val isPageBreak = prevRun != null && run.bounds != null && prevRun.bounds != null &&
            i > 0 && hasPageBreak(prevRun, run)

        val isLargeGap = prevRun != null && run.bounds != null && prevRun.bounds != null &&
            hasLargeVerticalGap(prevRun, run)

        if (isPageBreak || isLargeGap) {
            if (currentLine.isNotEmpty()) {
                paragraphs.add(currentLine.toString().trim())
                paragraphHeights.add(if (currentHeights.isEmpty()) 0f else currentHeights.average().toFloat())
                currentLine.clear()
                currentHeights.clear()
            }
        }

        if (currentLine.isNotEmpty()) {
            currentLine.append("\n")
        }
        currentLine.append(trimmed)
        currentHeights.add(avgHeightForRun(run))
    }

    if (currentLine.isNotEmpty()) {
        paragraphs.add(currentLine.toString().trim())
        paragraphHeights.add(if (currentHeights.isEmpty()) 0f else currentHeights.average().toFloat())
    }

    if (paragraphs.isEmpty()) return emptyList()

    val avgLength = paragraphs.map { it.length }.average().let { if (it.isNaN()) 100.0 else it }
    val headingThreshold = (avgLength * 0.4).coerceIn(10.0, 120.0)
    val avgParaHeight = paragraphHeights.filter { it > 0f }.average().let { if (it.isNaN()) 0f else it.toFloat() }

    return paragraphs.mapIndexed { idx, para ->
        val trimmed = para.trim()
        val isShort = trimmed.length < headingThreshold
        val hasNoNewlines = !trimmed.contains("\n")
        val startsWithCapital = trimmed.firstOrNull()?.isUpperCase() == true
        val paraHeight = paragraphHeights.getOrNull(idx) ?: 0f
        val isLargeFont = paraHeight > 0f && avgParaHeight > 0f && paraHeight > avgParaHeight * 1.32f
        val isLikelyHeading = isShort && hasNoNewlines && (startsWithCapital || isLargeFont) && trimmed.length > 2 || isLargeFont && isShort
        PdfStructuredParagraph(
            text = trimmed,
            isHeading = isLikelyHeading,
        )
    }
}

private fun hasPageBreak(prev: PdfTextRun, current: PdfTextRun): Boolean {
    val prevBounds = prev.bounds ?: return false
    val currBounds = current.bounds ?: return false
    if (prevBounds.isEmpty() || currBounds.isEmpty()) return false
    val prevBottom = prevBounds.maxOf { it.bottom }
    val currTop = currBounds.minOf { it.top }
    val pageHeight = prevBounds.maxOf { it.height() }.coerceAtLeast(100f)
    return currTop < prevBottom - pageHeight * 0.5f
}

private fun hasLargeVerticalGap(prev: PdfTextRun, current: PdfTextRun): Boolean {
    val prevBounds = prev.bounds ?: return false
    val currBounds = current.bounds ?: return false
    if (prevBounds.isEmpty() || currBounds.isEmpty()) return false
    val prevBottom = prevBounds.maxOf { it.bottom }
    val currTop = currBounds.minOf { it.top }
    val lineHeight = prevBounds.map { it.height() }.average().let { if (it.isNaN()) 14.0 else it }
    val gap = currTop - prevBottom
    return gap > lineHeight * 1.8
}
