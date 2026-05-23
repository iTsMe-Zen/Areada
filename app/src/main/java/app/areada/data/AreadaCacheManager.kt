package app.areada.data

import android.content.Context
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

object AreadaCacheManager {
    private const val TargetInactiveCacheBytes = 25L * 1024L * 1024L
    private const val HardInactiveCacheBytes = 50L * 1024L * 1024L
    private val cacheMutationLock = Any()

    fun <T> withCacheLock(block: () -> T): T =
        synchronized(cacheMutationLock) { block() }

    suspend fun cleanup(
        context: Context,
        protectedRoots: Set<File> = emptySet(),
    ) {
        coroutineContext.ensureActive()
        val roots = controlledCacheRoots(context)
        if (roots.isEmpty()) {
            return
        }

        val totalBytes = roots.sumOf { root -> root.sizeBytes() }
        if (totalBytes <= HardInactiveCacheBytes) {
            return
        }

        val protectedPaths = protectedRoots
            .mapNotNull { root -> root.canonicalPathOrNull() }
            .toSet()
        val candidates = cacheCandidates(roots, protectedPaths)
            .filter { candidate -> candidate.sizeBytes > 0L }
            .sortedBy { candidate -> candidate.lastModified }

        withCacheLock {
            var remainingBytes = totalBytes
            candidates.forEach { candidate ->
                if (remainingBytes <= TargetInactiveCacheBytes) {
                    return@withCacheLock
                }
                if (candidate.file.deleteSafely()) {
                    remainingBytes -= candidate.sizeBytes
                }
            }
        }
    }

    fun isOverHardLimit(context: Context): Boolean =
        controlledCacheRoots(context).sumOf { root -> root.sizeBytes() } > HardInactiveCacheBytes

    private fun controlledCacheRoots(context: Context): List<File> =
        listOfNotNull(
            File(context.cacheDir, "areada"),
            context.externalCacheDir?.let { externalCacheDir -> File(externalCacheDir, "areada") },
        ).filter { root -> root.exists() }

    private fun cacheCandidates(
        roots: List<File>,
        protectedPaths: Set<String>,
    ): List<CacheCandidate> =
        roots.flatMap { root ->
            buildList {
                listOf("epub", "pdf", "open_with").forEach { childName ->
                    File(root, childName).listFiles()
                        ?.forEach { child ->
                            if (childName == "epub" && child.isDirectory && child.name.startsWith("v")) {
                                child.listFiles()
                                    ?.forEach { versionedChild -> addCandidateIfSafe(versionedChild, protectedPaths) }
                                    ?: addCandidateIfSafe(child, protectedPaths)
                            } else {
                                addCandidateIfSafe(child, protectedPaths)
                            }
                        }
                }
                root.listFiles()
                    ?.filterNot { child -> child.name in setOf("epub", "pdf", "open_with") }
                    ?.forEach { child -> addCandidateIfSafe(child, protectedPaths) }
            }
        }

    private fun MutableList<CacheCandidate>.addCandidateIfSafe(
        file: File,
        protectedPaths: Set<String>,
    ) {
        val path = file.canonicalPathOrNull() ?: return
        if (protectedPaths.any { protectedPath ->
                path == protectedPath ||
                    path.startsWith("$protectedPath${File.separator}") ||
                    protectedPath.startsWith("$path${File.separator}")
            }
        ) {
            return
        }
        add(
            CacheCandidate(
                file = file,
                sizeBytes = file.sizeBytes(),
                lastModified = file.lastModifiedDeep(),
            ),
        )
    }

    private fun File.sizeBytes(): Long {
        if (!exists()) {
            return 0L
        }
        if (isFile) {
            return length().coerceAtLeast(0L)
        }
        return listFiles()?.sumOf { child -> child.sizeBytes() } ?: 0L
    }

    private fun File.lastModifiedDeep(): Long {
        val own = lastModified()
        if (!isDirectory) {
            return own
        }
        val childMax = listFiles()
            ?.maxOfOrNull { child -> child.lastModifiedDeep() }
            ?: 0L
        return maxOf(own, childMax)
    }

    private fun File.deleteSafely(): Boolean =
        runCatching {
            if (isDirectory) {
                deleteRecursively()
            } else {
                delete()
            }
        }.getOrDefault(false)

    private fun File.canonicalPathOrNull(): String? =
        runCatching { canonicalPath }.getOrNull()

    private data class CacheCandidate(
        val file: File,
        val sizeBytes: Long,
        val lastModified: Long,
    )
}
