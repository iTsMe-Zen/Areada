package app.areada.ui.reader

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import app.areada.data.reader.ReaderNavigationMode
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReaderRenderPalette
import app.areada.reader.epub.RenderedChapter
import app.areada.reader.pdf.PdfStructuredParagraph

/**
 * Extracted PDF text rendered as EPUB-style HTML in the shared [EpubWebView].
 * Same component, CSS, tap handling, search and selection toolbar as EPUB,
 * so behavior is identical (single scrollable page per section/document).
 */
@Composable
internal fun PdfExtractedTextContent(
    text: String,
    paragraphs: List<PdfStructuredParagraph>? = null,
    preferences: ReaderPreferences,
    renderPalette: ReaderRenderPalette,
    modifier: Modifier = Modifier,
    initialScrollFraction: Float = 0f,
    scrollRequest: EpubScrollRequest? = null,
    scrollEventId: Int = 0,
    scrollEventPixels: Int = 0,
    onScrollProgressChange: (Float) -> Unit = {},
    onPreviousSection: () -> Unit = {},
    onNextSection: () -> Unit = {},
    onOpenExternalLink: (Uri) -> Unit = {},
    onReaderTap: () -> Unit = {},
    searchQuery: String = "",
    searchRequest: Int = 0,
    searchBackwards: Boolean = false,
    onSearchResult: (current: Int, count: Int) -> Unit = { _, _ -> },
    navigationMode: ReaderNavigationMode = ReaderNavigationMode.SWIPE,
) {
    val html = remember(text, paragraphs, preferences.themeMode, preferences.fontChoice, preferences.fontSizeSp, preferences.lineSpacing, renderPalette) {
        buildPdfExtractedHtml(text, paragraphs, preferences, renderPalette)
    }
    val chapter = remember(html) {
        RenderedChapter(title = "", baseUrl = "about:blank", html = html)
    }
    EpubWebView(
        chapter = chapter,
        currentChapterFileUrl = "about:blank",
        preferences = preferences,
        navigationMode = navigationMode,
        renderPalette = renderPalette,
        initialScrollFraction = initialScrollFraction,
        scrollRequest = scrollRequest,
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .zIndex(0f),
        onScrollProgressChange = onScrollProgressChange,
        onScrollabilityChange = {},
        onReaderTap = onReaderTap,
        onSwipePrevious = onPreviousSection,
        onSwipeNext = onNextSection,
        onOpenLocalHref = { false },
        onOpenExternalLink = onOpenExternalLink,
        onNoteOpen = {},
        scrollEventId = scrollEventId,
        scrollEventPixels = scrollEventPixels,
        searchQuery = searchQuery,
        searchRequest = searchRequest,
        searchBackwards = searchBackwards,
        onSearchResult = onSearchResult,
    )
}

private fun buildPdfExtractedHtml(
    text: String,
    paragraphs: List<PdfStructuredParagraph>?,
    preferences: ReaderPreferences,
    palette: ReaderRenderPalette,
): String {
    val fontSize = preferences.fontSizeSp.coerceIn(14, 30)
    val lineSpacing = preferences.lineSpacing.coerceIn(1.2f, 2.4f)
    val body = StringBuilder()
    if (!paragraphs.isNullOrEmpty()) {
        paragraphs.forEach { para ->
            val content = escapePdfHtml(para.text).replace("\n", "<br/>")
            if (para.isHeading) {
                body.append("<h2>").append(content.ifBlank { "&nbsp;" }).append("</h2>")
            } else {
                body.append("<p>").append(content.ifBlank { "&nbsp;" }).append("</p>")
            }
        }
    } else {
        text.split("\n\n").filter { it.isNotBlank() }.forEach { part ->
            body.append("<p>").append(escapePdfHtml(part).replace("\n", "<br/>")).append("</p>")
        }
        if (body.isEmpty()) {
            body.append("<p>").append(escapePdfHtml(text).replace("\n", "<br/>")).append("</p>")
        }
    }
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
        <style>
        html {
          -webkit-text-size-adjust: none !important;
          text-size-adjust: none !important;
        }
        html, body {
          margin: 0;
          padding: 0;
          background: ${palette.backgroundHex};
          color: ${palette.textHex};
          font-family: ${preferences.fontChoice.cssFamily};
          font-size: ${fontSize}px !important;
          line-height: $lineSpacing !important;
        }
        body {
          padding: 76px max(18px, 5vw) 132px;
          word-break: break-word;
          overflow-wrap: break-word;
          overflow-wrap: anywhere;
          word-wrap: break-word;
        }
        h1 {
          font-size: ${fontSize + 8}px !important;
        }
        h2 {
          font-size: ${fontSize + 5}px !important;
        }
        h3, h4, h5, h6 {
          font-size: ${fontSize + 3}px !important;
        }
        p, li {
          margin-top: 0;
          margin-bottom: 1em;
        }
        a, a:link, a:visited {
          color: ${palette.accentHex};
          text-decoration: none !important;
          text-decoration-line: none !important;
          border-bottom: 0 !important;
        }
        </style>
        </head>
        <body>$body</body>
        </html>
    """.trimIndent()
}

private fun escapePdfHtml(raw: String): String {
    val out = StringBuilder(raw.length)
    raw.forEach { c ->
        when (c) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            else -> out.append(c)
        }
    }
    return out.toString()
}
