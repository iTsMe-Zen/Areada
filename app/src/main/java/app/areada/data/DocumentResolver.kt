package app.areada.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.areada.R
import app.areada.data.reader.ReaderDocument
import app.areada.data.reader.DocumentType
import java.util.Locale

object DocumentResolver {
    fun resolve(context: Context, uri: Uri): ReaderDocument {
        val contentResolver = context.contentResolver
        val displayName = runCatching { queryDisplayName(contentResolver, uri) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
            ?: "Untitled"
        val documentType = detectSupportedType(null, displayName)
            ?: detectSupportedType(runCatching { contentResolver.getType(uri) }.getOrNull(), displayName)
            ?: error(context.getString(R.string.unsupported_file_type))
        val title = displayName.substringBeforeLast('.', displayName).ifBlank { displayName }

        return ReaderDocument(
            uri = uri,
            uriString = uri.toString(),
            title = title,
            type = documentType,
        )
    }

    fun detectSupportedType(mimeType: String?, name: String): DocumentType? {
        val normalizedMime = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
        val lowerName = name.lowercase(Locale.ROOT)
        return when {
            normalizedMime == "application/epub+zip" ||
                normalizedMime == "application/x-epub" ||
                normalizedMime == "application/epub" ||
                lowerName.endsWith(".epub") -> DocumentType.EPUB
            normalizedMime == "application/pdf" || lowerName.endsWith(".pdf") -> DocumentType.PDF
            normalizedMime == "text/plain" || lowerName.endsWith(".txt") -> DocumentType.TXT
            normalizedMime == "application/x-fictionbook+xml" ||
                normalizedMime == "application/fb2+xml" ||
                lowerName.endsWith(".fb2") ||
                lowerName.endsWith(".fb2.zip") ||
                lowerName.endsWith(".fbz") -> DocumentType.FB2
            normalizedMime == "text/markdown" ||
                normalizedMime == "text/x-markdown" ||
                lowerName.endsWith(".md") ||
                lowerName.endsWith(".markdown") -> DocumentType.MARKDOWN
            normalizedMime == "application/zip" ||
                normalizedMime == "application/x-zip-compressed" ||
                lowerName.endsWith(".zip") -> DocumentType.ZIP
            normalizedMime == "application/x-7z-compressed" ||
                normalizedMime == "application/x-tar" ||
                normalizedMime == "application/x-bzip2" ||
                normalizedMime == "application/x-xz" ||
                normalizedMime == "application/x-rar-compressed" ||
                normalizedMime == "application/gzip" ||
                lowerName.endsWith(".7z") ||
                lowerName.endsWith(".tar") ||
                lowerName.endsWith(".tar.gz") ||
                lowerName.endsWith(".tgz") ||
                lowerName.endsWith(".tar.bz2") ||
                lowerName.endsWith(".tbz2") ||
                lowerName.endsWith(".tar.xz") ||
                lowerName.endsWith(".txz") ||
                lowerName.endsWith(".rar") ||
                lowerName.endsWith(".gz") ||
                lowerName.endsWith(".bz2") ||
                lowerName.endsWith(".xz") -> DocumentType.ARCHIVE
            else -> null
        }
    }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        }

        return null
    }
}
