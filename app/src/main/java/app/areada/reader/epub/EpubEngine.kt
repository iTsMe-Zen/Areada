package app.areada.reader.epub

import android.content.Context
import android.net.Uri
import android.util.Log
import app.areada.data.AreadaCacheManager
import app.areada.data.reader.ReaderPreferences
import app.areada.data.reader.ReaderRenderPalette
import app.areada.data.reader.renderPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document as JsoupHtmlDocument
import org.jsoup.nodes.Document.OutputSettings.Syntax
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

private const val EpubLogTag = "AreadaEpub"
private const val EpubReadyMarkerName = ".areada_epub_ready_v2"
private const val EpubArchiveFileName = "book.epub"
private val CssUrlRegex = Regex("""url\(\s*(['"]?)(.*?)\1\s*\)""", RegexOption.IGNORE_CASE)
private val CssImportRegex = Regex("""@import\s+(?:url\(\s*)?['"](.*?)['"](?:\s*\))?\s*;?""", RegexOption.IGNORE_CASE)

data class EpubBook(
    val title: String,
    val extractedRoot: File,
    val chapters: List<EpubChapter>,
    val archiveFile: File? = null,
)

data class EpubChapter(
    val title: String,
    val file: File,
    val archivePath: String = "",
)

data class RenderedChapter(
    val title: String,
    val baseUrl: String,
    val html: String,
)

object EpubEngine {
    suspend fun parse(context: Context, uri: Uri, fallbackTitle: String): EpubBook =
        withContext(Dispatchers.Default) {
            parseBlocking(context, uri, fallbackTitle)
        }

    suspend fun render(
        book: EpubBook,
        chapterIndex: Int,
        preferences: ReaderPreferences,
        paletteOverride: ReaderRenderPalette? = null,
        scrollToEnd: Boolean = false,
    ): RenderedChapter =
        withContext(Dispatchers.Default) {
            runCatching {
                renderBlocking(book, chapterIndex, preferences, paletteOverride, scrollToEnd)
            }.onFailure { throwable ->
                logRenderFailure(book, chapterIndex, throwable)
            }.getOrThrow()
        }

    fun isCacheUsable(book: EpubBook): Boolean =
        book.extractedRoot.isDirectory &&
            File(book.extractedRoot, EpubReadyMarkerName).isFile &&
            book.archiveFile?.isFile == true &&
            book.chapters.isNotEmpty()

