package app.areada.data

import app.areada.data.reader.DocumentType
import app.areada.data.reader.ReaderDocument
import android.content.Context
import android.net.Uri
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.zip.ZipInputStream

private const val ZipEntryUriSeparator = "#areadaZipEntry="

data class ZipBookEntry(
    val archiveUriString: String,
    val entryName: String,
    val displayName: String,
    val type: DocumentType,
    val isArchiveEntry: Boolean = false,
) {
    val uriString: String = if (isArchiveEntry) {
        archiveBookUriString(archiveUriString, entryName)
    } else {
        zipBookUriString(archiveUriString, entryName)
    }
    val title: String = displayName
        .substringAfterLast('/')
        .substringBeforeLast('.', displayName.substringAfterLast('/'))
        .ifBlank { displayName.substringAfterLast('/') }
}

data class ZipBookReference(
    val archiveUriString: String,
    val entryName: String,
)

fun zipBookUriString(
    archiveUriString: String,
    entryName: String,
): String = archiveUriString + ZipEntryUriSeparator + URLEncoder.encode(entryName, Charsets.UTF_8.name())

fun parseZipBookUriString(uriString: String): ZipBookReference? {
    val separatorIndex = uriString.lastIndexOf(ZipEntryUriSeparator)
    if (separatorIndex < 0) {
        return null
    }
    val archiveUriString = uriString.substring(0, separatorIndex)
    val entryName = URLDecoder.decode(
        uriString.substring(separatorIndex + ZipEntryUriSeparator.length),
        Charsets.UTF_8.name(),
    )
    if (archiveUriString.isBlank() || entryName.isBlank()) {
        return null
    }
    return ZipBookReference(archiveUriString, entryName)
}

object ZipBookContainer {
    fun listSupportedEntries(
        context: Context,
        archiveUri: Uri,
    ): List<ZipBookEntry> {
        val archiveUriString = archiveUri.toString()
        return runCatching {
            val entries = mutableListOf<ZipBookEntry>()
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val entryName = safeZipEntryName(entry.name)
                        if (!entry.isDirectory && entryName != null) {
                            supportedZipEntryType(entryName)?.let { type ->
                                entries += ZipBookEntry(
                                    archiveUriString = archiveUriString,
                                    entryName = entryName,
                                    displayName = entryName,
                                    type = type,
                                )
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: throw IllegalArgumentException("Could not open ZIP file.")
            entries
        }.getOrElse {
            throw IllegalArgumentException("Could not open ZIP file.")
        }
    }

    fun extractEntry(
        context: Context,
        entry: ZipBookEntry,
    ): ReaderDocument {
        val archiveUri = Uri.parse(entry.archiveUriString)
        val cacheDir = File(context.cacheDir, "zip-books").also { directory ->
            directory.mkdirs()
        }
        val extension = entry.displayName
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .takeIf { it in setOf("epub", "pdf", "txt", "fb2") }
            ?: "book"
        val target = File(cacheDir, "${stableFileName(entry.uriString)}.$extension")

        return runCatching {
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val zipEntry = zip.nextEntry ?: break
                        val entryName = safeZipEntryName(zipEntry.name)
                        if (!zipEntry.isDirectory && entryName == entry.entryName) {
                            target.outputStream().use { output ->
                                zip.copyTo(output)
                            }
                            return ReaderDocument(
                                uri = Uri.fromFile(target),
                                uriString = entry.uriString,
                                title = entry.title,
                                type = entry.type,
                            )
                        }
                        zip.closeEntry()
                    }
                }
            } ?: throw IllegalArgumentException("Could not open ZIP file.")
            throw IllegalArgumentException("Could not open selected file from ZIP.")
        }.getOrElse {
            throw IllegalArgumentException("Could not open selected file from ZIP.")
        }
    }
}

fun safeZipEntryName(rawName: String?): String? {
    val normalized = rawName
        ?.replace('\\', '/')
        ?.trim()
        .orEmpty()
    if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains('\u0000')) {
        return null
    }
    val parts = normalized.split('/').filter { part -> part.isNotBlank() }
    if (parts.isEmpty() || parts.any { part -> part == ".." }) {
        return null
    }
    if (parts.first().equals("__MACOSX", ignoreCase = true)) {
        return null
    }
    val fileName = parts.last()
    if (
        fileName.equals(".DS_Store", ignoreCase = true) ||
        fileName.equals("Thumbs.db", ignoreCase = true)
    ) {
        return null
    }
    return parts.joinToString("/")
}

fun supportedZipEntryType(entryName: String): DocumentType? {
    val lowerName = entryName.lowercase(Locale.ROOT)
    return when {
        lowerName.endsWith(".epub") -> DocumentType.EPUB
        lowerName.endsWith(".pdf") -> DocumentType.PDF
        lowerName.endsWith(".txt") -> DocumentType.TXT
        lowerName.endsWith(".fb2") -> DocumentType.FB2
        else -> null
    }
}

private fun stableFileName(value: String): String =
    value.hashCode().toUInt().toString(16)

private const val ArchiveEntryUriSeparator = "#areadaArchiveEntry="

fun archiveBookUriString(
    archiveUriString: String,
    entryName: String,
): String = archiveUriString + ArchiveEntryUriSeparator + URLEncoder.encode(entryName, Charsets.UTF_8.name())

fun parseArchiveBookUriString(uriString: String): ZipBookReference? {
    val separatorIndex = uriString.lastIndexOf(ArchiveEntryUriSeparator)
    if (separatorIndex < 0) {
        return null
    }
    val archiveUriString = uriString.substring(0, separatorIndex)
    val entryName = URLDecoder.decode(
        uriString.substring(separatorIndex + ArchiveEntryUriSeparator.length),
        Charsets.UTF_8.name(),
    )
    if (archiveUriString.isBlank() || entryName.isBlank()) {
        return null
    }
    return ZipBookReference(archiveUriString, entryName)
}
