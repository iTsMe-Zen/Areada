package app.areada.data.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import app.areada.reader.fb2.LocalTextExtractor
import app.areada.data.reader.DocumentType
import app.areada.data.DocumentResolver
import app.areada.data.decodeLocalText
import app.areada.data.reader.ReaderDocument
import app.areada.data.reader.TextDocumentContent

object LibraryRepository {
    private const val MAX_FOLDER_DOCUMENT_ID_CACHE = 512
    private const val MAX_SEARCH_DEPTH = 18
    private val folderDocumentIdCacheLock = Any()
    private val folderDocumentIdCache = linkedMapOf<String, String>()

    fun resolveRoot(
        context: Context,
        treeUri: Uri,
    ): LibraryRoot {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Unable to read that folder.")
        val name = root.name?.ifBlank { null } ?: "Library"
        return LibraryRoot(
            treeUriString = treeUri.toString(),
            name = name,
        )
    }

    fun loadFolder(
        context: Context,
        root: LibraryRoot,
        relativePath: String,
    ): LibraryFolderState =
        loadFolderFast(context, root, relativePath) ?: loadFolderWithDocumentFile(context, root, relativePath)

    fun clearFolderNavigationCache(rootUriString: String? = null) {
        synchronized(folderDocumentIdCacheLock) {
            if (rootUriString == null) {
                folderDocumentIdCache.clear()
                return@synchronized
            }

            val keysToRemove = folderDocumentIdCache.keys
                .filter { key -> key.startsWith("$rootUriString::") }
            keysToRemove.forEach { key -> folderDocumentIdCache.remove(key) }
        }
    }

    private fun loadFolderWithDocumentFile(
        context: Context,
        root: LibraryRoot,
        relativePath: String,
    ): LibraryFolderState {
        val rootUri = Uri.parse(root.treeUriString)
        val rootFolder = DocumentFile.fromTreeUri(context, rootUri)
            ?: error("Unable to access the selected folder.")
        val currentFolder = resolveFolder(rootFolder, relativePath)
        val entries = runCatching { currentFolder.listFiles() }
            .getOrElse { throw IllegalArgumentException("Unable to open that folder.") }

        val folders = entries
            .mapNotNull { folder ->
                runCatching {
                    if (!folder.isDirectory) {
                        return@runCatching null
                    }
                    val name = folder.name?.trim().orEmpty()
                    if (name.isBlank()) {
                        null
                    } else {
                        val folderPath = if (relativePath.isBlank()) name else "$relativePath/$name"
                        LibraryFolderEntry(
                            id = folderId(root, folderPath),
                            relativePath = folderPath,
                            name = name,
                        )
                    }
                }.getOrNull()
            }
            .sortedWith(NaturalSort.comparator { it.name })

        val books = entries
            .mapNotNull { file ->
                runCatching {
                    if (!file.isFile) {
                        return@runCatching null
                    }
                    val name = file.name?.trim().orEmpty()
                    if (name.isBlank()) {
                        return@runCatching null
                    }

                    val type = supportedTypeFromName(name) ?: return@runCatching null
                    LibraryBookEntry(
                        id = file.uri.toString(),
                        uriString = file.uri.toString(),
                        fileName = name,
                        title = name.substringBeforeLast('.', name).ifBlank { name },
                        type = type,
                    )
                }.getOrNull()
            }
            .sortedWith(NaturalSort.comparator { it.title })

        return LibraryFolderState(
            root = root,
            currentRelativePath = relativePath,
            pathSegments = buildPathSegments(root, relativePath),
            folders = folders,
            books = books,
        )
    }

