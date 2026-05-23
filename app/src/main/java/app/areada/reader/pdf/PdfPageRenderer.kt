package app.areada.reader.pdf

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.graphics.pdf.RenderParams
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val PdfRenderScale = 1.5f
private const val PdfMaxRenderWidthPx = 3200
private const val PdfMaxBitmapPixels = 8_500_000
private const val PdfRendererLogTag = "AreadaPdf"
private const val PdfTextAnnotationFlag = 0x2
private const val PdfHighlightAnnotationFlag = 0x4
private const val PdfStampAnnotationFlag = 0x8
private const val PdfFreeTextAnnotationFlag = 0x10
private const val PdfFormContentEnabled = 0x1
private const val PdfExtensionRenderParamsVersion = 13
private const val PdfExtensionFreeTextVersion = 18
private const val PdfExtensionFormContentVersion = 19
private const val PdfLinkFallbackMaxBytes = 32 * 1024 * 1024
private const val PdfNumberPattern = """[-+]?(?:\d+\.?\d*|\.\d+)"""
private val PdfObjectRegex = Regex("""(\d+)\s+\d+\s+obj(.*?)endobj""", RegexOption.DOT_MATCHES_ALL)
private val PdfCatalogTypeRegex = Regex("""/Type\s*/Catalog\b""")
private val PdfPageTypeRegex = Regex("""/Type\s*/Page\b""")
private val PdfPagesTypeRegex = Regex("""/Type\s*/Pages\b""")
private val PdfPagesReferenceRegex = Regex("""/Pages\s+(\d+)\s+\d+\s+R""")
private val PdfKidsRegex = Regex("""/Kids\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
private val PdfLinkSubtypeRegex = Regex("""/Subtype\s*/Link\b""")
private val PdfMediaBoxRegex = Regex("""/MediaBox\s*\[\s*($PdfNumberPattern)\s+($PdfNumberPattern)\s+($PdfNumberPattern)\s+($PdfNumberPattern)\s*]""")
private val PdfAnnotsRegex = Regex("""/Annots\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
private val PdfObjectReferenceRegex = Regex("""(\d+)\s+\d+\s+R""")
private val PdfRectRegex = Regex("""/Rect\s*\[\s*($PdfNumberPattern)\s+($PdfNumberPattern)\s+($PdfNumberPattern)\s+($PdfNumberPattern)\s*]""")
private val PdfDirectDestinationRegex = Regex("""/Dest\s*\[\s*(\d+)\s+\d+\s+R\s*/([A-Za-z]+)\s*([^]]*)]""", RegexOption.DOT_MATCHES_ALL)
private val PdfDestinationValueRegex = Regex("""null|$PdfNumberPattern""", RegexOption.IGNORE_CASE)

data class PdfLinkTarget(
    val bounds: List<RectF>,
    val uri: Uri? = null,
    val pageIndex: Int? = null,
    val destinationX: Float? = null,
    val destinationY: Float? = null,
    val destinationZoom: Float? = null,
)

data class PdfLinkLayer(
    val pageWidth: Int,
    val pageHeight: Int,
    val links: List<PdfLinkTarget>,
)

class PdfPageRenderer(
    context: Context,
    uri: Uri,
) : Closeable {
    private val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: error("Unable to read that PDF.")
    private val backend: PdfRenderBackend = createPdfRenderBackend(fileDescriptor)
    private val fallbackLinkLayers by lazy {
        parseDirectDestinationLinks(context, uri)
    }
    private var closed = false

    val pageCount: Int
        get() = synchronized(this) {
            if (closed) 0 else backend.pageCount
        }

    fun renderPage(
        pageIndex: Int,
        widthPx: Int,
        renderScale: Float = 1f,
    ): Bitmap {
        synchronized(this) {
            check(!closed) { "PDF renderer is closed." }
            return backend.renderPage(pageIndex, widthPx, renderScale)
        }
    }

    fun loadLinkLayer(pageIndex: Int): PdfLinkLayer? {
        synchronized(this) {
            check(!closed) { "PDF renderer is closed." }
            val nativeLayer = backend.loadLinkLayer(pageIndex)
            val fallbackLinks = fallbackLinkLayers[pageIndex].orEmpty()
            if (nativeLayer == null && fallbackLinks.isEmpty()) {
                return null
            }
            val pageSize = nativeLayer?.let { PdfPageSize(it.pageWidth, it.pageHeight) }
                ?: backend.pageSize(pageIndex)
            val nativeLinks = nativeLayer?.links.orEmpty()
            val externalLinks = nativeLinks.filter { link -> link.uri != null }
            val nativeGotoLinks = nativeLinks.filter { link -> link.uri == null }
            return PdfLinkLayer(
                pageWidth = pageSize.width,
                pageHeight = pageSize.height,
                // Direct /Dest links are used as a fallback before native goto links because
                // some platform builds expose direct destinations inconsistently.
                links = externalLinks + fallbackLinks + nativeGotoLinks,
            )
        }
    }

    override fun close() {
        synchronized(this) {
            if (closed) {
                return
            }
            closed = true
            runCatching { backend.close() }
            runCatching { fileDescriptor.close() }
        }
    }
}

private interface PdfRenderBackend : Closeable {
    val pageCount: Int
    fun renderPage(pageIndex: Int, widthPx: Int, renderScale: Float): Bitmap
    fun loadLinkLayer(pageIndex: Int): PdfLinkLayer?
    fun pageSize(pageIndex: Int): PdfPageSize
}

private data class PdfPageSize(
    val width: Int,
    val height: Int,
)

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private class ModernPdfRenderBackend(
    private val renderer: PdfRenderer,
) : PdfRenderBackend {
    private val renderParams by lazy { createPdfRenderParams() }

    override val pageCount: Int
        get() = renderer.pageCount

    override fun renderPage(pageIndex: Int, widthPx: Int, renderScale: Float): Bitmap {
        require(pageIndex in 0 until renderer.pageCount) { "Page out of range." }
        val page = renderer.openPage(pageIndex)
        try {
            val target = createPdfBitmapTarget(page.width, page.height, widthPx, renderScale)
            page.render(target.bitmap, target.destination, null, renderParams)
            return target.bitmap
        } finally {
            page.close()
        }
    }

    override fun loadLinkLayer(pageIndex: Int): PdfLinkLayer? {
        require(pageIndex in 0 until renderer.pageCount) { "Page out of range." }
        val page = renderer.openPage(pageIndex)
        return try {
            page.toPdfLinkLayer()
        } catch (error: Throwable) {
            Log.w(PdfRendererLogTag, "Unable to load PDF links from platform renderer.", error)
            null
        } finally {
            page.close()
        }
    }

    override fun pageSize(pageIndex: Int): PdfPageSize {
        require(pageIndex in 0 until renderer.pageCount) { "Page out of range." }
        val page = renderer.openPage(pageIndex)
        return try {
            PdfPageSize(page.width, page.height)
        } finally {
            page.close()
        }
    }

    override fun close() {
        renderer.close()
    }
}

@RequiresExtension(extension = Build.VERSION_CODES.S, version = PdfExtensionRenderParamsVersion)
private class ExtensionPdfRenderBackend(
    private val renderer: PdfRendererPreV,
) : PdfRenderBackend {
    private val renderParams by lazy { createPdfRenderParams() }

    override val pageCount: Int
        get() = renderer.pageCount

    override fun renderPage(pageIndex: Int, widthPx: Int, renderScale: Float): Bitmap {
        require(pageIndex in 0 until renderer.pageCount) { "Page out of range." }
        val page = renderer.openPage(pageIndex)
        try {
            val target = createPdfBitmapTarget(page.width, page.height, widthPx, renderScale)
            page.render(target.bitmap, target.destination, null, renderParams)
            return target.bitmap
        } finally {
            page.close()
        }
    }

    override fun loadLinkLayer(pageIndex: Int): PdfLinkLayer? {
        require(pageIndex in 0 until renderer.pageCount) { "Page out of range." }
        val page = renderer.openPage(pageIndex)
        return try {
            page.toPdfLinkLayer()
        } catch (error: Throwable) {
            Log.w(PdfRendererLogTag, "Unable to load PDF links from extension renderer.", error)
            null
        } finally {
            page.close()
        }
    }

    override fun pageSize(pageIndex: Int): PdfPageSize {
        require(pageIndex in 0 until renderer.pageCount) { "Page out of range." }
        val page = renderer.openPage(pageIndex)
        return try {
            PdfPageSize(page.width, page.height)
        } finally {
            page.close()
        }
    }

    override fun close() {
        renderer.close()
    }
}

private class LegacyPdfRenderBackend(
    private val renderer: PdfRenderer,
) : PdfRenderBackend {
    override val pageCount: Int
        get() = renderer.pageCount

    override fun renderPage(pageIndex: Int, widthPx: Int, renderScale: Float): Bitmap {
        require(pageIndex in 0 until renderer.pageCount) { "Page out of range." }
        val page = renderer.openPage(pageIndex)
        try {
            val target = createPdfBitmapTarget(page.width, page.height, widthPx, renderScale)
            page.render(
                target.bitmap,
                target.destination,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_PRINT,
            )
            return target.bitmap
        } finally {
            page.close()
        }
    }

    override fun loadLinkLayer(pageIndex: Int): PdfLinkLayer? = null

    override fun pageSize(pageIndex: Int): PdfPageSize {
        require(pageIndex in 0 until renderer.pageCount) { "Page out of range." }
        val page = renderer.openPage(pageIndex)
        return try {
            PdfPageSize(page.width, page.height)
        } finally {
            page.close()
        }
    }

    override fun close() {
        renderer.close()
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private fun PdfRenderer.Page.toPdfLinkLayer(): PdfLinkLayer {
    val externalLinks = runCatching {
        getLinkContents().mapNotNull { link ->
            val bounds = link.bounds.orEmpty()
            val uri = link.uri
            if (bounds.isEmpty()) {
                null
            } else {
                PdfLinkTarget(bounds = bounds, uri = uri)
            }
        }
    }.getOrDefault(emptyList())
    val gotoLinks = runCatching {
        getGotoLinks().mapNotNull { link ->
            val bounds = link.bounds.orEmpty()
            val destination = link.destination
            if (bounds.isEmpty()) {
                null
            } else {
                PdfLinkTarget(
                    bounds = bounds,
                    pageIndex = destination.pageNumber,
                    destinationX = destination.xCoordinate,
                    destinationY = destination.yCoordinate,
                    destinationZoom = destination.zoom,
                )
            }
        }
    }.getOrDefault(emptyList())
    return PdfLinkLayer(
        pageWidth = width,
        pageHeight = height,
        links = externalLinks + gotoLinks,
    )
}

@RequiresExtension(extension = Build.VERSION_CODES.S, version = PdfExtensionRenderParamsVersion)
private fun PdfRendererPreV.Page.toPdfLinkLayer(): PdfLinkLayer {
    val externalLinks = runCatching {
        getLinkContents().mapNotNull { link ->
            val bounds = link.bounds.orEmpty()
            val uri = link.uri
            if (bounds.isEmpty()) {
                null
            } else {
                PdfLinkTarget(bounds = bounds, uri = uri)
            }
        }
    }.getOrDefault(emptyList())
    val gotoLinks = runCatching {
        getGotoLinks().mapNotNull { link ->
            val bounds = link.bounds.orEmpty()
            val destination = link.destination
            if (bounds.isEmpty()) {
                null
            } else {
                PdfLinkTarget(
                    bounds = bounds,
                    pageIndex = destination.pageNumber,
                    destinationX = destination.xCoordinate,
                    destinationY = destination.yCoordinate,
                    destinationZoom = destination.zoom,
                )
            }
        }
    }.getOrDefault(emptyList())
    return PdfLinkLayer(
        pageWidth = width,
        pageHeight = height,
        links = externalLinks + gotoLinks,
    )
}

private data class PdfObjectSource(
    val id: Int,
    val body: String,
)

private data class PdfFallbackPage(
    val objectId: Int,
    val width: Int,
    val height: Int,
    val annotationIds: List<Int>,
)

private fun parseDirectDestinationLinks(
    context: Context,
    uri: Uri,
): Map<Int, List<PdfLinkTarget>> {
    val pdfText = readPdfTextForLinkParsing(context, uri) ?: return emptyMap()
    val objects = PdfObjectRegex.findAll(pdfText)
        .mapNotNull { match ->
            val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            PdfObjectSource(id = id, body = match.groupValues[2])
        }
        .toList()
    if (objects.isEmpty()) {
        return emptyMap()
    }

    val objectsById = objects.associateBy { source -> source.id }
    val pageTreeObjectIds = pdfPageTreeOrder(objectsById)
    val unorderedPageObjects = objects
        .filter { source -> source.body.isPdfPageObject() }
        .mapNotNull { source -> source.toFallbackPage() }
    val pageObjects = pageTreeObjectIds
        .mapNotNull { objectId -> objectsById[objectId]?.takeIf { source -> source.body.isPdfPageObject() } }
        .mapNotNull { source -> source.toFallbackPage() }
        .ifEmpty { unorderedPageObjects }
    if (pageObjects.isEmpty()) {
        return emptyMap()
    }

    val pageIndexByObjectId = pageObjects
        .mapIndexed { index, page -> page.objectId to index }
        .toMap()
    val linkAnnotations = objects
        .filter { source -> PdfLinkSubtypeRegex.containsMatchIn(source.body) }
        .associateBy { source -> source.id }

    val linksByPage = linkedMapOf<Int, MutableList<PdfLinkTarget>>()
    pageObjects.forEachIndexed { sourcePageIndex, page ->
        page.annotationIds.forEach { annotationId ->
            val annotation = linkAnnotations[annotationId]?.body ?: return@forEach
            val link = annotation.toDirectDestinationLink(pageIndexByObjectId) ?: return@forEach
            linksByPage.getOrPut(sourcePageIndex) { mutableListOf() } += link
        }
    }
    return linksByPage
}

private fun readPdfTextForLinkParsing(
    context: Context,
    uri: Uri,
): String? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                total += read
                if (total > PdfLinkFallbackMaxBytes) {
                    return null
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray().toString(StandardCharsets.ISO_8859_1)
        }
    }.getOrNull()
}

private fun String.isPdfPageObject(): Boolean =
    PdfPageTypeRegex.containsMatchIn(this) && !PdfPagesTypeRegex.containsMatchIn(this)

private fun pdfPageTreeOrder(
    objectsById: Map<Int, PdfObjectSource>,
): List<Int> {
    val catalog = objectsById.values.firstOrNull { source ->
        PdfCatalogTypeRegex.containsMatchIn(source.body)
    } ?: return emptyList()
    val rootPagesId = PdfPagesReferenceRegex.find(catalog.body)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: return emptyList()
    val visited = mutableSetOf<Int>()

    fun collectPageIds(objectId: Int): List<Int> {
        if (!visited.add(objectId)) {
            return emptyList()
        }
        val source = objectsById[objectId] ?: return emptyList()
        if (source.body.isPdfPageObject()) {
            return listOf(objectId)
        }
        val kids = PdfKidsRegex.find(source.body)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptyList()
        return PdfObjectReferenceRegex.findAll(kids)
            .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
            .flatMap { childId -> collectPageIds(childId) }
            .toList()
    }

    return collectPageIds(rootPagesId)
}

private fun PdfObjectSource.toFallbackPage(): PdfFallbackPage? {
    val mediaBox = PdfMediaBoxRegex.find(body)
    val width = mediaBox?.let { match ->
        val left = match.groupValues[1].toFloatOrNull() ?: 0f
        val right = match.groupValues[3].toFloatOrNull() ?: 0f
        (right - left).roundToInt().coerceAtLeast(1)
    } ?: 1
    val height = mediaBox?.let { match ->
        val bottom = match.groupValues[2].toFloatOrNull() ?: 0f
        val top = match.groupValues[4].toFloatOrNull() ?: 0f
        (top - bottom).roundToInt().coerceAtLeast(1)
    } ?: 1
    val annotationIds = PdfAnnotsRegex.find(body)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { annots ->
            PdfObjectReferenceRegex.findAll(annots)
                .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
                .toList()
        }
        .orEmpty()
    return PdfFallbackPage(
        objectId = id,
        width = width,
        height = height,
        annotationIds = annotationIds,
    )
}

private fun String.toDirectDestinationLink(
    pageIndexByObjectId: Map<Int, Int>,
): PdfLinkTarget? {
    val rect = PdfRectRegex.find(this)?.toRectF() ?: return null
    val destination = PdfDirectDestinationRegex.find(this) ?: return null
    val destinationPageObjectId = destination.groupValues[1].toIntOrNull() ?: return null
    val pageIndex = pageIndexByObjectId[destinationPageObjectId] ?: return null
    val destinationType = destination.groupValues[2]
    val destinationValues = PdfDestinationValueRegex.findAll(destination.groupValues[3])
        .map { match -> match.value }
        .toList()
    val destinationX = if (destinationType == "XYZ") destinationValues.getOrNull(0)?.pdfFloatOrNull() else null
    val destinationY = if (destinationType == "XYZ") destinationValues.getOrNull(1)?.pdfFloatOrNull() else null
    val destinationZoom = if (destinationType == "XYZ") destinationValues.getOrNull(2)?.pdfFloatOrNull() else null
    return PdfLinkTarget(
        bounds = listOf(rect),
        pageIndex = pageIndex,
        destinationX = destinationX,
        destinationY = destinationY,
        destinationZoom = destinationZoom,
    )
}

private fun MatchResult.toRectF(): RectF? {
    val left = groupValues[1].toFloatOrNull() ?: return null
    val bottom = groupValues[2].toFloatOrNull() ?: return null
    val right = groupValues[3].toFloatOrNull() ?: return null
    val top = groupValues[4].toFloatOrNull() ?: return null
    return RectF(left, bottom, right, top)
}

private fun String.pdfFloatOrNull(): Float? =
    takeUnless { value -> value.equals("null", ignoreCase = true) }?.toFloatOrNull()

private data class PdfBitmapTarget(
    val bitmap: Bitmap,
    val destination: Rect,
)

@SuppressLint("NewApi")
private fun createPdfRenderBackend(
    fileDescriptor: android.os.ParcelFileDescriptor,
): PdfRenderBackend =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> {
            ModernPdfRenderBackend(PdfRenderer(fileDescriptor))
        }
        supportsPdfRendererPreV() -> {
            ExtensionPdfRenderBackend(PdfRendererPreV(fileDescriptor))
        }
        else -> {
            LegacyPdfRenderBackend(PdfRenderer(fileDescriptor))
        }
    }

