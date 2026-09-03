package app.areada.data

import android.annotation.SuppressLint
import app.areada.data.reader.DocumentType
import app.areada.data.reader.ReaderDocument
import android.content.Context
import android.net.Uri
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale

object ArchiveBookContainer {
    private fun archiveCacheDir(context: Context) =
        File(context.cacheDir, "archive-books").also { it.mkdirs() }

    fun listSupportedEntries(
        context: Context,
        archiveUri: Uri,
    ): List<ZipBookEntry> {
        val name = archiveUri.lastPathSegment?.lowercase(Locale.ROOT) ?: ""
        val archiveUriString = archiveUri.toString()
        return when {
            name.endsWith(".tar.gz") || name.endsWith(".tgz") ->
                listTarGzEntries(context, archiveUri, archiveUriString)
            name.endsWith(".tar.bz2") || name.endsWith(".tbz2") ->
                listTarBz2Entries(context, archiveUri, archiveUriString)
            name.endsWith(".tar.xz") || name.endsWith(".txz") ->
                listTarXzEntries(context, archiveUri, archiveUriString)
            name.endsWith(".tar") -> listTarEntries(context, archiveUri, archiveUriString)
            name.endsWith(".bz2") -> listBz2Entry(name, archiveUriString)
            name.endsWith(".gz") -> listGzEntry(name, archiveUriString)
            name.endsWith(".xz") -> listXzEntry(name, archiveUriString)
            name.endsWith(".7z") -> list7zEntries(context, archiveUri, archiveUriString)
            else -> throw IllegalArgumentException("Unsupported archive format.")
        }
    }

    fun extractEntry(
        context: Context,
        entry: ZipBookEntry,
    ): ReaderDocument {
        val archiveUri = Uri.parse(entry.archiveUriString)
        val name = archiveUri.lastPathSegment?.lowercase(Locale.ROOT) ?: ""
        return when {
            name.endsWith(".tar.gz") || name.endsWith(".tgz") ->
                extractTarGzEntry(context, archiveUri, entry)
            name.endsWith(".tar.bz2") || name.endsWith(".tbz2") ->
                extractTarBz2Entry(context, archiveUri, entry)
            name.endsWith(".tar.xz") || name.endsWith(".txz") ->
                extractTarXzEntry(context, archiveUri, entry)
            name.endsWith(".tar") -> extractTarEntry(context, archiveUri, entry)
            name.endsWith(".bz2") -> extractBz2Entry(context, archiveUri, entry)
            name.endsWith(".gz") -> extractGzEntry(context, archiveUri, entry)
            name.endsWith(".xz") -> extractXzEntry(context, archiveUri, entry)
            name.endsWith(".7z") -> extract7zEntry(context, archiveUri, entry)
            else -> throw IllegalArgumentException("Unsupported archive format.")
        }
    }

