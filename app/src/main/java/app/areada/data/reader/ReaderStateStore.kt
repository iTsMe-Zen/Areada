package app.areada.data.reader

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import app.areada.data.BookNoteLink
import app.areada.data.BookStatus
import app.areada.data.bookStatusFromName
import app.areada.data.library.LibraryFileFilter
import app.areada.data.library.LibraryRoot
import app.areada.data.library.LibrarySortMode

object ReaderStateStore {
    private const val PREFERENCES_NAME = "areada_reader"
    private var cachedPrefs: SharedPreferences? = null
    private var cachedPrefsContext: Context? = null

    private fun prefs(context: Context): SharedPreferences {
        if (cachedPrefsContext !== context) {
            cachedPrefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            cachedPrefsContext = context
        }
        return cachedPrefs!!
    }
    private const val KEY_READER_PREFERENCES = "reader_preferences"
    private const val KEY_READING_PROGRESS = "reading_progress"
    private const val KEY_LIBRARY_ROOTS = "library_roots"
    private const val KEY_LIBRARY_SORT_MODE = "library_sort_mode"
    private const val KEY_LIBRARY_FILE_FILTER = "library_file_filter"
    private const val KEY_HOME_TAB = "home_tab"
    private const val KEY_PINNED_LIBRARY_ITEMS = "pinned_library_items"
    private const val KEY_LIBRARY_ADDED_AT = "library_added_at"
    private const val KEY_BOOKMARKS = "bookmarks"
    private const val KEY_BOOK_STATUSES = "book_statuses"
    private const val KEY_NOTE_DOCUMENT_IDS = "note_document_ids"
    private const val KEY_BOOK_NOTE_LINKS = "book_note_links"
    private const val KEY_NOTE_LAST_SECTIONS = "note_last_sections"

    fun loadPreferences(context: Context): ReaderPreferences {
        val payload = prefs(context)
            .getString(KEY_READER_PREFERENCES, null)
            ?: return ReaderPreferences()

        return runCatching {
            val item = JSONObject(payload)
            sanitizeReaderPreferences(
                ReaderPreferences(
                    themeMode = ReaderThemeMode.entries.firstOrNull { it.name == item.optString("themeMode") }
                        ?: ReaderPreferences().themeMode,
                    fontChoice = ReaderFontChoice.entries.firstOrNull { it.name == item.optString("fontChoice") }
                        ?: ReaderPreferences().fontChoice,
                    languageMode = readerLanguageModeFromName(item.optString("languageMode")),
                    orientationMode = readerOrientationModeFromName(item.optString("orientationMode")),
                    navigationMode = readerNavigationModeFromName(item.optString("navigationMode")),
                    fontSizeSp = item.optInt("fontSizeSp", ReaderPreferences().fontSizeSp),
                    lineSpacing = item.optDouble("lineSpacing", ReaderPreferences().lineSpacing.toDouble())
                        .toFloat(),
                    keepScreenOn = item.optBoolean("keepScreenOn", ReaderPreferences().keepScreenOn),
                    volumeButtonsTurnPages = item.optBoolean(
                        "volumeButtonsTurnPages",
                        ReaderPreferences().volumeButtonsTurnPages,
                    ),
                    invertVolumeButtons = item.optBoolean(
                        "invertVolumeButtons",
                        ReaderPreferences().invertVolumeButtons,
                    ),
                    readingRuler = item.optBoolean(
                        "readingRuler",
                        item.optDouble("readingRulerStrength", 0.0) > 0.0,
                    ),
                    readingRulerPosition = item.optDouble(
                        "readingRulerPosition",
                        item.optDouble("readingRulerStrength", ReaderRulerPositionDefault.toDouble()),
                    ).toFloat(),
                ),
            )
        }.getOrDefault(ReaderPreferences())
    }

    fun savePreferences(context: Context, preferences: ReaderPreferences) {
        val payload = JSONObject().apply {
            put("themeMode", preferences.themeMode.name)
            put("fontChoice", preferences.fontChoice.name)
            put("languageMode", preferences.languageMode.name)
            put("orientationMode", preferences.orientationMode.name)
            put("navigationMode", preferences.navigationMode.name)
            put("fontSizeSp", preferences.fontSizeSp)
            put("lineSpacing", preferences.lineSpacing.toDouble())
            put("keepScreenOn", preferences.keepScreenOn)
            put("volumeButtonsTurnPages", preferences.volumeButtonsTurnPages)
            put("invertVolumeButtons", preferences.invertVolumeButtons)
            put("readingRuler", preferences.readingRuler)
            put("readingRulerPosition", preferences.readingRulerPosition.toDouble())
        }

        prefs(context)
            .edit()
            .putString(KEY_READER_PREFERENCES, payload.toString())
            .apply()
    }

