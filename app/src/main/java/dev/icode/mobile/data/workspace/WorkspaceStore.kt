package dev.icode.mobile.data.workspace

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class StoredWorkspace(
    val name: String,
    val location: String,
    val displayPath: String,
    val lastOpenedAt: Long,
)

internal data class StoredRecentDocument(
    val name: String,
    val location: String,
    val workspaceLocation: String,
    val lastOpenedAt: Long,
)

/** Most-recent-first, bounded per workspace and across all workspaces. */
internal fun touchRecentDocument(
    current: List<StoredRecentDocument>,
    touched: StoredRecentDocument,
): List<StoredRecentDocument> {
    val counts = mutableMapOf<String, Int>()
    return (listOf(touched) + current.filterNot { it.location == touched.location && it.workspaceLocation == touched.workspaceLocation })
        .filter { item ->
            val count = counts.getOrDefault(item.workspaceLocation, 0)
            if (count >= 20) false else {
                counts[item.workspaceLocation] = count + 1
                true
            }
        }
        .take(100)
}

internal interface WorkspaceStore {
    fun load(): List<StoredWorkspace>
    fun touch(workspace: StoredWorkspace)
    /** Removes one launcher history entry. This never touches the directory. */
    fun remove(location: String)
    fun loadRecentDocuments(workspaceLocation: String): List<StoredRecentDocument>
    fun touchRecentDocument(document: StoredRecentDocument)
}

internal class SharedPreferencesWorkspaceStore(context: Context) : WorkspaceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): List<StoredWorkspace> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RECENTS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    StoredWorkspace(
                        name = item.getString("name"),
                        location = item.getString("location"),
                        displayPath = item.getString("displayPath"),
                        lastOpenedAt = item.getLong("lastOpenedAt"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    override fun touch(workspace: StoredWorkspace) {
        val updated = buildList {
            add(workspace)
            addAll(load().filterNot { it.location == workspace.location })
        }.take(MAX_RECENTS)
        write(updated)
    }

    override fun remove(location: String) {
        val current = load()
        if (current.none { it.location == location }) return
        write(current.filterNot { it.location == location }, synchronous = true)
    }

    override fun loadRecentDocuments(workspaceLocation: String): List<StoredRecentDocument> =
        loadAllRecentDocuments().filter { it.workspaceLocation == workspaceLocation }

    override fun touchRecentDocument(document: StoredRecentDocument) {
        val updated = touchRecentDocument(loadAllRecentDocuments(), document)
        val array = JSONArray()
        updated.forEach { item ->
            array.put(
                JSONObject()
                    .put("name", item.name)
                    .put("location", item.location)
                    .put("workspaceLocation", item.workspaceLocation)
                    .put("lastOpenedAt", item.lastOpenedAt),
            )
        }
        check(preferences.edit().putString(KEY_RECENT_DOCUMENTS, array.toString()).commit()) {
            "Unable to save recent files"
        }
    }

    private fun loadAllRecentDocuments(): List<StoredRecentDocument> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RECENT_DOCUMENTS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    StoredRecentDocument(
                        name = item.getString("name"),
                        location = item.getString("location"),
                        workspaceLocation = item.getString("workspaceLocation"),
                        lastOpenedAt = item.getLong("lastOpenedAt"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun write(workspaces: List<StoredWorkspace>, synchronous: Boolean = false) {
        val array = JSONArray()
        workspaces.forEach { item ->
            array.put(
                JSONObject()
                    .put("name", item.name)
                    .put("location", item.location)
                    .put("displayPath", item.displayPath)
                    .put("lastOpenedAt", item.lastOpenedAt),
            )
        }
        val edit = preferences.edit().putString(KEY_RECENTS, array.toString())
        if (synchronous) {
            check(edit.commit()) { "Unable to save recent workspaces" }
        } else {
            edit.apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "workspace_history"
        const val KEY_RECENTS = "recent_workspaces_v1"
        const val KEY_RECENT_DOCUMENTS = "recent_documents_v1"
        const val MAX_RECENTS = 20
    }
}