    private fun listTarEntries(
        context: Context,
        archiveUri: Uri,
        archiveUriString: String,
    ): List<ZipBookEntry> {
        return runCatching {
            val entries = mutableListOf<ZipBookEntry>()
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                TarArchiveInputStream(input.buffered()).use { archive ->
                    while (true) {
                        val entry = archive.nextEntry ?: break
                        val entryName = safeZipEntryName(entry.name)
                        if (!entry.isDirectory && entryName != null) {
                            supportedZipEntryType(entryName)?.let { type ->
                                entries += ZipBookEntry(
                                    archiveUriString = archiveUriString,
                                    entryName = entryName,
                                    displayName = entryName,
                                    type = type,
                                    isArchiveEntry = true,
                                )
                            }
                        }
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            entries
        }.getOrElse {
            throw IllegalArgumentException("Could not open archive file.")
        }
    }

    private fun extractTarEntry(
        context: Context,
        archiveUri: Uri,
        entry: ZipBookEntry,
    ): ReaderDocument {
        val cacheDir = archiveCacheDir(context)
        val extension = entry.displayName
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .takeIf { it in setOf("epub", "pdf", "txt", "fb2") }
            ?: "book"
        val target = File(cacheDir, "${archiveStableFileName(entry.uriString)}.$extension")
        return runCatching {
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                TarArchiveInputStream(input.buffered()).use { archive ->
                    while (true) {
                        val archiveEntry = archive.nextEntry ?: break
                        val entryName = safeZipEntryName(archiveEntry.name)
                        if (!archiveEntry.isDirectory && entryName == entry.entryName) {
                            target.outputStream().use { output ->
                                archive.copyTo(output)
                            }
                            return ReaderDocument(
                                uri = Uri.fromFile(target),
                                uriString = entry.uriString,
                                title = entry.title,
                                type = entry.type,
                            )
                        }
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            throw IllegalArgumentException("Could not open selected file from archive.")
        }.getOrElse {
            throw IllegalArgumentException("Could not open selected file from archive.")
        }
    }

    private fun listTarGzEntries(
        context: Context,
        archiveUri: Uri,
        archiveUriString: String,
    ): List<ZipBookEntry> {
        return runCatching {
            val entries = mutableListOf<ZipBookEntry>()
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                val gzInput = GzipCompressorInputStream(
                    input.buffered(),
                )
                TarArchiveInputStream(gzInput).use { archive ->
                    while (true) {
                        val entry = archive.nextEntry ?: break
                        val entryName = safeZipEntryName(entry.name)
                        if (!entry.isDirectory && entryName != null) {
                            supportedZipEntryType(entryName)?.let { type ->
                                entries += ZipBookEntry(
                                    archiveUriString = archiveUriString,
                                    entryName = entryName,
                                    displayName = entryName,
                                    type = type,
                                    isArchiveEntry = true,
                                )
                            }
                        }
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            entries
        }.getOrElse {
            throw IllegalArgumentException("Could not open archive file.")
        }
    }

    private fun extractTarGzEntry(
        context: Context,
        archiveUri: Uri,
        entry: ZipBookEntry,
    ): ReaderDocument {
        val cacheDir = archiveCacheDir(context)
        val extension = entry.displayName
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .takeIf { it in setOf("epub", "pdf", "txt", "fb2") }
            ?: "book"
        val target = File(cacheDir, "${archiveStableFileName(entry.uriString)}.$extension")
        return runCatching {
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                val gzInput = GzipCompressorInputStream(
                    input.buffered(),
                )
                TarArchiveInputStream(gzInput).use { archive ->
                    while (true) {
                        val archiveEntry = archive.nextEntry ?: break
                        val entryName = safeZipEntryName(archiveEntry.name)
                        if (!archiveEntry.isDirectory && entryName == entry.entryName) {
                            target.outputStream().use { output ->
                                archive.copyTo(output)
                            }
                            return ReaderDocument(
                                uri = Uri.fromFile(target),
                                uriString = entry.uriString,
                                title = entry.title,
                                type = entry.type,
                            )
                        }
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            throw IllegalArgumentException("Could not open selected file from archive.")
        }.getOrElse {
            throw IllegalArgumentException("Could not open selected file from archive.")
        }
    }

    private fun listGzEntry(
        name: String,
        archiveUriString: String,
    ): List<ZipBookEntry> {
        val innerName = name.removeSuffix(".gz").ifBlank { "extracted" }
        val type = supportedZipEntryType(innerName) ?: return emptyList()
        return listOf(
            ZipBookEntry(
                archiveUriString = archiveUriString,
                entryName = innerName,
                displayName = innerName,
                type = type,
                isArchiveEntry = true,
            ),
        )
    }

    private fun extractGzEntry(
        context: Context,
        archiveUri: Uri,
        entry: ZipBookEntry,
    ): ReaderDocument {
        val cacheDir = archiveCacheDir(context)
        val extension = entry.displayName
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .takeIf { it in setOf("epub", "pdf", "txt", "fb2") }
            ?: "book"
        val target = File(cacheDir, "${archiveStableFileName(entry.uriString)}.$extension")
        return runCatching {
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                GzipCompressorInputStream(input.buffered()).use { gz ->
                    target.outputStream().use { output ->
                        gz.copyTo(output)
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            ReaderDocument(
                uri = Uri.fromFile(target),
                uriString = entry.uriString,
                title = entry.title,
                type = entry.type,
            )
        }.getOrElse {
            throw IllegalArgumentException("Could not open archive file.")
        }
    }

    private fun listBz2Entry(
        name: String,
        archiveUriString: String,
    ): List<ZipBookEntry> {
        val innerName = name.removeSuffix(".bz2").ifBlank { "extracted" }
        val type = supportedZipEntryType(innerName) ?: return emptyList()
        return listOf(
            ZipBookEntry(
                archiveUriString = archiveUriString,
                entryName = innerName,
                displayName = innerName,
                type = type,
                isArchiveEntry = true,
            ),
        )
    }

    private fun extractBz2Entry(
        context: Context,
        archiveUri: Uri,
        entry: ZipBookEntry,
    ): ReaderDocument {
        val cacheDir = archiveCacheDir(context)
        val extension = entry.displayName
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .takeIf { it in setOf("epub", "pdf", "txt", "fb2") }
            ?: "book"
        val target = File(cacheDir, "${archiveStableFileName(entry.uriString)}.$extension")
        return runCatching {
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                BZip2CompressorInputStream(input.buffered()).use { bz2 ->
                    target.outputStream().use { output ->
                        bz2.copyTo(output)
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            ReaderDocument(
                uri = Uri.fromFile(target),
                uriString = entry.uriString,
                title = entry.title,
                type = entry.type,
            )
        }.getOrElse {
            throw IllegalArgumentException("Could not open archive file.")
        }
    }

    private fun listTarBz2Entries(
        context: Context,
        archiveUri: Uri,
        archiveUriString: String,
    ): List<ZipBookEntry> {
        return runCatching {
            val entries = mutableListOf<ZipBookEntry>()
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                val bz2Input = BZip2CompressorInputStream(input.buffered())
                TarArchiveInputStream(bz2Input).use { archive ->
                    while (true) {
                        val entry = archive.nextEntry ?: break
                        val entryName = safeZipEntryName(entry.name)
                        if (!entry.isDirectory && entryName != null) {
                            supportedZipEntryType(entryName)?.let { type ->
                                entries += ZipBookEntry(
                                    archiveUriString = archiveUriString,
                                    entryName = entryName,
                                    displayName = entryName,
                                    type = type,
                                    isArchiveEntry = true,
                                )
                            }
                        }
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            entries
        }.getOrElse {
            throw IllegalArgumentException("Could not open archive file.")
        }
    }

    private fun extractTarBz2Entry(
        context: Context,
        archiveUri: Uri,
        entry: ZipBookEntry,
    ): ReaderDocument {
        val cacheDir = archiveCacheDir(context)
        val extension = entry.displayName
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .takeIf { it in setOf("epub", "pdf", "txt", "fb2") }
            ?: "book"
        val target = File(cacheDir, "${archiveStableFileName(entry.uriString)}.$extension")
        return runCatching {
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                val bz2Input = BZip2CompressorInputStream(input.buffered())
                TarArchiveInputStream(bz2Input).use { archive ->
                    while (true) {
                        val archiveEntry = archive.nextEntry ?: break
                        val entryName = safeZipEntryName(archiveEntry.name)
                        if (!archiveEntry.isDirectory && entryName == entry.entryName) {
                            target.outputStream().use { output ->
                                archive.copyTo(output)
                            }
                            return ReaderDocument(
                                uri = Uri.fromFile(target),
                                uriString = entry.uriString,
                                title = entry.title,
                                type = entry.type,
                            )
                        }
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            throw IllegalArgumentException("Could not open selected file from archive.")
        }.getOrElse {
            throw IllegalArgumentException("Could not open selected file from archive.")
        }
    }

    private fun listXzEntry(
        name: String,
        archiveUriString: String,
    ): List<ZipBookEntry> {
        val innerName = name.removeSuffix(".xz").ifBlank { "extracted" }
        val type = supportedZipEntryType(innerName) ?: return emptyList()
        return listOf(
            ZipBookEntry(
                archiveUriString = archiveUriString,
                entryName = innerName,
                displayName = innerName,
                type = type,
                isArchiveEntry = true,
            ),
        )
    }

    private fun extractXzEntry(
        context: Context,
        archiveUri: Uri,
        entry: ZipBookEntry,
    ): ReaderDocument {
        val cacheDir = archiveCacheDir(context)
        val extension = entry.displayName
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .takeIf { it in setOf("epub", "pdf", "txt", "fb2") }
            ?: "book"
        val target = File(cacheDir, "${archiveStableFileName(entry.uriString)}.$extension")
        return runCatching {
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                XZCompressorInputStream(input.buffered()).use { xz ->
                    target.outputStream().use { output ->
                        xz.copyTo(output)
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            ReaderDocument(
                uri = Uri.fromFile(target),
                uriString = entry.uriString,
                title = entry.title,
                type = entry.type,
            )
        }.getOrElse {
            throw IllegalArgumentException("Could not open archive file.")
        }
    }

    private fun listTarXzEntries(
        context: Context,
        archiveUri: Uri,
        archiveUriString: String,
    ): List<ZipBookEntry> {
        return runCatching {
            val entries = mutableListOf<ZipBookEntry>()
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                val xzInput = XZCompressorInputStream(input.buffered())
                TarArchiveInputStream(xzInput).use { archive ->
                    while (true) {
                        val entry = archive.nextEntry ?: break
                        val entryName = safeZipEntryName(entry.name)
                        if (!entry.isDirectory && entryName != null) {
                            supportedZipEntryType(entryName)?.let { type ->
                                entries += ZipBookEntry(
                                    archiveUriString = archiveUriString,
                                    entryName = entryName,
                                    displayName = entryName,
                                    type = type,
                                    isArchiveEntry = true,
                                )
                            }
                        }
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            entries
        }.getOrElse {
            throw IllegalArgumentException("Could not open archive file.")
        }
    }

    private fun extractTarXzEntry(
        context: Context,
        archiveUri: Uri,
        entry: ZipBookEntry,
    ): ReaderDocument {
        val cacheDir = archiveCacheDir(context)
        val extension = entry.displayName
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .takeIf { it in setOf("epub", "pdf", "txt", "fb2") }
            ?: "book"
        val target = File(cacheDir, "${archiveStableFileName(entry.uriString)}.$extension")
        return runCatching {
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                val xzInput = XZCompressorInputStream(input.buffered())
                TarArchiveInputStream(xzInput).use { archive ->
                    while (true) {
                        val archiveEntry = archive.nextEntry ?: break
                        val entryName = safeZipEntryName(archiveEntry.name)
                        if (!archiveEntry.isDirectory && entryName == entry.entryName) {
                            target.outputStream().use { output ->
                                archive.copyTo(output)
                            }
                            return ReaderDocument(
                                uri = Uri.fromFile(target),
                                uriString = entry.uriString,
                                title = entry.title,
                                type = entry.type,
                            )
                        }
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            throw IllegalArgumentException("Could not open selected file from archive.")
        }.getOrElse {
            throw IllegalArgumentException("Could not open selected file from archive.")
        }
    }

    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun list7zEntries(
        context: Context,
        archiveUri: Uri,
        archiveUriString: String,
    ): List<ZipBookEntry> {
        return runCatching {
            val entries = mutableListOf<ZipBookEntry>()
            context.contentResolver.openFileDescriptor(archiveUri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    fis.channel.use { channel ->
                        SevenZFile(channel).use { sevenZ ->
                            while (true) {
                                val entry = sevenZ.getNextEntry() ?: break
                                val entryName = safeZipEntryName(entry.name)
                                if (!entry.isDirectory && entryName != null) {
                                    supportedZipEntryType(entryName)?.let { type ->
                                        entries += ZipBookEntry(
                                            archiveUriString = archiveUriString,
                                            entryName = entryName,
                                            displayName = entryName,
                                            type = type,
                                            isArchiveEntry = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            entries
        }.getOrElse {
            throw IllegalArgumentException("Could not open archive file.")
        }
    }

    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun extract7zEntry(
        context: Context,
        archiveUri: Uri,
        entry: ZipBookEntry,
    ): ReaderDocument {
        val cacheDir = archiveCacheDir(context)
        val extension = entry.displayName
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .takeIf { it in setOf("epub", "pdf", "txt", "fb2") }
            ?: "book"
        val target = File(cacheDir, "${archiveStableFileName(entry.uriString)}.$extension")
        return runCatching {
            context.contentResolver.openFileDescriptor(archiveUri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    fis.channel.use { channel ->
                        SevenZFile(channel).use { sevenZ ->
                            while (true) {
                                val archiveEntry = sevenZ.getNextEntry() ?: break
                                val entryName = safeZipEntryName(archiveEntry.name)
                                if (!archiveEntry.isDirectory && entryName == entry.entryName) {
                                    target.outputStream().use { output ->
                                        val buffer = ByteArray(8192)
                                        while (true) {
                                            val read = sevenZ.read(buffer)
                                            if (read < 0) break
                                            output.write(buffer, 0, read)
                                        }
                                    }
                                    return ReaderDocument(
                                        uri = Uri.fromFile(target),
                                        uriString = entry.uriString,
                                        title = entry.title,
                                        type = entry.type,
                                    )
                                }
                            }
                        }
                    }
                }
            } ?: throw IllegalArgumentException("Could not open archive file.")
            throw IllegalArgumentException("Could not open selected file from archive.")
        }.getOrElse {
            throw IllegalArgumentException("Could not open selected file from archive.")
        }
    }
}

private fun archiveStableFileName(value: String): String =
    value.hashCode().toUInt().toString(16)
