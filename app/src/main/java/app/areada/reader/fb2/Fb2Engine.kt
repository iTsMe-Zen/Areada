package app.areada.reader.fb2

import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReaderRenderPalette
import app.areada.data.reader.renderPalette
import app.areada.reader.epub.RenderedChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

object Fb2Engine {

    suspend fun parse(
        context: android.content.Context,
        uri: android.net.Uri,
        fallbackTitle: String,
    ): Fb2Book = withContext(Dispatchers.IO) {
        val doc = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffered = java.io.BufferedInputStream(input)
            buffered.mark(16)
            val first = buffered.read()
            val second = buffered.read()
            buffered.reset()
            if (first == 'P'.code && second == 'K'.code) {
                parseZipForFb2(buffered)
            } else {
                parseFb2Xml(buffered)
            }
        } ?: error("Unable to read that FB2.")

        val title = firstElementText(doc, "book-title")?.trim()?.ifBlank { null }
            ?: fallbackTitle
        val author = firstAuthorName(doc)
        val images = extractBinaryImages(doc)
        val sections = extractSections(doc)

        val chapters = if (sections.isEmpty()) {
            val bodyText = extractAllBodyText(doc)
            if (bodyText.isNotBlank()) {
                listOf(Fb2Chapter(title = title, html = textToHtml(bodyText)))
            } else {
                emptyList()
            }
        } else {
            sections.map { section ->
                Fb2Chapter(
                    title = section.title.ifBlank { "Section" },
                    html = nodesToHtml(section.contentNodes, images),
                )
            }
        }

