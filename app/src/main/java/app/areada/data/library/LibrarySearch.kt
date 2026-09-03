package app.areada.data.library
import app.areada.data.reader.DocumentType

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

internal fun supportedTypeFromName(name: String): DocumentType? {
    val lowerName = name.lowercase(Locale.ROOT)
    return when {
        lowerName.endsWith(".epub") -> DocumentType.EPUB
        lowerName.endsWith(".pdf") -> DocumentType.PDF
        lowerName.endsWith(".txt") -> DocumentType.TXT
        lowerName.endsWith(".md") ||
            lowerName.endsWith(".markdown") -> DocumentType.MARKDOWN
        lowerName.endsWith(".fb2") ||
            lowerName.endsWith(".fb2.zip") ||
            lowerName.endsWith(".fbz") -> DocumentType.FB2
        lowerName.endsWith(".zip") -> DocumentType.ZIP
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

internal fun searchLibrary(
    context: Context,
    roots: List<LibraryRoot>,
    query: String,
): List<LibrarySearchResult> {
    val cleanQuery = query.trim().lowercase(Locale.ROOT)
    if (cleanQuery.isBlank() || roots.isEmpty()) {
        return emptyList()
    }

    val results = ArrayList<LibrarySearchResult>(80)
    var visited = 0
    roots.forEach { root ->
        if (results.size >= 80 || visited >= 50_000) {
            return@forEach
        }

        val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(root.treeUriString))
            ?: return@forEach
        if (root.name.lowercase(Locale.ROOT).contains(cleanQuery)) {
            results += LibrarySearchResult(
                id = folderIdStatic(root, ""),
                rootUriString = root.treeUriString,
                rootName = root.name,
                relativePath = "",
                title = root.name,
                type = LibrarySearchResultType.FOLDER,
            )
        }
        searchFolderRecursive(
            context = context,
            root = root,
            folder = rootFolder,
            relativePath = "",
            query = cleanQuery,
            depth = 0,
            results = results,
            visitedProvider = { visited },
            onVisited = { visited++ },
        )
    }

    return results
}

private fun searchFolderRecursive(
    context: Context,
    root: LibraryRoot,
    folder: DocumentFile,
    relativePath: String,
    query: String,
    depth: Int,
    results: MutableList<LibrarySearchResult>,
    visitedProvider: () -> Int,
    onVisited: () -> Unit,
) {
    if (
        depth > 18 ||
        results.size >= 80 ||
        visitedProvider() >= 50_000
    ) {
        return
    }

    val children = runCatching { folder.listFiles() }.getOrDefault(emptyArray<DocumentFile>())
    children.forEach { child ->
        if (results.size >= 80 || visitedProvider() >= 50_000) {
            return
        }
        onVisited()

        val name = runCatching { child.name?.trim().orEmpty() }.getOrDefault("")
        if (name.isBlank()) {
            return@forEach
        }
        val childRelativePath = if (relativePath.isBlank()) name else "$relativePath/$name"
        val lowerName = name.lowercase(Locale.ROOT)

        if (runCatching { child.isDirectory }.getOrDefault(false)) {
            if (lowerName.contains(query)) {
                results += LibrarySearchResult(
                    id = folderIdStatic(root, childRelativePath),
                    rootUriString = root.treeUriString,
                    rootName = root.name,
                    relativePath = childRelativePath,
                    title = name,
                    type = LibrarySearchResultType.FOLDER,
                )
            }
            searchFolderRecursive(
                context = context,
                root = root,
                folder = child,
                relativePath = childRelativePath,
                query = query,
                depth = depth + 1,
                results = results,
                visitedProvider = visitedProvider,
                onVisited = onVisited,
            )
            return@forEach
        }

        if (!runCatching { child.isFile }.getOrDefault(false)) {
            return@forEach
        }

        val documentType = supportedTypeFromName(name) ?: return@forEach
        val noteMatches = documentType == DocumentType.TXT &&
            noteSearchText(context, child.uri).contains(query)
        if (!lowerName.contains(query) && !noteMatches) {
            return@forEach
        }
        results += LibrarySearchResult(
            id = child.uri.toString(),
            rootUriString = root.treeUriString,
            rootName = root.name,
            relativePath = childRelativePath,
            title = name.substringBeforeLast('.', name).ifBlank { name },
            type = LibrarySearchResultType.BOOK,
            documentType = documentType,
            uriString = child.uri.toString(),
        )
    }
}

internal fun buildSearchIndex(
    context: Context,
    roots: List<LibraryRoot>,
): List<LibrarySearchIndexEntry> {
    if (roots.isEmpty()) {
        return emptyList()
    }

    val index = ArrayList<LibrarySearchIndexEntry>(512)
    var visited = 0
    roots.forEach { root ->
        if (index.size >= 10_000 || visited >= 50_000) {
            return@forEach
        }

        val rootFolder = DocumentFile.fromTreeUri(context, Uri.parse(root.treeUriString))
            ?: return@forEach
        index += rootSearchIndexEntry(root)

        val pending = ArrayDeque<SearchFolderWork>()
        pending.add(SearchFolderWork(rootFolder, "", 0))
        while (
            pending.isNotEmpty() &&
            index.size < 10_000 &&
            visited < 50_000
        ) {
            val work = pending.removeFirst()
            if (work.depth > 18) {
                continue
            }

            val children = runCatching { work.folder.listFiles() }.getOrDefault(emptyArray<DocumentFile>())
            for (child in children) {
                if (index.size >= 10_000 || visited >= 50_000) {
                    break
                }
                visited++
                if (visited % 128 == 0) {
                    Thread.yield()
                }

                val name = runCatching { child.name?.trim().orEmpty() }.getOrDefault("")
                if (name.isBlank()) {
                    continue
                }
                val childRelativePath = if (work.relativePath.isBlank()) name else "${work.relativePath}/$name"

                if (runCatching { child.isDirectory }.getOrDefault(false)) {
                    index += folderSearchIndexEntry(root, childRelativePath, name)
                    pending.add(SearchFolderWork(child, childRelativePath, work.depth + 1))
                    continue
                }

                val documentType = supportedTypeFromName(name) ?: continue
                val noteContent = if (documentType == DocumentType.TXT) {
                    noteSearchText(context, child.uri)
                } else {
                    ""
                }
                index += bookSearchIndexEntry(
                    root = root,
                    relativePath = childRelativePath,
                    name = name,
                    documentType = documentType,
                    uriString = child.uri.toString(),
                    noteContent = noteContent,
                )
            }
        }
    }

    return index
}

private fun folderIdStatic(
    root: LibraryRoot,
    relativePath: String,
): String = "${root.treeUriString}::$relativePath"

private fun rootSearchIndexEntry(root: LibraryRoot): LibrarySearchIndexEntry =
    folderSearchIndexEntry(
        root = root,
        relativePath = "",
        name = root.name,
    )

private fun folderSearchIndexEntry(
    root: LibraryRoot,
    relativePath: String,
    name: String,
): LibrarySearchIndexEntry {
    val result = LibrarySearchResult(
        id = folderIdStatic(root, relativePath),
        rootUriString = root.treeUriString,
        rootName = root.name,
        relativePath = relativePath,
        title = name,
        type = LibrarySearchResultType.FOLDER,
    )
    return LibrarySearchIndexEntry(
        result = result,
        searchText = "${root.name} $relativePath $name".lowercase(Locale.ROOT),
    )
}

private fun bookSearchIndexEntry(
    root: LibraryRoot,
    relativePath: String,
    name: String,
    documentType: DocumentType,
    uriString: String,
    noteContent: String = "",
): LibrarySearchIndexEntry {
    val title = name.substringBeforeLast('.', name).ifBlank { name }
    val result = LibrarySearchResult(
        id = uriString,
        rootUriString = root.treeUriString,
        rootName = root.name,
        relativePath = relativePath,
        title = title,
        type = LibrarySearchResultType.BOOK,
        documentType = documentType,
        uriString = uriString,
    )
    return LibrarySearchIndexEntry(
        result = result,
        searchText = "${root.name} $relativePath $title $name $noteContent".lowercase(Locale.ROOT),
    )
}

private const val MAX_NOTE_SEARCH_CHARS = 24_000

private fun noteSearchText(
    context: Context,
    uri: Uri,
): String =
    runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            val buffer = CharArray(MAX_NOTE_SEARCH_CHARS)
            val count = reader.read(buffer)
            if (count > 0) String(buffer, 0, count) else ""
        }.orEmpty()
    }.getOrDefault("")
        .lowercase(Locale.ROOT)

private data class SearchFolderWork(
    val folder: DocumentFile,
    val relativePath: String,
    val depth: Int,
)