private fun createPdfBitmapTarget(
    pageWidth: Int,
    pageHeight: Int,
    widthPx: Int,
    renderScale: Float,
): PdfBitmapTarget {
    val safeWidth = max(widthPx, 1)
    val aspectRatio = pageHeight.toFloat() / pageWidth.toFloat()
    val targetWidth = (safeWidth * PdfRenderScale * renderScale.coerceIn(1f, 3f))
        .roundToInt()
        .coerceAtLeast(safeWidth)
        .coerceAtMost(PdfMaxRenderWidthPx)
    val pixelCappedWidth = sqrt(PdfMaxBitmapPixels / aspectRatio)
        .roundToInt()
        .coerceAtLeast(1)
    val renderWidth = targetWidth.coerceAtMost(pixelCappedWidth)
    val heightPx = max((renderWidth * aspectRatio).roundToInt(), 1)
    val bitmap = Bitmap.createBitmap(renderWidth, heightPx, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)
    return PdfBitmapTarget(
        bitmap = bitmap,
        destination = Rect(0, 0, renderWidth, heightPx),
    )
}

@SuppressLint("NewApi")
private fun createPdfRenderParams(): RenderParams {
    val flags = supportedAnnotationRenderFlags()
    val builder = RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY)
    runCatching {
        builder.setRenderFlags(flags, flags)
    }.getOrElse { error ->
        val fallbackFlags = RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS or
            RenderParams.FLAG_RENDER_HIGHLIGHT_ANNOTATIONS
        Log.w(PdfRendererLogTag, "Full annotation flags unavailable; falling back to text/highlight.", error)
        runCatching {
            builder.setRenderFlags(fallbackFlags, fallbackFlags)
        }.getOrElse { fallbackError ->
            Log.w(PdfRendererLogTag, "Annotation render flags unavailable; rendering page content only.", fallbackError)
        }
    }
    enablePdfFormContentIfAvailable(builder)
    return builder.build()
}

private fun supportedAnnotationRenderFlags(): Int {
    var flags = PdfTextAnnotationFlag or PdfHighlightAnnotationFlag
    if (pdfExtensionVersion() >= PdfExtensionFreeTextVersion || Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        flags = flags or PdfStampAnnotationFlag or PdfFreeTextAnnotationFlag
    }
    return flags
}

@SuppressLint("NewApi")
private fun enablePdfFormContentIfAvailable(builder: RenderParams.Builder): Boolean {
    if (pdfExtensionVersion() < PdfExtensionFormContentVersion && Build.VERSION.SDK_INT <= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        return false
    }
    return runCatching {
        builder.javaClass
            .getMethod("setRenderFormContentMode", Int::class.javaPrimitiveType)
            .invoke(builder, PdfFormContentEnabled)
        true
    }.getOrElse {
        false
    }
}

@ChecksSdkIntAtLeast(extension = Build.VERSION_CODES.S, api = PdfExtensionRenderParamsVersion)
private fun supportsPdfRendererPreV(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM &&
        pdfExtensionVersion() >= PdfExtensionRenderParamsVersion

private fun pdfExtensionVersion(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S)
    } else {
        0
    }