    private fun loadFolderFast(
        context: Context,
        root: LibraryRoot,
        relativePath: String,
    ): LibraryFolderState? =
        runCatching {
            val rootUri = Uri.parse(root.treeUriString)
            val targetDocumentId = resolveFolderDocumentId(
                context = context,
                rootUri = rootUri,
                root = root,
                relativePath = relativePath,
            ) ?: return@runCatching null

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, targetDocumentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            val folders = mutableListOf<LibraryFolderEntry>()
            val books = mutableListOf<LibraryBookEntry>()

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val documentIdIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeTypeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (documentIdIndex < 0 || nameIndex < 0 || mimeTypeIndex < 0) {
                    return@runCatching null
                }

                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(documentIdIndex).orEmpty()
                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    val mimeType = cursor.getString(mimeTypeIndex).orEmpty()
                    if (documentId.isBlank() || name.isBlank()) {
                        continue
                    }

                    val documentUriString = DocumentsContract
                        .buildDocumentUriUsingTree(rootUri, documentId)
                        .toString()
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val folderPath = if (relativePath.isBlank()) name else "$relativePath/$name"
                        cacheFolderDocumentId(root, folderPath, documentId)
                        folders += LibraryFolderEntry(
                            id = folderId(root, folderPath),
                            relativePath = folderPath,
                            name = name,
                        )
                        continue
                    }

