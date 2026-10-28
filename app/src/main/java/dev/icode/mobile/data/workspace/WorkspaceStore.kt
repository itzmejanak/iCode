package dev.icode.mobile.data.workspace

import android.content.Context
import dev.icode.mobile.data.io.commitOrFail
import dev.icode.mobile.data.io.relocated
import org.json.JSONArray
import org.json.JSONObject

/** A file opened in iCode, in a workspace or on phone storage, and when. */
internal data class StoredRecentDocument(
    val location: String,
    val lastOpenedAt: Long,
)

/** What was open when iCode last ran: the workspace, its tabs in order, the one selected. */
internal data class StoredSession(
    val workspaceLocation: String,
    val documents: List<String>,
    val activeDocument: String?,
)

/**
 * [touched] first, and once: at most [RECENT_PER_GROUP] of each group — a workspace, by [groupOf], or
 * phone storage, null — so one never pushes out the others, and [RECENT_MOST] in all.
 */
internal fun touchRecentDocument(
    current: List<StoredRecentDocument>,
    touched: StoredRecentDocument,
    groupOf: (location: String) -> String?,
): List<StoredRecentDocument> {
    val counts = mutableMapOf<String?, Int>()
    return (listOf(touched) + current.filterNot { it.location == touched.location })
        .filter { item ->
            val group = groupOf(item.location)
            val count = counts.getOrDefault(group, 0)
            (count < RECENT_PER_GROUP).also { if (it) counts[group] = count + 1 }
        }
        .take(RECENT_MOST)
}

private const val RECENT_PER_GROUP = 20
private const val RECENT_MOST = 100

/** The session after its workspace was renamed; unchanged when it is another workspace's. */
internal fun StoredSession.relocatedWorkspace(from: String, to: String): StoredSession =
    if (workspaceLocation != from) this
    else StoredSession(to, documents.map { it.relocated(from, to) }, activeDocument?.relocated(from, to))

/**
 * What iCode remembers beyond the files themselves: when each workspace was last opened, and the
 * session to reopen — the folders are the list of workspaces (docs/workspace-storage-plan.md §4.3),
 * so nothing here can make one appear or go; and, app-wide, the files opened and the folders pinned
 * (docs/explorer-gaps-plan.md §3.14).
 */
internal interface WorkspaceStore {
    /** When each workspace was last opened in iCode, by location. */
    fun lastOpened(): Map<String, Long>
    fun touch(location: String, at: Long)
    /** A workspace renamed: its last-opened time and saved session follow it. */
    fun relocate(from: String, to: String)
    /** A workspace deleted: its last-opened time and saved session go. */
    fun forget(location: String)
    /** The files opened in iCode, newest first. */
    fun loadRecentDocuments(): List<StoredRecentDocument>
    /** Rewrites them all at once: one opened, files moved. */
    fun updateRecentDocuments(change: (List<StoredRecentDocument>) -> List<StoredRecentDocument>)
    /** The folders pinned to Favorites, in the order pinned. */
    fun loadFavorites(): List<String>
    fun updateFavorites(change: (List<String>) -> List<String>)
    /** Null when there is none, or what was stored cannot be read. */
    fun loadSession(): StoredSession?
    fun saveSession(session: StoredSession?)
}

internal class SharedPreferencesWorkspaceStore(context: Context) : WorkspaceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun lastOpened(): Map<String, Long> = runCatching {
        val item = JSONObject(preferences.getString(KEY_LAST_OPENED, "{}") ?: "{}")
        item.keys().asSequence().associateWith { item.getLong(it) }
    }.getOrDefault(emptyMap())

    override fun touch(location: String, at: Long) = writeLastOpened(lastOpened() + (location to at))

    override fun relocate(from: String, to: String) {
        val times = lastOpened()
        times[from]?.let { writeLastOpened(times - from + (to to it)) }
        loadSession()?.let { session -> saveSession(session.relocatedWorkspace(from, to)) }
    }

    override fun forget(location: String) {
        writeLastOpened(lastOpened() - location)
        loadSession()?.takeIf { it.workspaceLocation == location }?.let { saveSession(null) }
    }

    override fun loadRecentDocuments(): List<StoredRecentDocument> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RECENT_DOCUMENTS, "[]"))
        (0 until array.length()).map { index ->
            array.getJSONObject(index).let { StoredRecentDocument(it.getString("location"), it.getLong("lastOpenedAt")) }
        }
    }.getOrDefault(emptyList())

    override fun updateRecentDocuments(change: (List<StoredRecentDocument>) -> List<StoredRecentDocument>) {
        val array = JSONArray()
        change(loadRecentDocuments()).forEach { array.put(JSONObject().put("location", it.location).put("lastOpenedAt", it.lastOpenedAt)) }
        preferences.commitOrFail("Unable to save recent files") { putString(KEY_RECENT_DOCUMENTS, array.toString()) }
    }

    override fun loadFavorites(): List<String> = runCatching {
        JSONArray(preferences.getString(KEY_FAVORITES, "[]")).let { array -> (0 until array.length()).map(array::getString) }
    }.getOrDefault(emptyList())

    override fun updateFavorites(change: (List<String>) -> List<String>) {
        preferences.commitOrFail("Unable to save Favorites") { putString(KEY_FAVORITES, JSONArray(change(loadFavorites())).toString()) }
    }

    override fun loadSession(): StoredSession? = runCatching {
        val item = JSONObject(preferences.getString(KEY_SESSION, null) ?: return null)
        val documents = item.getJSONArray("documents")
        StoredSession(
            workspaceLocation = item.getString("workspace"),
            documents = (0 until documents.length()).map(documents::getString),
            activeDocument = if (item.isNull("active")) null else item.getString("active"),
        )
    }.getOrNull()

    override fun saveSession(session: StoredSession?) {
        val encoded = session?.let {
            JSONObject()
                .put("workspace", it.workspaceLocation)
                .put("documents", JSONArray(it.documents))
                .put("active", it.activeDocument ?: JSONObject.NULL)
                .toString()
        }
        preferences.commitOrFail("Unable to save the open workspace") { putString(KEY_SESSION, encoded) }
    }

    private fun writeLastOpened(times: Map<String, Long>) {
        val encoded = JSONObject().apply { times.forEach { (location, at) -> put(location, at) } }.toString()
        preferences.commitOrFail("Unable to save the workspace list") { putString(KEY_LAST_OPENED, encoded) }
    }

    private companion object {
        // Stored names; do not rename.
        const val PREFERENCES_NAME = "workspace_history"
        const val KEY_LAST_OPENED = "last_opened_v1"
        const val KEY_RECENT_DOCUMENTS = "recent_documents_v1"
        const val KEY_FAVORITES = "favorites_v1"
        const val KEY_SESSION = "session_v1"
    }
}
