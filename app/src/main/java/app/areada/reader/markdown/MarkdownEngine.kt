package app.areada.reader.markdown

import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReaderRenderPalette
import app.areada.data.reader.renderPalette
import app.areada.reader.epub.RenderedChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MarkdownBook(
    val title: String,
    val content: String,
    val chapters: List<RenderedChapter> = listOf(RenderedChapter(title, "", content)),
)

object MarkdownEngine {

    suspend fun parse(
        context: android.content.Context,
        uri: android.net.Uri,
        fallbackTitle: String,
    ): MarkdownBook = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            reader.readText()
        } ?: error("Unable to read that Markdown file.")
        val title = extractTitle(text, fallbackTitle)
        MarkdownBook(title = title, content = text)
    }

    private fun extractTitle(text: String, fallback: String): String {
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                val extracted = trimmed.substringAfter("# ").trim()
                if (extracted.isNotBlank()) return extracted
            }
        }
        return fallback
    }

    fun render(
        book: MarkdownBook,
        chapterIndex: Int,
        preferences: ReaderPreferences,
        paletteOverride: ReaderRenderPalette?,
        scrollToEnd: Boolean = false,
        baseUrl: String = "",
    ): RenderedChapter {
        val palette = paletteOverride ?: preferences.themeMode.renderPalette()
        val fontSize = preferences.fontSizeSp.coerceIn(14, 30)
        val lineSpacing = preferences.lineSpacing.coerceIn(1.2f, 2.4f)
        val scrollThumbColor = palette.onSurfaceVariantHex
        val content = convertMarkdownToHtml(book.content)
        val scrollScript = if (scrollToEnd) {
            "<script>window.scrollTo(0,document.body.scrollHeight)</script>"
        } else { "" }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
                <style>
                    html { -webkit-text-size-adjust: none !important; text-size-adjust: none !important; }
                    html, body {
                        margin: 0; padding: 0;
                        background: ${palette.backgroundHex};
                        color: ${palette.textHex};
                        font-family: ${preferences.fontChoice.cssFamily};
                        font-size: ${fontSize}px !important;
                        line-height: $lineSpacing !important;
                    }
                    body { padding: 0 max(18px,5vw) 132px; text-align: justify; word-break: break-word; overflow-wrap: break-word; }
                    ::-webkit-scrollbar { width: 6px; height: 6px; }
                    ::-webkit-scrollbar-track { background: transparent; }
                    ::-webkit-scrollbar-thumb { background-color: $scrollThumbColor; border-radius: 999px; }
                    img, table, video, picture, figure { box-sizing: border-box; max-width: 100%; height: auto; }
                    body * { text-decoration: none !important; }
                    p, div, span, li, td, th, blockquote, pre, em, strong, i, b, u, a, font, small, sup, sub, ins, section, article {
                        font-family: inherit !important; font-size: inherit !important;
                        line-height: inherit !important; text-decoration: none !important;
                    }
                    h1 { font-size: ${fontSize + 8}px !important; margin-top: 1.5em; border-top: 1px solid ${palette.mutedHex}; padding-top: 0.75em; }
                    h2 { font-size: ${fontSize + 5}px !important; margin-top: 1.2em; border-top: 1px solid ${palette.mutedHex}; padding-top: 0.75em; }
                    h3, h4, h5, h6 { font-size: ${fontSize + 3}px !important; margin-top: 1em; }
                    p, li { margin-top: 0; margin-bottom: 1em; }
                    a, a:link, a:visited { color: ${palette.accentHex}; text-decoration: none !important; border-bottom: 0 !important; }
                    blockquote { border-left: 3px solid ${palette.mutedHex}; margin-left: 0; padding-left: 1em; color: ${palette.mutedHex}; }
                    pre, code { background: ${palette.surfaceHex}; border-radius: 4px; padding: 2px 4px; font-family: monospace !important; font-size: ${(fontSize * 0.9).toInt()}px !important; overflow-x: auto; }
                    pre { padding: 0.75em 1em; white-space: pre-wrap; word-break: break-all; }
                    hr { border: none; border-top: 1px solid ${palette.mutedHex}; margin: 1.5em 0; }
                    img { display: block; max-width: 100%; margin: 0.5em auto; }
                    ul, ol { padding-left: 1.5em; margin-bottom: 1em; }
                    li { margin-bottom: 0.25em; }
                </style>
            </head>
            <body>$content$scrollScript</body>
            </html>
        """.trimIndent()

        return RenderedChapter(html = html, baseUrl = baseUrl, title = book.title)
    }

    private fun convertMarkdownToHtml(markdown: String): String {
        val lines = markdown.lines()
        val result = StringBuilder()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            when {
                line.isBlank() -> { i++ }

                line.startsWith("```") -> {
                    val lang = line.removePrefix("```").trim()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].startsWith("```")) {
                        codeLines.add(escapeHtml(lines[i]))
                        i++
                    }
                    i++
                    val codeHtml = if (lang.isNotEmpty()) {
                        "<pre><code class=\"language-$lang\">${codeLines.joinToString("<br>")}</code></pre>"
                    } else {
                        "<pre><code>${codeLines.joinToString("<br>")}</code></pre>"
                    }
                    result.append(codeHtml)
                }

                line.trimStart().startsWith("<") && line.contains(">") && (
                    line.trimStart().startsWith("<p") ||
                    line.trimStart().startsWith("</p") ||
                    line.trimStart().startsWith("<br") ||
                    line.trimStart().startsWith("<img") ||
                    line.trimStart().startsWith("<a ") ||
                    line.trimStart().startsWith("</a") ||
                    line.trimStart().startsWith("<b>") ||
                    line.trimStart().startsWith("</b") ||
                    line.trimStart().startsWith("<i>") ||
                    line.trimStart().startsWith("</i") ||
                    line.trimStart().startsWith("<div") ||
                    line.trimStart().startsWith("</div") ||
                    line.trimStart().startsWith("<span") ||
                    line.trimStart().startsWith("</span") ||
                    line.trimStart().startsWith("<table") ||
                    line.trimStart().startsWith("</table") ||
                    line.trimStart().startsWith("<tr") ||
                    line.trimStart().startsWith("</tr") ||
                    line.trimStart().startsWith("<td") ||
                    line.trimStart().startsWith("</td") ||
                    line.trimStart().startsWith("<th") ||
                    line.trimStart().startsWith("</th") ||
                    line.trimStart().startsWith("<ul") ||
                    line.trimStart().startsWith("</ul") ||
                    line.trimStart().startsWith("<ol") ||
                    line.trimStart().startsWith("</ol") ||
                    line.trimStart().startsWith("<li") ||
                    line.trimStart().startsWith("</li") ||
                    line.trimStart().startsWith("<h") ||
                    line.trimStart().startsWith("<pre") ||
                    line.trimStart().startsWith("</pre") ||
                    line.trimStart().startsWith("<code") ||
                    line.trimStart().startsWith("</code") ||
                    line.trimStart().startsWith("<hr") ||
                    line.trimStart().startsWith("<sup") ||
                    line.trimStart().startsWith("<sub") ||
                    line.trimStart().startsWith("<ins") ||
                    line.trimStart().startsWith("<del") ||
                    line.trimStart().startsWith("<details") ||
                    line.trimStart().startsWith("<summary") ||
                    line.trimStart().startsWith("<blockquote") ||
                    line.trimStart().startsWith("<dl") ||
                    line.trimStart().startsWith("<dt") ||
                    line.trimStart().startsWith("<dd")
                ) -> {
                    result.append(line)
                    i++
                }

                line.startsWith("---") || line.startsWith("***") || line.startsWith("___") -> {
                    result.append("<hr>")
                    i++
                }

                line.startsWith("#") && line.length > 1 && line[1] == ' ' -> {
                    result.append("<h1>${convertInline(line.substring(2).trim())}</h1>")
                    i++
                }
                line.startsWith("##") && line.length > 2 && line[2] == ' ' -> {
                    result.append("<h2>${convertInline(line.substring(3).trim())}</h2>")
                    i++
                }
                line.startsWith("###") && line.length > 3 && line[3] == ' ' -> {
                    result.append("<h3>${convertInline(line.substring(4).trim())}</h3>")
                    i++
                }
                line.startsWith("####") && line.length > 4 && line[4] == ' ' -> {
                    result.append("<h4>${convertInline(line.substring(5).trim())}</h4>")
                    i++
                }
                line.startsWith("#####") && line.length > 5 && line[5] == ' ' -> {
                    result.append("<h5>${convertInline(line.substring(6).trim())}</h5>")
                    i++
                }
                line.startsWith("######") && line.length > 6 && line[6] == ' ' -> {
                    result.append("<h6>${convertInline(line.substring(7).trim())}</h6>")
                    i++
                }

                line.startsWith("> ") -> {
                    val quoteLines = mutableListOf<String>()
                    while (i < lines.size && (lines[i].startsWith("> ") || lines[i].isBlank())) {
                        if (lines[i].startsWith("> ")) {
                            quoteLines.add(convertInline(lines[i].removePrefix("> ").trim()))
                        }
                        i++
                    }
                    result.append("<blockquote>${quoteLines.joinToString("<br>")}</blockquote>")
                }

                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size && (lines[i].startsWith("- ") || lines[i].startsWith("* ") || lines[i].startsWith("+ "))) {
                        items.add("<li>${convertInline(lines[i].substringAfter(" ").trimStart())}</li>")
                        i++
                    }
                    if (items.isNotEmpty()) {
                        result.append("<ul>${items.joinToString("")}</ul>")
                    }
                }

                line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size && lines[i].matches(Regex("^\\d+\\.\\s+.*"))) {
                        items.add("<li>${convertInline(lines[i].replaceFirst(Regex("^\\d+\\.\\s+"), ""))}</li>")
                        i++
                    }
                    if (items.isNotEmpty()) {
                        result.append("<ol>${items.joinToString("")}</ol>")
                    }
                }

                line.startsWith("    ") || line.startsWith("\t") -> {
                    val codeLines = mutableListOf<String>()
                    while (i < lines.size && (lines[i].startsWith("    ") || lines[i].startsWith("\t"))) {
                        codeLines.add(escapeHtml(
                            if (lines[i].startsWith("    ")) lines[i].removePrefix("    ")
                            else lines[i].removePrefix("\t")
                        ))
                        i++
                    }
                    result.append("<pre><code>${codeLines.joinToString("<br>")}</code></pre>")
                }

                else -> {
                    result.append("<p>${convertInline(line)}</p>")
                    i++
                }
            }
        }
        return result.toString()
    }

    private fun convertInline(text: String): String {
        var result = escapeHtml(text)

        result = result.replace(Regex("\\*\\*\\*(.+?)\\*\\*\\*"), "<strong><em>$1</em></strong>")
        result = result.replace(Regex("___([^_]+)___"), "<strong><em>$1</em></strong>")
        result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        result = result.replace(Regex("__([^_]+)__"), "<strong>$1</strong>")
        result = result.replace(Regex("(?<!\\*)\\*([^*]+?)\\*(?!\\*)"), "<em>$1</em>")
        result = result.replace(Regex("(?<!_)_([^_]+?)_(?!_)"), "<em>$1</em>")
        result = result.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        result = result.replace(Regex("!\\[(.*?)]\\((.*?)\\)"), "<img src=\"$2\" alt=\"$1\" loading=\"lazy\">")
        result = result.replace(Regex("\\[([^*`\n]+?)]\\((.*?)\\)"), "<a href=\"$2\">$1</a>")

        return result
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