                    val type = supportedTypeFromName(name) ?: continue
                    books += LibraryBookEntry(
                        id = documentUriString,
                        uriString = documentUriString,
                        fileName = name,
                        title = name.substringBeforeLast('.', name).ifBlank { name },
                        type = type,
                    )
                }
            } ?: return@runCatching null

            cacheFolderDocumentId(root, relativePath, targetDocumentId)
            LibraryFolderState(
                root = root,
                currentRelativePath = relativePath,
                pathSegments = buildPathSegments(root, relativePath),
                folders = folders.sortedWith(NaturalSort.comparator { it.name }),
                books = books.sortedWith(NaturalSort.comparator { it.title }),
            )
        }.getOrNull()

    fun deleteFolder(
        context: Context,
        root: LibraryRoot,
        relativePath: String,
    ): Boolean {
        val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(root.treeUriString))
            ?: return false
        return resolveFolder(rootFolder, relativePath).delete()
    }

    fun deleteBook(
        context: Context,
        uriString: String,
    ): Boolean =
        DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.delete() == true

    fun renameFolder(
        context: Context,
        root: LibraryRoot,
        relativePath: String,
        newName: String,
    ): Boolean {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            return false
        }

        val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(root.treeUriString))
            ?: return false
        return renameDocumentUri(context, resolveFolder(rootFolder, relativePath), cleanName) != null
    }

    fun renameBook(
        context: Context,
        root: LibraryRoot?,
        relativePath: String,
        book: LibraryBookEntry,
        newTitle: String,
    ): Boolean {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isBlank()) {
            return false
        }

        val originalExtension = book.fileName.substringAfterLast('.', "")
        val targetName = if (
            originalExtension.isNotBlank() &&
            !cleanTitle.endsWith(".$originalExtension", ignoreCase = true)
        ) {
            "$cleanTitle.$originalExtension"
        } else {
            cleanTitle
        }

        findFileInFolder(
            context = context,
            root = root,
            relativePath = relativePath,
            uriString = book.uriString,
        )?.let { file ->
            if (renameDocumentUri(context, file, targetName) != null) {
                return true
            }
        }

        return DocumentFile.fromSingleUri(context, Uri.parse(book.uriString))?.let { file ->
            renameDocumentUri(context, file, targetName) != null
        } == true
    }

    fun renameTextDocument(
        context: Context,
        roots: List<LibraryRoot>,
        currentRoot: LibraryRoot?,
        currentRelativePath: String,
        document: ReaderDocument,
        newTitle: String,
    ): ReaderDocument? {
        val targetName = targetTextFileName(newTitle) ?: return null

        findFileInFolder(
            context = context,
            root = currentRoot,
            relativePath = currentRelativePath,
            uriString = document.uriString,
        )?.let { file ->
            renameTextFile(context, file, targetName)?.let { return it }
        }

        DocumentFile.fromSingleUri(context, document.uri)?.let { file ->
            renameTextFile(context, file, targetName)?.let { return it }
        }

        roots.forEach { root ->
            val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(root.treeUriString)) ?: return@forEach
            findFileByUri(rootFolder, document.uriString)?.let { file ->
                renameTextFile(context, file, targetName)?.let { return it }
            }
        }

        return null
    }

    fun createTextNote(
        context: Context,
        root: LibraryRoot,
        relativePath: String,
        baseName: String? = null,
    ): LibraryBookEntry? {
        val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(root.treeUriString))
            ?: return null
        val targetFolder = resolveFolder(rootFolder, relativePath)
        val noteName = uniqueNoteName(targetFolder, baseName)
        val noteFile = targetFolder.createFile("text/plain", noteName)
            ?: return null
        context.contentResolver.openOutputStream(noteFile.uri, "wt")?.use { output ->
            output.write(ByteArray(0))
        } ?: return null

        val fileName = noteFile.name?.trim().orEmpty().ifBlank { noteName }
        return LibraryBookEntry(
            id = noteFile.uri.toString(),
            uriString = noteFile.uri.toString(),
            fileName = fileName,
            title = fileName.substringBeforeLast('.', fileName).ifBlank { fileName },
            type = DocumentType.TXT,
        )
    }

    fun findBookLocation(
        context: Context,
        roots: List<LibraryRoot>,
        uriString: String,
    ): LibraryBookLocation? {
        if (uriString.isBlank()) {
            return null
        }

        roots.forEach { root ->
            val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(root.treeUriString))
                ?: return@forEach
            val folderRelativePath = findContainingFolderRelativePath(
                folder = rootFolder,
                uriString = uriString,
                relativePath = "",
            )
            if (folderRelativePath != null) {
                return LibraryBookLocation(
                    root = root,
                    folderRelativePath = folderRelativePath,
                )
            }
        }
        return null
    }

    fun readText(
        context: Context,
        uri: Uri,
    ): String =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                decodeLocalText(input.readBytes())
            }
        }.getOrDefault(null).orEmpty()

    fun readTextLikeDocument(
        context: Context,
        document: ReaderDocument,
    ): String =
        readTextLikeDocumentContent(context, document).text

    fun readTextLikeDocumentContent(
        context: Context,
        document: ReaderDocument,
    ): TextDocumentContent =
        when (document.type) {
            DocumentType.TXT,
            DocumentType.MARKDOWN -> TextDocumentContent(text = readText(context, document.uri))
            DocumentType.FB2 -> runCatching {
                LocalTextExtractor.readFb2Document(context, document.uri)
            }.getOrElse {
                throw IllegalArgumentException("Unable to read that FB2.")
            }.let { fb2 ->
                val text = fb2.text.ifBlank {
                    throw IllegalArgumentException("This FB2 does not contain readable text.")
                }
                val title = listOfNotNull(
                    fb2.title?.takeIf { title -> title.isNotBlank() },
                    fb2.author?.takeIf { author -> author.isNotBlank() },
                ).joinToString(" - ").ifBlank { null }
                TextDocumentContent(
                    text = text,
                    title = title,
                )
            }

            DocumentType.EPUB,
            DocumentType.PDF,
            DocumentType.ZIP,
            DocumentType.ARCHIVE -> TextDocumentContent(text = "")
        }

    fun saveText(
        context: Context,
        uri: Uri,
        text: String,
    ): Boolean =
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)

    fun folderId(
        root: LibraryRoot,
        relativePath: String,
    ): String = "${root.treeUriString}::$relativePath"

    private fun resolveFolder(
        root: DocumentFile,
        relativePath: String,
    ): DocumentFile {
        if (relativePath.isBlank()) {
            return root
        }

        return relativePath.split('/')
            .filter { it.isNotBlank() }
            .fold(root) { current, segment ->
                val children = runCatching { current.listFiles() }
                    .getOrElse { throw IllegalArgumentException("Unable to open that folder.") }
                children.firstOrNull { file ->
                    runCatching { file.isDirectory && file.name == segment }.getOrDefault(false)
                } ?: throw IllegalArgumentException("That folder is no longer available.")
            }
    }

    private fun buildPathSegments(
        root: LibraryRoot,
        relativePath: String,
    ): List<LibraryPathSegment> {
        val segments = mutableListOf(
            LibraryPathSegment(
                relativePath = "",
                name = root.name,
            ),
        )

        if (relativePath.isBlank()) {
            return segments
        }

        val names = relativePath.split('/').filter { it.isNotBlank() }
        var runningPath = ""
        names.forEach { name ->
            runningPath = if (runningPath.isBlank()) name else "$runningPath/$name"
            segments += LibraryPathSegment(
                relativePath = runningPath,
                name = name,
            )
        }

        return segments
    }

    private fun resolveFolderDocumentId(
        context: Context,
        rootUri: Uri,
        root: LibraryRoot,
        relativePath: String,
    ): String? {
        cachedFolderDocumentId(root, relativePath)?.let { documentId -> return documentId }

        val rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(rootUri) }
            .getOrNull()
            ?: return null
        cacheFolderDocumentId(root, "", rootDocumentId)
        if (relativePath.isBlank()) {
            return rootDocumentId
        }

        var currentDocumentId = rootDocumentId
        var runningPath = ""
        relativePath.split('/')
            .filter { segment -> segment.isNotBlank() }
            .forEach { segment ->
                runningPath = if (runningPath.isBlank()) segment else "$runningPath/$segment"
                val cached = cachedFolderDocumentId(root, runningPath)
                if (cached != null) {
                    currentDocumentId = cached
                    return@forEach
                }

                currentDocumentId = findChildFolderDocumentId(
                    context = context,
                    rootUri = rootUri,
                    parentDocumentId = currentDocumentId,
                    folderName = segment,
                ) ?: return null
                cacheFolderDocumentId(root, runningPath, currentDocumentId)
            }

        return currentDocumentId
    }

    private fun findChildFolderDocumentId(
        context: Context,
        rootUri: Uri,
        parentDocumentId: String,
        folderName: String,
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

        return runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val documentIdIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeTypeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (documentIdIndex < 0 || nameIndex < 0 || mimeTypeIndex < 0) {
                    return@use null
                }

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    val mimeType = cursor.getString(mimeTypeIndex).orEmpty()
                    if (name == folderName && mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        return@use cursor.getString(documentIdIndex).orEmpty().ifBlank { null }
                    }
                }

                null
            }
        }.getOrNull()
    }

    private fun folderDocumentIdCacheKey(
        root: LibraryRoot,
        relativePath: String,
    ): String = "${root.treeUriString}::$relativePath"

    private fun cachedFolderDocumentId(
        root: LibraryRoot,
        relativePath: String,
    ): String? =
        synchronized(folderDocumentIdCacheLock) {
            val key = folderDocumentIdCacheKey(root, relativePath)
            val cached = folderDocumentIdCache.remove(key)
            if (cached != null) {
                folderDocumentIdCache[key] = cached
            }
            cached
        }

    private fun cacheFolderDocumentId(
        root: LibraryRoot,
        relativePath: String,
        documentId: String,
    ) {
        if (documentId.isBlank()) {
            return
        }
        synchronized(folderDocumentIdCacheLock) {
            folderDocumentIdCache[folderDocumentIdCacheKey(root, relativePath)] = documentId
            while (folderDocumentIdCache.size > MAX_FOLDER_DOCUMENT_ID_CACHE && folderDocumentIdCache.isNotEmpty()) {
                val oldestKey = folderDocumentIdCache.keys.first()
                folderDocumentIdCache.remove(oldestKey)
            }
        }
    }

    private fun uniqueNoteName(
        folder: DocumentFile,
        baseName: String? = null,
    ): String {
        val existingNames = runCatching {
            folder.listFiles()
                .mapNotNull { file -> java.util.Locale.ROOT.let { file.name?.lowercase(it) } }
                .toSet()
        }.getOrDefault(emptySet())

        val cleanBaseName = baseName
            ?.let(::targetTextFileName)
            ?.substringBeforeLast('.', "")
            ?.ifBlank { null }
            ?: "Note"
        repeat(1000) { index ->
            val name = if (index == 0) "$cleanBaseName.txt" else "$cleanBaseName ${index + 1}.txt"
            if (java.util.Locale.ROOT.let { name.lowercase(it) } !in existingNames) {
                return name
            }
        }

        return "$cleanBaseName ${System.currentTimeMillis()}.txt"
    }

    private fun targetTextFileName(rawTitle: String): String? {
        val cleanTitle = rawTitle
            .trim()
            .map { character ->
                if (character in invalidFileNameCharacters) ' ' else character
            }
            .joinToString("")
            .trim()
        if (cleanTitle.isBlank()) {
            return null
        }

        return if (cleanTitle.endsWith(".txt", ignoreCase = true)) {
            cleanTitle
        } else {
            "$cleanTitle.txt"
        }
    }

    private val invalidFileNameCharacters = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    private fun findFileInFolder(
        context: Context,
        root: LibraryRoot?,
        relativePath: String,
        uriString: String,
    ): DocumentFile? {
        root ?: return null
        val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(root.treeUriString))
            ?: return null
        val targetFolder = runCatching { resolveFolder(rootFolder, relativePath) }.getOrNull()
            ?: return null
        return runCatching {
            targetFolder.listFiles().firstOrNull { file ->
                runCatching {
                    file.isFile && file.uri.toString() == uriString
                }.getOrDefault(false)
            }
        }.getOrNull()
    }

    private fun findFileByUri(
        folder: DocumentFile,
        uriString: String,
        depth: Int = 0,
    ): DocumentFile? {
        if (depth > MAX_SEARCH_DEPTH) {
            return null
        }

        val children = runCatching { folder.listFiles() }.getOrDefault(emptyArray<DocumentFile>())
        children.forEach { child ->
            val found = runCatching {
                when {
                    child.uri.toString() == uriString && child.isFile -> child
                    child.isDirectory -> findFileByUri(child, uriString, depth + 1)
                    else -> null
                }
            }.getOrNull()
            if (found != null) {
                return found
            }
        }
        return null
    }

    private fun findContainingFolderRelativePath(
        folder: DocumentFile,
        uriString: String,
        relativePath: String,
        depth: Int = 0,
    ): String? {
        if (depth > MAX_SEARCH_DEPTH) {
            return null
        }

        val children = runCatching { folder.listFiles() }.getOrDefault(emptyArray<DocumentFile>())
        children.forEach { child ->
            val found = runCatching {
                when {
                    child.isFile && child.uri.toString() == uriString -> relativePath
                    child.isDirectory -> {
                        val name = child.name?.trim().orEmpty()
                        val childRelativePath = if (name.isBlank()) {
                            relativePath
                        } else if (relativePath.isBlank()) {
                            name
                        } else {
                            "$relativePath/$name"
                        }
                        findContainingFolderRelativePath(
                            folder = child,
                            uriString = uriString,
                            relativePath = childRelativePath,
                            depth = depth + 1,
                        )
                    }
                    else -> null
                }
            }.getOrNull()
            if (found != null) {
                return found
            }
        }
        return null
    }

    private fun renameTextFile(
        context: Context,
        file: DocumentFile,
        targetName: String,
    ): ReaderDocument? {
        val currentName = file.name?.trim().orEmpty()
        val currentType = DocumentResolver.detectSupportedType(file.type, currentName)
        if (currentType != null && currentType != DocumentType.TXT) {
            return null
        }

        val renamedUri = if (currentName.equals(targetName, ignoreCase = false)) {
            file.uri
        } else {
            renameDocumentUri(context, file, targetName) ?: return null
        }
        val renamedName = queryDisplayName(context, renamedUri).orEmpty()
            .ifBlank { file.name?.trim().orEmpty() }
            .ifBlank { targetName }
        return ReaderDocument(
            uri = renamedUri,
            uriString = renamedUri.toString(),
            title = renamedName.substringBeforeLast('.', renamedName).ifBlank { renamedName },
            type = DocumentType.TXT,
        )
    }

    private fun renameDocumentUri(
        context: Context,
        file: DocumentFile,
        targetName: String,
    ): Uri? {
        val renamedUri = runCatching {
            DocumentsContract.renameDocument(context.contentResolver, file.uri, targetName)
        }.getOrNull()
        if (renamedUri != null) {
            return renamedUri
        }

        return if (runCatching { file.renameTo(targetName) }.getOrDefault(false)) {
            file.uri
        } else {
            null
        }
    }

    private fun queryDisplayName(
        context: Context,
        uri: Uri,
    ): String? =
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use null
            }
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
}
