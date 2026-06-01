package app.areada.reader.fb2

import android.content.Context
import android.net.Uri
import org.w3c.dom.Node
import java.util.Locale

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object LocalTextExtractor {
    private const val MAX_EXTRACTED_CHARS = 1_200_000

    fun readFb2(
        context: Context,
        uri: Uri,
    ): String =
        readFb2Document(context, uri).text

    fun readFb2Document(
        context: Context,
        uri: Uri,
    ): LocalFb2Document =
        context.contentResolver.openInputStream(uri)?.use { input ->
            parseFb2OrZip(input)
        } ?: throw IllegalArgumentException("Unable to read that FB2.")

    internal fun extractFb2Text(input: InputStream): String =
        parseFb2OrZip(input).text

    internal fun extractFb2Document(input: InputStream): LocalFb2Document =
        parseFb2OrZip(input)

    private fun parseFb2OrZip(input: InputStream): LocalFb2Document {
        val buffered = BufferedInputStream(input)
        buffered.mark(16)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        if (first == 'P'.code && second == 'K'.code) {
            var fb2Entry: InputStream? = null
            var firstNonDirEntry: InputStream? = null
            ZipInputStream(buffered).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val name = entry.name?.lowercase(Locale.ROOT).orEmpty()
                    val looksFb2 = name.endsWith(".fb2") ||
                        name.endsWith(".fb2.xml") ||
                        name.endsWith(".fbz") ||
                        (name.contains("fb2") && name.endsWith(".xml"))
                    if (looksFb2 && fb2Entry == null) {
                        fb2Entry = zip
                    } else if (firstNonDirEntry == null) {
                        firstNonDirEntry = zip
                    }
                    if (fb2Entry == null) {
                        zip.closeEntry()
                    } else {
                        break
                    }
                }
                val stream = fb2Entry ?: firstNonDirEntry
                if (stream != null) {
                    return runCatching { parseFb2(stream) }
                        .getOrDefault(LocalFb2Document())
                }
            }
            return LocalFb2Document()
        }
        return parseFb2(buffered)
    }

    private fun parseFb2(input: InputStream): LocalFb2Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val document = factory.newDocumentBuilder().parse(input)
        val builder = StringBuilder()
        val title = firstElementText(document, "book-title")
        val author = firstAuthorName(document)

        title?.let { title ->
            appendBlock(builder, title)
        }
        val bodies = document.getElementsByTagName("body")
        if (bodies.length == 0) {
            appendBlock(builder, document.documentElement?.textContent.orEmpty())
        } else {
            for (index in 0 until bodies.length) {
                appendFb2Children(bodies.item(index), builder)
            }
        }

        return LocalFb2Document(
            text = builder.toString().trim().take(MAX_EXTRACTED_CHARS),
            title = title,
            author = author,
        )
    }

    private fun appendFb2Children(
        node: Node,
        builder: StringBuilder,
    ) {
        val children = node.childNodes
        for (index in 0 until children.length) {
            if (builder.length >= MAX_EXTRACTED_CHARS) {
                return
            }
            val child = children.item(index)
            val tag = child.nodeName.substringAfter(':').lowercase(Locale.ROOT)
            when (tag) {
                "binary", "description", "stylesheet",
                "coverpage", "isbn", "id", "version", "output",
                "program-used", "sequence", "lang", "src-lang",
                "translator", "annotation", "keywords", "genre",
                "first-name", "middle-name", "last-name",
                "nickname", "image" -> Unit
                "title", "subtitle", "p", "v",
                "strong", "emphasis", "a", "style" ->
                    appendBlock(builder, child.textContent.orEmpty())
                "empty-line" -> appendNewline(builder)
                else -> appendFb2Children(child, builder)
            }
        }
    }

    private fun firstElementText(
        document: org.w3c.dom.Document,
        tagName: String,
    ): String? {
        forEachElementByLocalName(document, tagName) { node ->
            val text = node.textContent.orEmpty().compactWhitespace()
            if (text.isNotBlank()) {
                return text
            }
        }
        return null
    }

    private fun firstAuthorName(document: org.w3c.dom.Document): String? {
        forEachElementByLocalName(document, "author") { authorNode ->
            val parts = listOf("first-name", "middle-name", "last-name")
                .mapNotNull { tagName -> firstChildElementText(authorNode, tagName) }
            val fullName = parts.joinToString(" ").compactWhitespace()
            if (fullName.isNotBlank()) {
                return fullName
            }

            val nickname = firstChildElementText(authorNode, "nickname")
            if (!nickname.isNullOrBlank()) {
                return nickname
            }

            val fallback = authorNode.textContent.orEmpty().compactWhitespace()
            if (fallback.isNotBlank()) {
                return fallback
            }
        }
        return null
    }

    private fun firstChildElementText(
        node: Node,
        tagName: String,
    ): String? {
        val children = node.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeName.substringAfter(':').equals(tagName, ignoreCase = true)) {
                val text = child.textContent.orEmpty().compactWhitespace()
                if (text.isNotBlank()) {
                    return text
                }
            }
        }
        return null
    }

    private inline fun forEachElementByLocalName(
        document: org.w3c.dom.Document,
        tagName: String,
        block: (Node) -> Unit,
    ) {
        val nodes = document.getElementsByTagName("*")
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeName.substringAfter(':').equals(tagName, ignoreCase = true)) {
                block(node)
            }
        }
    }

    private fun appendBlock(
        builder: StringBuilder,
        rawText: String,
    ) {
        val text = rawText.compactWhitespace()
        if (text.isBlank()) {
            return
        }
        if (builder.isNotEmpty() && !builder.endsWith("\n\n")) {
            builder.append("\n\n")
        }
        builder.append(text)
    }

    private fun appendNewline(builder: StringBuilder) {
        if (builder.isNotEmpty() && !builder.endsWith("\n\n")) {
            builder.append("\n\n")
        }
    }

    private fun String.compactWhitespace(): String =
        replace(Regex("\\s+"), " ").trim()

    private fun StringBuilder.endsWith(suffix: String): Boolean =
        length >= suffix.length && substring(length - suffix.length, length) == suffix
}

data class LocalFb2Document(
    val text: String = "",
    val title: String? = null,
    val author: String? = null,
)