        Fb2Book(title = title, author = author, chapters = chapters, images = images)
    }

    fun render(
        book: Fb2Book,
        chapterIndex: Int,
        preferences: ReaderPreferences,
        paletteOverride: ReaderRenderPalette?,
        scrollToEnd: Boolean = false,
    ): RenderedChapter {
        val chapter = book.chapters.getOrNull(chapterIndex) ?: error("Chapter not found.")
        val palette = paletteOverride ?: preferences.themeMode.renderPalette()
        val fontSize = preferences.fontSizeSp.coerceIn(14, 30)
        val lineSpacing = preferences.lineSpacing.coerceIn(1.2f, 2.4f)
        val scrollThumbColor = palette.onSurfaceVariantHex

        val bodyContent = chapter.html
        val scrollScript = if (scrollToEnd) {
            "<script>window.scrollTo(0,document.body.scrollHeight)</script>"
        } else {
            ""
        }

        val html = """
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
                    ::-webkit-scrollbar {
                        width: 6px;
                        height: 6px;
                    }
                    ::-webkit-scrollbar-track {
                        background: transparent;
                    }
                    ::-webkit-scrollbar-thumb {
                        background-color: $scrollThumbColor;
                        border-radius: 999px;
                    }
                    body img, body table, body video, body picture, body figure {
                        box-sizing: border-box;
                        max-width: 100%;
                        height: auto;
                    }
                    body * {
                        text-decoration: none !important;
                        text-decoration-line: none !important;
                    }
                    p, div, span, li, td, th, blockquote, pre, em, strong, i, b, u, a, font, small, sup, sub, ins, section, article {
                        font-family: inherit !important;
                        font-size: inherit !important;
                        line-height: inherit !important;
                        text-decoration: none !important;
                        text-decoration-line: none !important;
                    }
                    h1 {
                        font-size: ${fontSize + 8}px !important;
                        margin-top: 1.5em;
                    }
                    h2 {
                        font-size: ${fontSize + 5}px !important;
                        margin-top: 1.2em;
                    }
                    h3, h4, h5, h6 {
                        font-size: ${fontSize + 3}px !important;
                        margin-top: 1em;
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
                    a * {
                        color: inherit !important;
                    }
                    blockquote, pre {
                        background: ${palette.surfaceHex};
                        color: ${palette.textHex};
                        border-radius: 0;
                        padding: 16px;
                        overflow: auto;
                    }
                    hr {
                        border: 0;
                        border-top: 1px solid ${palette.mutedHex};
                        margin: 1.5rem 0;
                    }
                </style>
            </head>
            <body>
                $bodyContent
                $scrollScript
            </body>
            </html>
        """.trimIndent()

        return RenderedChapter(
            title = chapter.title,
            baseUrl = "",
            html = html,
        )
    }

    private fun parseZipForFb2(input: java.io.InputStream): Document {
        java.util.zip.ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val name = entry.name?.lowercase(Locale.ROOT).orEmpty()
                    val looksFb2 = name.endsWith(".fb2") ||
                        name.endsWith(".fb2.xml") ||
                        name.endsWith(".fbz") ||
                        (name.contains("fb2") && name.endsWith(".xml"))
                    if (looksFb2) {
                        return parseFb2Xml(zip)
                    }
                }
                zip.closeEntry()
            }
        }
        error("ZIP does not contain a readable FB2 file.")
    }

    private fun parseFb2Xml(input: java.io.InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        return factory.newDocumentBuilder().parse(input)
    }

    private fun extractBinaryImages(doc: Document): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val allElements = doc.getElementsByTagName("*")
        for (i in 0 until allElements.length) {
            val node = allElements.item(i) ?: continue
            val localTag = node.nodeName.substringAfter(':').lowercase(Locale.ROOT)
            if (localTag != "binary") continue
            val id = node.attributes?.getNamedItem("id")?.nodeValue?.trim().orEmpty()
            if (id.isBlank()) continue
            val contentType = node.attributes?.getNamedItem("content-type")?.nodeValue?.trim().orEmpty()
            val base64 = node.textContent.orEmpty().replace(Regex("\\s"), "")
            if (base64.isBlank()) continue
            val mime = if (contentType.isNotBlank()) contentType else "image/png"
            result[id.lowercase(Locale.ROOT)] = "data:$mime;base64,$base64"
        }
        return result
    }

    private data class Fb2Section(
        val title: String,
        val contentNodes: List<Node>,
    )

    private fun extractSections(doc: Document): List<Fb2Section> {
        val bodies = doc.getElementsByTagName("body")
        val sections = mutableListOf<Fb2Section>()
        for (bodyIndex in 0 until bodies.length) {
            val body = bodies.item(bodyIndex)
            collectSections(body, sections)
        }
        return sections
    }

    private fun collectSections(node: Node, sections: MutableList<Fb2Section>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            val tag = child.nodeName.substringAfter(':').lowercase(Locale.ROOT)
            if (tag == "section") {
                val sectionTitle = extractSectionTitle(child)
                val contentNodes = mutableListOf<Node>()
                val grandchildren = child.childNodes
                for (j in 0 until grandchildren.length) {
                    val gc = grandchildren.item(j)
                    val gcTag = gc.nodeName.substringAfter(':').lowercase(Locale.ROOT)
                    if (gcTag != "title" && gcTag != "section") {
                        contentNodes.add(gc)
                    }
                }
                if (contentNodes.isNotEmpty()) {
                    sections.add(Fb2Section(title = sectionTitle, contentNodes = contentNodes))
                }
                val grandchildren2 = child.childNodes
                for (j in 0 until grandchildren2.length) {
                    val gc = grandchildren2.item(j)
                    val gcTag = gc.nodeName.substringAfter(':').lowercase(Locale.ROOT)
                    if (gcTag == "section") {
                        collectSections(gc, sections)
                    }
                }
            }
        }
    }

    private fun extractSectionTitle(sectionNode: Node): String {
        val children = sectionNode.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            val tag = child.nodeName.substringAfter(':').lowercase(Locale.ROOT)
            if (tag == "title") {
                return child.textContent.orEmpty().compactWhitespace().trim()
            }
        }
        return ""
    }

    private fun extractAllBodyText(doc: Document): String {
        val builder = StringBuilder()
        val bodies = doc.getElementsByTagName("body")
        for (i in 0 until bodies.length) {
            extractTextContent(bodies.item(i), builder)
        }
        return builder.toString().trim()
    }

    private fun extractTextContent(node: Node, builder: StringBuilder) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            val tag = child.nodeName.substringAfter(':').lowercase(Locale.ROOT)
            when (tag) {
                "binary", "description", "stylesheet" -> Unit
                "title", "subtitle", "p", "v",
                "strong", "emphasis", "a", "style" -> {
                    val text = child.textContent.orEmpty().compactWhitespace()
                    if (text.isNotBlank()) {
                        if (builder.isNotEmpty() && !builder.endsWith("\n\n")) {
                            builder.append("\n\n")
                        }
                        builder.append(text)
                    }
                }
                "empty-line" -> {
                    if (builder.isNotEmpty() && !builder.endsWith("\n\n")) {
                        builder.append("\n\n")
                    }
                }
                else -> extractTextContent(child, builder)
            }
        }
    }

    private fun nodesToHtml(nodes: List<Node>, images: Map<String, String>): String {
        val sb = StringBuilder()
        for (node in nodes) {
            nodeToHtml(node, sb, images)
        }
        return sb.toString()
    }

    private fun nodeToHtml(node: Node, sb: StringBuilder, images: Map<String, String>) {
        if (node.nodeType == Node.TEXT_NODE) {
            val text = node.textContent.orEmpty()
            if (text.isNotBlank()) {
                sb.append(htmlEscape(text))
            }
            return
        }
        if (node.nodeType != Node.ELEMENT_NODE) return

        val tag = node.nodeName.substringAfter(':').lowercase(Locale.ROOT)
        when (tag) {
            "binary", "description", "stylesheet",
            "isbn", "id", "version", "output",
            "program-used", "sequence", "lang", "src-lang",
            "translator", "annotation", "keywords", "genre",
            "first-name", "middle-name", "last-name",
            "nickname" -> return
            "p" -> {
                sb.append("<p>")
                appendChildContent(node, sb, images)
                sb.append("</p>")
            }
            "title" -> {
                sb.append("<h2>")
                appendChildContent(node, sb, images)
                sb.append("</h2>")
            }
            "subtitle" -> {
                sb.append("<h3>")
                appendChildContent(node, sb, images)
                sb.append("</h3>")
            }
            "v" -> {
                sb.append("<p><em>")
                appendChildContent(node, sb, images)
                sb.append("</em></p>")
            }
            "empty-line" -> sb.append("<br><br>")
            "strong", "b" -> {
                sb.append("<strong>")
                appendChildContent(node, sb, images)
                sb.append("</strong>")
            }
            "emphasis", "i" -> {
                sb.append("<em>")
                appendChildContent(node, sb, images)
                sb.append("</em>")
            }
            "a" -> {
                sb.append("<span>")
                appendChildContent(node, sb, images)
                sb.append("</span>")
            }
            "epigraph" -> {
                sb.append("<blockquote>")
                appendChildContent(node, sb, images)
                sb.append("</blockquote>")
            }
            "cite" -> {
                sb.append("<blockquote>")
                appendChildContent(node, sb, images)
                sb.append("</blockquote>")
            }
            "poem" -> {
                sb.append("<div>")
                appendChildContent(node, sb, images)
                sb.append("</div>")
            }
            "stanza" -> {
                sb.append("<div style='margin-bottom:1em'>")
                appendChildContent(node, sb, images)
                sb.append("</div>")
            }
            "text-author" -> {
                sb.append("<p><em>")
                appendChildContent(node, sb, images)
                sb.append("</em></p>")
            }
            "date" -> {
                sb.append("<p><small>")
                appendChildContent(node, sb, images)
                sb.append("</small></p>")
            }
            "image" -> {
                val href = node.attributes?.getNamedItem("href")?.nodeValue
                    ?: node.attributes?.getNamedItem("l:href")?.nodeValue
                    ?: node.attributes?.getNamedItem("xlink:href")?.nodeValue
                val id = href?.removePrefix("#")?.lowercase(Locale.ROOT)
                val dataUri = id?.let { images[it] }
                if (dataUri != null) {
                    sb.append("<img src=\"${htmlEscape(dataUri)}\" alt=\"\" style=\"max-width:100%;height:auto;\">")
                }
            }
            "section" -> {
                sb.append("<div>")
                appendChildContent(node, sb, images)
                sb.append("</div>")
            }
            else -> appendChildContent(node, sb, images)
        }
    }

    private fun appendChildContent(node: Node, sb: StringBuilder, images: Map<String, String>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            nodeToHtml(children.item(i), sb, images)
        }
    }

    private fun textToHtml(text: String): String {
        return text.split("\n\n").joinToString("\n") { para ->
            val trimmed = para.trim()
            if (trimmed.isNotEmpty()) "<p>${htmlEscape(trimmed)}</p>" else ""
        }
    }

    private fun htmlEscape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun firstElementText(document: Document, tagName: String): String? {
        val nodes = document.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeName.substringAfter(':').equals(tagName, ignoreCase = true)) {
                val text = node.textContent.orEmpty().compactWhitespace()
                if (text.isNotBlank()) return text
            }
        }
        return null
    }

    private fun firstAuthorName(document: Document): String? {
        val nodes = document.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeName.substringAfter(':').equals("author", ignoreCase = true)) {
                val parts = listOf("first-name", "middle-name", "last-name")
                    .mapNotNull { tagName -> childElementText(node, tagName) }
                val fullName = parts.joinToString(" ").compactWhitespace()
                if (fullName.isNotBlank()) return fullName
                val nickname = childElementText(node, "nickname")
                if (!nickname.isNullOrBlank()) return nickname
            }
        }
        return null
    }

    private fun childElementText(parent: Node, tagName: String): String? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeName.substringAfter(':').equals(tagName, ignoreCase = true)) {
                val text = child.textContent.orEmpty().compactWhitespace()
                if (text.isNotBlank()) return text
            }
        }
        return null
    }

    private fun String.compactWhitespace(): String =
        replace(Regex("\\s+"), " ").trim()
}