    fun loadBookStatuses(context: Context): Map<String, BookStatus> {
        val payload = prefs(context)
            .getString(KEY_BOOK_STATUSES, null)
            ?: return emptyMap()

        return runCatching {
            buildMap {
                val item = JSONObject(payload)
                item.keys().forEach { uriString ->
                    if (uriString.isBlank()) {
                        return@forEach
                    }
                    bookStatusFromName(item.optString(uriString))?.let { status ->
                        put(uriString, status)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveBookStatuses(
        context: Context,
        statusesByUri: Map<String, BookStatus>,
    ) {
        val payload = JSONObject()
        statusesByUri
            .toSortedMap()
            .forEach { (uriString, status) ->
                if (uriString.isNotBlank()) {
                    payload.put(uriString, status.name)
                }
            }

        prefs(context)
            .edit()
            .putString(KEY_BOOK_STATUSES, payload.toString())
            .apply()
    }

    fun loadNoteDocumentIds(context: Context): Set<String> {
        val payload = prefs(context)
            .getString(KEY_NOTE_DOCUMENT_IDS, null)
            ?: return emptySet()

        return runCatching {
            buildSet {
                val items = JSONArray(payload)
                repeat(items.length()) { index ->
                    val id = items.optString(index)
                    if (id.isNotBlank()) {
                        add(id)
                    }
                }
            }
        }.getOrDefault(emptySet())
    }

    fun saveNoteDocumentIds(
        context: Context,
        noteDocumentIds: Set<String>,
    ) {
        val payload = JSONArray()
        noteDocumentIds.sorted().forEach { id ->
            if (id.isNotBlank()) {
                payload.put(id)
            }
        }

        prefs(context)
            .edit()
            .putString(KEY_NOTE_DOCUMENT_IDS, payload.toString())
            .apply()
    }

    fun loadBookNoteLinks(context: Context): Map<String, BookNoteLink> {
        val payload = prefs(context)
            .getString(KEY_BOOK_NOTE_LINKS, null)
            ?: return emptyMap()

        return runCatching {
            buildMap {
                val item = JSONObject(payload)
                item.keys().forEach { bookUriString ->
                    val link = item.optJSONObject(bookUriString) ?: return@forEach
                    val noteUriString = link.optString("noteUri")
                    if (bookUriString.isNotBlank() && noteUriString.isNotBlank()) {
                        put(
                            bookUriString,
                            BookNoteLink(
                                bookUriString = bookUriString,
                                noteUriString = noteUriString,
                                noteTitle = link.optString("noteTitle").ifBlank { "Book Note" },
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveBookNoteLinks(
        context: Context,
        linksByBookUri: Map<String, BookNoteLink>,
    ) {
        val payload = JSONObject()
        linksByBookUri
            .toSortedMap()
            .forEach { (bookUriString, link) ->
                if (bookUriString.isNotBlank() && link.noteUriString.isNotBlank()) {
                    payload.put(
                        bookUriString,
                        JSONObject().apply {
                            put("noteUri", link.noteUriString)
                            put("noteTitle", link.noteTitle)
                        },
                    )
                }
            }

        prefs(context)
            .edit()
            .putString(KEY_BOOK_NOTE_LINKS, payload.toString())
            .apply()
    }

    fun loadLastNoteSections(context: Context): Map<String, String> {
        val payload = prefs(context)
            .getString(KEY_NOTE_LAST_SECTIONS, null)
            ?: return emptyMap()

        return runCatching {
            buildMap {
                val item = JSONObject(payload)
                item.keys().forEach { noteUriString ->
                    val sectionTitle = item.optString(noteUriString)
                    if (noteUriString.isNotBlank() && sectionTitle.isNotBlank()) {
                        put(noteUriString, sectionTitle)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveLastNoteSections(
        context: Context,
        lastSectionsByNoteUri: Map<String, String>,
    ) {
        val payload = JSONObject()
        lastSectionsByNoteUri
            .toSortedMap()
            .forEach { (noteUriString, sectionTitle) ->
                if (noteUriString.isNotBlank() && sectionTitle.isNotBlank()) {
                    payload.put(noteUriString, sectionTitle)
                }
            }

        prefs(context)
            .edit()
            .putString(KEY_NOTE_LAST_SECTIONS, payload.toString())
            .apply()
    }

    fun loadProgress(context: Context): Map<String, ReadingProgress> {
        val payload = prefs(context)
            .getString(KEY_READING_PROGRESS, null)
            ?: return emptyMap()

        return runCatching {
            buildMap {
                val items = JSONArray(payload)
                repeat(items.length()) { index ->
                    val item = items.optJSONObject(index) ?: return@repeat
                    val uriString = item.optString("uri")
                    if (uriString.isBlank()) {
                        return@repeat
                    }

                    val type = DocumentType.entries.firstOrNull { it.name == item.optString("type") } ?: return@repeat
                    put(
                        uriString,
                        ReadingProgress(
                            uriString = uriString,
                            type = type,
                            epubChapterIndex = item.optInt("epubChapterIndex").coerceAtLeast(0),
                            epubChapterCount = item.optInt("epubChapterCount").coerceAtLeast(0),
                            epubScrollFraction = item.optDouble("epubScrollFraction", 0.0).toFloat().coerceIn(0f, 1f),
                            pdfPageIndex = item.optInt("pdfPageIndex").coerceAtLeast(0),
                            pdfPageCount = item.optInt("pdfPageCount").coerceAtLeast(0),
                            pdfZoomScale = item.optDouble("pdfZoomScale", 1.0).toFloat().coerceIn(1f, 5f),
                            txtScrollFraction = item.optDouble("txtScrollFraction", 0.0).toFloat().coerceIn(0f, 1f),
                            updatedAt = item.optLong("updatedAt", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveProgress(
        context: Context,
        progressByUri: Map<String, ReadingProgress>,
    ) {
        val payload = JSONArray()
        progressByUri.values
            .sortedByDescending { it.updatedAt }
            .forEach { progress ->
                payload.put(
                    JSONObject().apply {
                        put("uri", progress.uriString)
                        put("type", progress.type.name)
                        put("epubChapterIndex", progress.epubChapterIndex)
                        put("epubChapterCount", progress.epubChapterCount)
                        put("epubScrollFraction", progress.epubScrollFraction)
                        put("pdfPageIndex", progress.pdfPageIndex)
                        put("pdfPageCount", progress.pdfPageCount)
                        put("pdfZoomScale", progress.pdfZoomScale)
                        put("txtScrollFraction", progress.txtScrollFraction)
                        put("updatedAt", progress.updatedAt)
                    },
                )
            }

        prefs(context)
            .edit()
            .putString(KEY_READING_PROGRESS, payload.toString())
            .apply()
    }

    fun loadLibraryRoots(context: Context): List<LibraryRoot> {
        val payload = prefs(context)
            .getString(KEY_LIBRARY_ROOTS, null)
            ?: return emptyList()

        return runCatching {
            buildList {
                val items = JSONArray(payload)
                repeat(items.length()) { index ->
                    val item = items.optJSONObject(index) ?: return@repeat
                    val treeUriString = item.optString("treeUri")
                    val name = item.optString("name")
                    if (treeUriString.isBlank() || name.isBlank()) {
                        return@repeat
                    }

                    add(
                        LibraryRoot(
                            treeUriString = treeUriString,
                            name = name,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveLibraryRoots(
        context: Context,
        roots: List<LibraryRoot>,
    ) {
        val payload = JSONArray()
        roots.forEach { root ->
            payload.put(
                JSONObject().apply {
                    put("treeUri", root.treeUriString)
                    put("name", root.name)
                },
            )
        }

        prefs(context)
            .edit()
            .putString(KEY_LIBRARY_ROOTS, payload.toString())
            .apply()
    }

    fun loadLibrarySortMode(context: Context): LibrarySortMode {
        val savedName = prefs(context)
            .getString(KEY_LIBRARY_SORT_MODE, null)
        return LibrarySortMode.entries.firstOrNull { it.name == savedName } ?: LibrarySortMode.NAME_ASC
    }

    fun saveLibrarySortMode(
        context: Context,
        sortMode: LibrarySortMode,
    ) {
        prefs(context)
            .edit()
            .putString(KEY_LIBRARY_SORT_MODE, sortMode.name)
            .apply()
    }

    fun loadLibraryFileFilter(context: Context): LibraryFileFilter {
        val savedName = prefs(context)
            .getString(KEY_LIBRARY_FILE_FILTER, null)
        return LibraryFileFilter.entries.firstOrNull { it.name == savedName } ?: LibraryFileFilter.ALL
    }

    fun saveLibraryFileFilter(
        context: Context,
        filter: LibraryFileFilter,
    ) {
        prefs(context)
            .edit()
            .putString(KEY_LIBRARY_FILE_FILTER, filter.name)
            .apply()
    }

    fun loadHomeTabName(context: Context): String =
        prefs(context)
            .getString(KEY_HOME_TAB, null)
            ?.takeIf { it.isNotBlank() }
            ?: "Collection"

    fun saveHomeTabName(
        context: Context,
        tabName: String,
    ) {
        prefs(context)
            .edit()
            .putString(KEY_HOME_TAB, tabName)
            .apply()
    }

    fun loadPinnedLibraryItemIds(context: Context): Set<String> {
        val payload = prefs(context)
            .getString(KEY_PINNED_LIBRARY_ITEMS, null)
            ?: return emptySet()

        return runCatching {
            buildSet {
                val items = JSONArray(payload)
                repeat(items.length()) { index ->
                    val id = items.optString(index)
                    if (id.isNotBlank()) {
                        add(id)
                    }
                }
            }
        }.getOrDefault(emptySet())
    }

    fun savePinnedLibraryItemIds(
        context: Context,
        itemIds: Set<String>,
    ) {
        val payload = JSONArray()
        itemIds.sorted().forEach { id ->
            payload.put(id)
        }

        prefs(context)
            .edit()
            .putString(KEY_PINNED_LIBRARY_ITEMS, payload.toString())
            .apply()
    }

    fun loadLibraryAddedAt(context: Context): Map<String, Long> {
        val payload = prefs(context)
            .getString(KEY_LIBRARY_ADDED_AT, null)
            ?: return emptyMap()

        return runCatching {
            buildMap {
                val item = JSONObject(payload)
                item.keys().forEach { id ->
                    val timestamp = item.optLong(id, 0L)
                    if (id.isNotBlank() && timestamp > 0L) {
                        put(id, timestamp)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveLibraryAddedAt(
        context: Context,
        addedAtById: Map<String, Long>,
    ) {
        val payload = JSONObject()
        addedAtById.forEach { (id, timestamp) ->
            if (id.isNotBlank() && timestamp > 0L) {
                payload.put(id, timestamp)
            }
        }

        prefs(context)
            .edit()
            .putString(KEY_LIBRARY_ADDED_AT, payload.toString())
            .apply()
    }

    fun loadBookmarks(context: Context): List<ReadingBookmark> {
        val payload = prefs(context)
            .getString(KEY_BOOKMARKS, null)
            ?: return emptyList()

        return runCatching {
            buildList {
                val items = JSONArray(payload)
                repeat(items.length()) { index ->
                    val item = items.optJSONObject(index) ?: return@repeat
                    val id = item.optString("id")
                    val uriString = item.optString("uri")
                    val title = item.optString("title")
                    val type = DocumentType.entries.firstOrNull { it.name == item.optString("type") }
                        ?: return@repeat
                    if (id.isBlank() || uriString.isBlank() || title.isBlank()) {
                        return@repeat
                    }

                    add(
                        ReadingBookmark(
                            id = id,
                            uriString = uriString,
                            title = title,
                            type = type,
                            positionLabel = item.optString("positionLabel").ifBlank { type.name },
                            epubChapterIndex = item.optInt("epubChapterIndex").coerceAtLeast(0),
                            epubChapterCount = item.optInt("epubChapterCount").coerceAtLeast(0),
                            epubChapterTitle = item.optString("epubChapterTitle"),
                            epubScrollFraction = item.optDouble("epubScrollFraction", 0.0).toFloat().coerceIn(0f, 1f),
                            pdfPageIndex = item.optInt("pdfPageIndex").coerceAtLeast(0),
                            pdfPageCount = item.optInt("pdfPageCount").coerceAtLeast(0),
                            txtScrollFraction = item.optDouble("txtScrollFraction", 0.0).toFloat().coerceIn(0f, 1f),
                            createdAt = item.optLong("createdAt", 0L),
                            updatedAt = item.optLong("updatedAt", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveBookmarks(
        context: Context,
        bookmarks: List<ReadingBookmark>,
    ) {
        val payload = JSONArray()
        bookmarks.forEach { bookmark ->
            payload.put(
                JSONObject().apply {
                    put("id", bookmark.id)
                    put("uri", bookmark.uriString)
                    put("title", bookmark.title)
                    put("type", bookmark.type.name)
                    put("positionLabel", bookmark.positionLabel)
                    put("epubChapterIndex", bookmark.epubChapterIndex)
                    put("epubChapterCount", bookmark.epubChapterCount)
                    put("epubChapterTitle", bookmark.epubChapterTitle)
                    put("epubScrollFraction", bookmark.epubScrollFraction)
                    put("pdfPageIndex", bookmark.pdfPageIndex)
                    put("pdfPageCount", bookmark.pdfPageCount)
                    put("txtScrollFraction", bookmark.txtScrollFraction)
                    put("createdAt", bookmark.createdAt)
                    put("updatedAt", bookmark.updatedAt)
                },
            )
        }

        prefs(context)
            .edit()
            .putString(KEY_BOOKMARKS, payload.toString())
            .apply()
    }
}
