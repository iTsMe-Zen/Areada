package app.areada.data.reader

import app.areada.data.library.moveListItem

internal data class RecentProgressState(
    val recents: List<RecentDocument>,
    val progressByUri: Map<String, ReadingProgress>,
)

internal object RecentReadingActions {
    fun updatedForOpen(
        recents: List<RecentDocument>,
        document: ReaderDocument,
        timestamp: Long = System.currentTimeMillis(),
    ): List<RecentDocument> {
        val updated = RecentDocument(
            uriString = document.uriString,
            title = document.title,
            type = document.type,
            lastOpenedAt = timestamp,
        )

        return buildList {
            add(updated)
            addAll(recents.filterNot { recent -> recent.uriString == document.uriString })
        }
    }

    fun removed(
        recents: List<RecentDocument>,
        progressByUri: Map<String, ReadingProgress>,
        uriString: String,
    ): RecentProgressState =
        RecentProgressState(
            recents = recents.filterNot { recent -> recent.uriString == uriString },
            progressByUri = progressByUri - uriString,
        )

    fun moved(
        recents: List<RecentDocument>,
        recent: RecentDocument,
        offset: Int,
    ): List<RecentDocument> {
        val index = recents.indexOfFirst { item -> item.uriString == recent.uriString }
        return moveListItem(recents, index, offset)
    }

    fun updatedForProgressSave(
        recents: List<RecentDocument>,
        progressByUri: Map<String, ReadingProgress>,
        progress: ReadingProgress,
        completed: Boolean,
    ): RecentProgressState =
        RecentProgressState(
            recents = if (completed) {
                recents.filterNot { recent -> recent.uriString == progress.uriString }
            } else {
                recents
            },
            progressByUri = if (completed) {
                progressByUri - progress.uriString
            } else {
                progressByUri + (progress.uriString to progress)
            },
        )

    fun renamedDocument(
        recents: List<RecentDocument>,
        oldUriString: String,
        document: ReaderDocument,
    ): List<RecentDocument> =
        recents.map { recent ->
            if (recent.uriString == oldUriString) {
                recent.copy(
                    uriString = document.uriString,
                    title = document.title,
                )
            } else {
                recent
            }
        }
}