    private fun getSourceLastModified(context: Context, uri: Uri): Long {
        return try {
            if (uri.scheme == "file") {
                File(uri.path ?: "").lastModified()
            } else {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (idx >= 0 && cursor.moveToFirst()) {
                        val v = cursor.getLong(idx)
                        if (v > 0) v else 0L
                    } else 0L
                } ?: 0L
            }
        } catch (_: Exception) { 0L }
    }

    private fun parseBlocking(context: Context, uri: Uri, fallbackTitle: String): EpubBook {
        val root = File(
            context.cacheDir,
            "areada/epub/${uri.toString().hashCode().toUInt().toString(16)}",
        )
        return AreadaCacheManager.withCacheLock {
            try {
                val readyMarker = File(root, EpubReadyMarkerName)
                val archiveFile = File(root, EpubArchiveFileName)
                if ((!readyMarker.exists() || !archiveFile.isFile) && root.exists()) {
                    root.deleteRecursively()
                }
                // Invalidate cache if source file was modified (for EPUB creators)
                if (archiveFile.isFile && readyMarker.isFile) {
                    val srcModified = getSourceLastModified(context, uri)
                    if (srcModified > 0 && srcModified > archiveFile.lastModified()) {
                        root.deleteRecursively()
                    }
                }
                if (!archiveFile.isFile) {
                    root.mkdirs()
                    copyArchive(context, uri, archiveFile)
                }

                ZipFile(archiveFile).use { zip ->
                    val containerDocument = readZipXml(zip, "META-INF/container.xml")
                    val rootFile = containerDocument
                        .getElementsByTagNameNS("*", "rootfile")
                        .item(0) as? Element
                        ?: throw IllegalArgumentException("Invalid or unsupported EPUB file.")

                    val packagePath = rootFile.getAttribute("full-path")
                        .replace('\\', '/')
                        .trimStart('/')
                    val packageDocument = readZipXml(zip, packagePath)

                    val manifest = buildManifest(packageDocument)
                    val packageDirectory = packagePath.substringBeforeLast('/', "")
                    val titleByHref = buildTitleByHref(zip, packageDocument, manifest, packageDirectory)
                    val chapters = buildChapters(root, zip, packageDocument, manifest, packageDirectory, titleByHref)
                    require(chapters.isNotEmpty()) { "This EPUB does not contain readable chapters." }

                    val title = packageDocument
                        .getElementsByTagNameNS("*", "title")
                        .item(0)
                        ?.textContent
                        ?.trim()
                        ?.ifBlank { null }
                        ?: fallbackTitle

                    readyMarker.writeText("ready")
                    val now = System.currentTimeMillis()
                    root.setLastModified(now)
                    archiveFile.setLastModified(now)
                    readyMarker.setLastModified(now)
                    EpubBook(
                        title = title,
                        extractedRoot = root,
                        chapters = chapters,
                        archiveFile = archiveFile,
                    )
                }
            } catch (throwable: Throwable) {
                root.deleteRecursively()
                throw IllegalArgumentException(cleanEpubError(throwable), throwable)
            }
        }
    }

    private fun renderBlocking(
        book: EpubBook,
        chapterIndex: Int,
        preferences: ReaderPreferences,
        paletteOverride: ReaderRenderPalette?,
        scrollToEnd: Boolean = false,
    ): RenderedChapter {
        val chapter = book.chapters.getOrNull(chapterIndex) ?: error("Chapter not found.")
        AreadaCacheManager.withCacheLock {
            ensureChapterExtracted(book, chapter)
        }
        val baseUrl = chapter.file.parentFile?.toURI()?.toString().orEmpty()
        val palette = paletteOverride ?: preferences.themeMode.renderPalette()
        val fontSize = preferences.fontSizeSp.coerceIn(14, 30)
        val lineSpacing = preferences.lineSpacing.coerceIn(1.2f, 2.4f)
        val scrollThumbColor = palette.onSurfaceVariantHex
        val normalizedDocument = parseChapterDocument(chapter.file, baseUrl)
        AreadaCacheManager.withCacheLock {
            ensureReferencedAssets(book, chapter, normalizedDocument)
        }
        val renderedDocument = buildRenderedDocument(
            sourceDocument = normalizedDocument,
            baseUrl = baseUrl,
        )

        renderedDocument.outputSettings()
            .prettyPrint(false)
            .syntax(Syntax.html)
            .escapeMode(Entities.EscapeMode.base)
            .charset(Charsets.UTF_8)

        val head = renderedDocument.head()
        head.prependElement("meta")
            .attr("name", "viewport")
            .attr("content", "width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes")
        head.prependElement("meta")
            .attr("charset", "utf-8")
        head.appendElement("style").appendText(
            """
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
            }
            p, div, span, li, td, th, blockquote, pre, em, strong, i, b, a, font, small, sup, sub, section, article {
              font-family: inherit !important;
              line-height: inherit !important;
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
            img, svg, video, picture, figure {
              display: block;
              max-width: 100% !important;
              height: auto !important;
              margin: 1.25rem auto;
              image-rendering: auto;
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
            /* Keep epub underline/wavy intact for <u> and .book */
            aside {
              display: block;
              margin: 1em 0;
              padding: 12px;
              background: ${palette.surfaceHex};
              border-left: 3px solid ${palette.mutedHex};
            }
            table {
              display: block;
              width: 100% !important;
              overflow-x: auto;
              border-collapse: collapse;
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
            """.trimIndent(),
        )
        if (scrollToEnd) {
            renderedDocument.body().appendElement("script").appendText(
                "window.scrollTo(0,document.body.scrollHeight)",
            )
        }
        val html = renderedDocument.outerHtml()

        return RenderedChapter(
            title = chapter.title,
            baseUrl = chapter.file.parentFile?.toURI()?.toString() ?: book.extractedRoot.toURI().toString(),
            html = html,
        )
    }

    private fun buildRenderedDocument(
        sourceDocument: JsoupHtmlDocument,
        baseUrl: String,
    ): JsoupHtmlDocument {
        sourceDocument.select("script").remove()

        val renderedDocument = Jsoup.parse(
            "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head></head><body></body></html>",
            baseUrl,
            Parser.xmlParser(),
        )
        val sourceHtml = sourceDocument.selectFirst("html")
        val sourceHead = sourceDocument.head()
        val sourceBody = sourceDocument.body()
        val renderedHtml = renderedDocument.selectFirst("html")
        val renderedBody = renderedDocument.body()
        val renderedHead = renderedDocument.head()

        sourceHtml?.attributes()?.forEach { attribute ->
            if (attribute.key.lowercase(Locale.ROOT) != "xmlns") {
                renderedHtml?.attr(attribute.key, attribute.value)
            }
        }
        sourceBody.attributes().forEach { attribute ->
            renderedBody.attr(attribute.key, attribute.value)
        }

        sourceHead.children()
            .filterNot { child ->
                when (child.normalName()) {
                    "meta" -> child.hasAttr("charset") || child.hasAttr("http-equiv")
                    "script" -> true
                    else -> false
                }
            }
            .forEach { child ->
                renderedHead.appendChild(child.clone())
            }

        for (child in sourceBody.childNodesCopy()) {
            renderedBody.appendChild(child.clone())
        }
        return renderedDocument
    }

    private fun parseChapterDocument(
        file: File,
        baseUrl: String,
    ): JsoupHtmlDocument {
        val xmlDocument = runCatching {
            Jsoup.parse(file, null, baseUrl, Parser.xmlParser())
        }.getOrNull()

        if (xmlDocument != null && xmlDocument.hasRenderableBody()) {
            return xmlDocument
        }

        return Jsoup.parse(file, null, baseUrl, Parser.htmlParser())
    }

    private fun JsoupHtmlDocument.hasRenderableBody(): Boolean {
        val body = selectFirst("body") ?: selectFirst("xhtml\\:body") ?: body()
        return body.children().isNotEmpty() || body.text().isNotBlank()
    }

    private fun buildManifest(document: Document): Map<String, ManifestItem> {
        val nodes = document.getElementsByTagNameNS("*", "item")
        return buildMap {
            repeat(nodes.length) { index ->
                val element = nodes.item(index) as? Element ?: return@repeat
                val id = element.getAttribute("id").trim()
                val href = element.getAttribute("href").trim()
                val mediaType = element.getAttribute("media-type").trim()
                val properties = element.getAttribute("properties").trim()
                if (id.isNotEmpty() && href.isNotEmpty()) {
                    put(id, ManifestItem(href, mediaType, properties))
                }
            }
        }
    }

    private fun buildTitleByHref(
        zip: ZipFile,
        document: Document,
        manifest: Map<String, ManifestItem>,
        packageDirectory: String,
    ): Map<String, String> {
        val titles = linkedMapOf<String, String>()

        navManifestItems(manifest).forEach { item ->
            val navPath = normalizeArchivePath(packageDirectory, decodeArchivePath(item.href))
            val navDirectory = navPath.substringBeforeLast('/', "")
            val navText = readZipText(zip, navPath) ?: return@forEach
            val navDocument = runCatching {
                Jsoup.parse(navText, "", Parser.xmlParser())
            }.getOrNull() ?: return@forEach

            tocLinks(navDocument).forEach { link ->
                val title = cleanTocTitle(link.text())
                val archivePath = archiveReferenceFrom(navDirectory, link.attr("href")) ?: return@forEach
                if (title.isNotBlank()) {
                    titles.putIfAbsentCompat(archivePath, title)
                }
            }
        }

        ncxManifestItems(document, manifest).forEach { item ->
            val ncxPath = normalizeArchivePath(packageDirectory, decodeArchivePath(item.href))
            val ncxDirectory = ncxPath.substringBeforeLast('/', "")
            val ncxDocument = runCatching {
                readZipXml(zip, ncxPath)
            }.getOrNull() ?: return@forEach
            val navPoints = ncxDocument.getElementsByTagNameNS("*", "navPoint")
            repeat(navPoints.length) { index ->
                val navPoint = navPoints.item(index) as? Element ?: return@repeat
                val content = navPoint
                    .getElementsByTagNameNS("*", "content")
                    .item(0) as? Element ?: return@repeat
                val archivePath = archiveReferenceFrom(ncxDirectory, content.getAttribute("src")) ?: return@repeat
                val title = cleanTocTitle(
                    navPoint
                        .getElementsByTagNameNS("*", "text")
                        .item(0)
                        ?.textContent
                        .orEmpty(),
                )
                if (title.isNotBlank()) {
                    titles.putIfAbsentCompat(archivePath, title)
                }
            }
        }

        return titles
    }

    private fun tocLinks(document: JsoupHtmlDocument): List<org.jsoup.nodes.Element> {
        val tocNav = document.select("nav").firstOrNull { nav ->
            nav.attributes().any { attribute ->
                val key = attribute.key.lowercase(Locale.ROOT)
                (key == "type" || key == "epub:type" || key.endsWith(":type")) &&
                    attribute.value.contains("toc", ignoreCase = true)
            } || nav.attr("role").contains("doc-toc", ignoreCase = true)
        }
        return (tocNav ?: document).select("a[href]")
    }

    private fun navManifestItems(manifest: Map<String, ManifestItem>): List<ManifestItem> =
        manifest.values.filter { item ->
            item.properties
                .split(' ')
                .any { property -> property.equals("nav", ignoreCase = true) } ||
                item.href.endsWith("nav.xhtml", ignoreCase = true) ||
                item.href.endsWith("toc.xhtml", ignoreCase = true)
        }

    private fun ncxManifestItems(
        document: Document,
        manifest: Map<String, ManifestItem>,
    ): List<ManifestItem> {
        val items = linkedSetOf<ManifestItem>()
        val spine = document.getElementsByTagNameNS("*", "spine").item(0) as? Element
        spine?.getAttribute("toc")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { tocId -> manifest[tocId] }
            ?.let { item -> items += item }

        manifest.values
            .filter { item ->
                item.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) ||
                    item.href.endsWith(".ncx", ignoreCase = true)
            }
            .forEach { item -> items += item }

        return items.toList()
    }

    private fun buildChapters(
        root: File,
        zip: ZipFile,
        document: Document,
        manifest: Map<String, ManifestItem>,
        packageDirectory: String,
        titleByHref: Map<String, String>,
    ): List<EpubChapter> {
        val nodes = document.getElementsByTagNameNS("*", "itemref")
        return buildList {
            repeat(nodes.length) { index ->
                val element = nodes.item(index) as? Element ?: return@repeat
                val idRef = element.getAttribute("idref").trim()
                val manifestItem = manifest[idRef] ?: return@repeat

                if (!isReadableChapter(manifestItem)) {
                    return@repeat
                }

                val archivePath = normalizeArchivePath(packageDirectory, decodeArchivePath(manifestItem.href))
                if (findZipEntry(zip, archivePath) == null) {
                    return@repeat
                }

                add(
                    EpubChapter(
                        title = titleByHref[archivePath] ?: prettyChapterTitle(manifestItem.href, index),
                        file = resolveInside(root, archivePath),
                        archivePath = archivePath,
                    ),
                )
            }
        }
    }

    private fun copyArchive(context: Context, uri: Uri, archiveFile: File) {
        archiveFile.parentFile?.mkdirs()
        val tempFile = File(archiveFile.parentFile, "${archiveFile.name}.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE * 4)
            }
        } ?: throw IllegalArgumentException("Unable to read that EPUB.")

        if (archiveFile.exists()) {
            archiveFile.delete()
        }
        if (!tempFile.renameTo(archiveFile)) {
            tempFile.copyTo(archiveFile, overwrite = true)
            tempFile.delete()
        }
    }

    private fun readZipXml(zip: ZipFile, archivePath: String): Document {
        val normalizedPath = normalizeArchivePath("", archivePath)
        val entry = findZipEntry(zip, normalizedPath)
            ?: throw IllegalArgumentException("Invalid or unsupported EPUB file.")
        return zip.getInputStream(entry).use { input ->
            parseXml(input)
        }
    }

    private fun readZipText(zip: ZipFile, archivePath: String): String? {
        val normalizedPath = normalizeArchivePath("", archivePath)
        val entry = findZipEntry(zip, normalizedPath) ?: return null
        return zip.getInputStream(entry).use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    private fun ensureChapterExtracted(book: EpubBook, chapter: EpubChapter) {
        if (chapter.file.isFile) {
            return
        }
        val archiveFile = book.archiveFile ?: return
        val archivePath = chapter.archivePath.ifBlank {
            chapter.file.relativeToOrNull(book.extractedRoot)?.invariantSeparatorsPath.orEmpty()
        }
        if (archivePath.isBlank()) {
            return
        }

        ZipFile(archiveFile).use { zip ->
            extractEntry(zip, book.extractedRoot, archivePath)
                ?: throw IllegalArgumentException("Invalid or unsupported EPUB file.")
        }
    }

    private fun ensureReferencedAssets(
        book: EpubBook,
        chapter: EpubChapter,
        document: JsoupHtmlDocument,
    ) {
        val archiveFile = book.archiveFile ?: return
        val chapterDirectory = chapter.archivePath.substringBeforeLast('/', "")
        val archivePaths = linkedSetOf<String>()
        val cssPaths = linkedSetOf<String>()

        document.getAllElements().forEach { element ->
            listOf("src", "href", "xlink:href", "data", "poster", "background").forEach { attribute ->
                val rawReference = element.attr(attribute)
                val archivePath = archiveReferenceFrom(chapterDirectory, rawReference) ?: return@forEach
                archivePaths += archivePath
                if (isCssReference(rawReference, archivePath)) {
                    cssPaths += archivePath
                }
            }
        }
        document.select("[srcset]").forEach { element ->
            element.attr("srcset")
                .split(',')
                .map { candidate -> candidate.trim().substringBefore(' ').trim() }
                .forEach { candidate ->
                    archiveReferenceFrom(chapterDirectory, candidate)?.let { archivePaths += it }
                }
        }
        document.select("style").forEach { styleElement ->
            cssArchiveReferences(chapterDirectory, styleElement.data())
                .forEach { archivePath -> archivePaths += archivePath }
        }
        document.select("[style]").forEach { element ->
            cssArchiveReferences(chapterDirectory, element.attr("style"))
                .forEach { archivePath -> archivePaths += archivePath }
        }

        if (archivePaths.isEmpty()) {
            return
        }

        ZipFile(archiveFile).use { zip ->
            archivePaths.forEach { archivePath ->
                extractEntry(zip, book.extractedRoot, archivePath)
            }
            cssPaths.forEach { cssPath ->
                val cssDirectory = cssPath.substringBeforeLast('/', "")
                readZipText(zip, cssPath)
                    ?.let { css -> cssArchiveReferences(cssDirectory, css) }
                    ?.forEach { archivePath -> extractEntry(zip, book.extractedRoot, archivePath) }
            }
        }
    }

    private fun isCssReference(rawReference: String, archivePath: String): Boolean {
        val lowerReference = rawReference.substringBefore('#').substringBefore('?').lowercase(Locale.ROOT)
        return lowerReference.endsWith(".css") || archivePath.lowercase(Locale.ROOT).endsWith(".css")
    }

    private fun cssArchiveReferences(
        basePath: String,
        css: String,
    ): List<String> =
        buildList {
            CssUrlRegex.findAll(css).forEach { match ->
                match.groupValues.getOrNull(2)?.let { reference ->
                    archiveReferenceFrom(basePath, reference)?.let { add(it) }
                }
            }
            CssImportRegex.findAll(css).forEach { match ->
                match.groupValues.getOrNull(1)?.let { reference ->
                    archiveReferenceFrom(basePath, reference)?.let { add(it) }
                }
            }
        }.distinct()

    private fun logRenderFailure(
        book: EpubBook,
        chapterIndex: Int,
        throwable: Throwable,
    ) {
        val chapter = book.chapters.getOrNull(chapterIndex)
        Log.e(
            EpubLogTag,
            "Unable to render EPUB section index=$chapterIndex total=${book.chapters.size} " +
                "archivePath=${chapter?.archivePath.orEmpty()} file=${chapter?.file?.path.orEmpty()} " +
                "archiveExists=${book.archiveFile?.isFile == true} root=${book.extractedRoot.path}: ${throwable.message}",
            throwable,
        )
    }

    private fun extractEntry(zip: ZipFile, root: File, archivePath: String): File? {
        val normalizedPath = normalizeArchivePath("", archivePath)
        if (normalizedPath.isBlank()) {
            return null
        }
        val target = resolveInside(root, normalizedPath)
        val entry = findZipEntry(zip, normalizedPath) ?: return null
        if (target.isDirectory && entry.isDirectory) {
            return target
        }

        if (target.isFile && !entry.isDirectory) {
            val expectedSize = entry.size
            val existingSize = target.length()
            if (existingSize > 0L && (expectedSize < 0L || existingSize == expectedSize)) {
                return target
            }
        }

        if (entry.isDirectory) {
            target.mkdirs()
            return target
        }

        target.parentFile?.mkdirs()
        val tempFile = File(target.parentFile, "${target.name}.tmp")
        zip.getInputStream(entry).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE * 4)
            }
        }
        if (target.exists()) {
            target.delete()
        }
        if (!tempFile.renameTo(target)) {
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
        }
        return target
    }

    private fun findZipEntry(zip: ZipFile, archivePath: String): ZipEntry? {
        val normalizedPath = normalizeArchivePath("", archivePath)
        if (normalizedPath.isBlank()) {
            return null
        }
        zip.getEntry(normalizedPath)?.let { entry -> return entry }
        val lowerPath = normalizedPath.lowercase(Locale.ROOT)
        return zip.entries().asSequence().firstOrNull { entry ->
            entry.name.replace('\\', '/').lowercase(Locale.ROOT) == lowerPath
        }
    }

    private fun archiveReferenceFrom(basePath: String, rawReference: String): String? {
        val reference = rawReference
            .trim()
            .substringBefore('#')
            .substringBefore('?')
            .trim()
        if (reference.isBlank() || isExternalReference(reference)) {
            return null
        }

        return normalizeArchivePath(basePath, decodeArchivePath(reference))
            .takeIf { it.isNotBlank() }
    }

    private fun decodeArchivePath(reference: String): String =
        runCatching { URI(reference).path ?: reference }.getOrDefault(reference)

    private fun isExternalReference(reference: String): Boolean {
        val lower = reference.lowercase(Locale.ROOT)
        return lower.startsWith("//") ||
            lower.startsWith("data:") ||
            lower.startsWith("http:") ||
            lower.startsWith("https:") ||
            lower.startsWith("mailto:") ||
            lower.startsWith("tel:") ||
            lower.startsWith("javascript:") ||
            lower.startsWith("file:") ||
            lower.startsWith("android.resource:")
    }

    private fun parseXml(file: File): Document =
        file.inputStream().use { input -> parseXml(input) }

    private fun parseXml(inputStream: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return factory.newDocumentBuilder().parse(inputStream)
    }

    private fun resolveInside(root: File, archivePath: String): File {
        val rootFile = root.canonicalFile
        val candidate = File(rootFile, archivePath.replace('/', File.separatorChar)).canonicalFile
        val rootPath = rootFile.path
        val candidatePath = candidate.path
        require(candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)) {
            "Unsafe EPUB path detected."
        }
        return candidate
    }

    private fun <K, V> MutableMap<K, V>.putIfAbsentCompat(key: K, value: V) {
        if (!containsKey(key)) {
            put(key, value)
        }
    }

    private fun normalizeArchivePath(basePath: String, relativePath: String): String {
        val segments = mutableListOf<String>()
        val normalizedRelative = relativePath.replace('\\', '/')
        val combined = if (normalizedRelative.startsWith("/")) {
            normalizedRelative.trimStart('/')
        } else {
            listOf(basePath, normalizedRelative)
                .filter { it.isNotBlank() }
                .joinToString("/")
        }.replace('\\', '/')

        combined.split("/").forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) {
                    segments.removeAt(segments.lastIndex)
                }
                else -> segments += part
            }
        }

        return segments.joinToString("/")
    }

    private fun isReadableChapter(item: ManifestItem): Boolean {
        val lowerHref = item.href.lowercase(Locale.ROOT)
        return item.mediaType.contains("html", ignoreCase = true) ||
            lowerHref.endsWith(".xhtml") ||
            lowerHref.endsWith(".html") ||
            lowerHref.endsWith(".htm")
    }

    private fun cleanEpubError(throwable: Throwable): String {
        val message = throwable.message.orEmpty()
        return when {
            message.contains("readable chapters", ignoreCase = true) -> "This EPUB does not contain readable chapters."
            message.contains("Unable to read", ignoreCase = true) -> "Unable to read that EPUB."
            else -> "Invalid or unsupported EPUB file."
        }
    }

    private fun prettyChapterTitle(href: String, index: Int): String {
        val stem = href
            .substringAfterLast('/')
            .substringBeforeLast('.', "")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()

        if (stem.isBlank() || stem.equals("index", ignoreCase = true)) {
            return "Section ${index + 1}"
        }

        return stem.split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { char ->
                    if (char.isLowerCase()) {
                        char.titlecase(Locale.ROOT)
                    } else {
                        char.toString()
                    }
                }
            }
    }

    private fun cleanTocTitle(rawTitle: String): String =
        rawTitle
            .replace(Regex("\\s+"), " ")
            .trim()

    private data class ManifestItem(
        val href: String,
        val mediaType: String,
        val properties: String,
    )
}

