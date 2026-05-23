package app.areada.data.reader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RecentDocumentStore {
    private const val PREFERENCES_NAME = "areada_reader"
    private const val KEY_RECENTS = "recent_documents"

    fun load(context: Context): List<RecentDocument> {
        val payload = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECENTS, null)
            ?: return emptyList()

        return runCatching {
            buildList {
                val items = JSONArray(payload)
                repeat(items.length()) { index ->
                    val item = items.optJSONObject(index) ?: return@repeat
                    val typeName = item.optString("type")
                    val type = DocumentType.entries.firstOrNull { it.name == typeName } ?: return@repeat

                    add(
                        RecentDocument(
                            uriString = item.optString("uri"),
                            title = item.optString("title"),
                            type = type,
                            lastOpenedAt = item.optLong("lastOpenedAt"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, recents: List<RecentDocument>) {
        val payload = JSONArray()
        recents.forEach { recent ->
            payload.put(
                JSONObject().apply {
                    put("uri", recent.uriString)
                    put("title", recent.title)
                    put("type", recent.type.name)
                    put("lastOpenedAt", recent.lastOpenedAt)
                },
            )
        }

        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENTS, payload.toString())
            .apply()
    }
}
