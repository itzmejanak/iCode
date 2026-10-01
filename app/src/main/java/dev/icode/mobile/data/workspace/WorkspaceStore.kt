package dev.icode.mobile.data.workspace

import android.content.Context
import dev.icode.mobile.data.io.commitOrFail
import org.json.JSONArray
import org.json.JSONObject

internal data class StoredRecentDocument(
    val name: String,
    val location: String,
    val workspaceLocation: String,
    val lastOpenedAt: Long,
)

/** What was open when iCode last ran: the workspace, its tabs in order, the one selected. */
internal data class StoredSession(
    val workspaceLocation: String,
    val documents: List<String>,
    val activeDocument: String?,
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

/** A whole workspace renamed from [from] to [to]: its recent files follow it, names kept. */
internal fun List<StoredRecentDocument>.relocatedWorkspace(from: String, to: String): List<StoredRecentDocument> = map { item ->
    if (item.workspaceLocation != from) item
    else item.copy(location = item.location.relocated(from, to), workspaceLocation = to)
}

/** The session after its workspace was renamed; unchanged when it is another workspace's. */
internal fun StoredSession.relocatedWorkspace(from: String, to: String): StoredSession =
    if (workspaceLocation != from) this
    else StoredSession(to, documents.map { it.relocated(from, to) }, activeDocument?.relocated(from, to))

/**
 * What iCode remembers about workspaces, beyond the folders themselves: when each was last opened,
 * its recent files, and the session to reopen. The folders are the list of workspaces
 * (docs/workspace-storage-plan.md §4.3); nothing here can make one appear or go.
 */
internal interface WorkspaceStore {
    /** When each workspace was last opened in iCode, by location. */
    fun lastOpened(): Map<String, Long>
    fun touch(location: String, at: Long)
    /** A workspace renamed: its last-opened time, recent files and saved session follow it. */
    fun relocate(from: String, to: String)
    /** A workspace deleted: its last-opened time, recent files and saved session go. */
    fun forget(location: String)
    fun loadRecentDocuments(workspaceLocation: String): List<StoredRecentDocument>
    fun touchRecentDocument(document: StoredRecentDocument)
    /** Rewrites every recent file entry at once: files renamed, moved or deleted in the Explorer. */
    fun updateRecentDocuments(change: (List<StoredRecentDocument>) -> List<StoredRecentDocument>)
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
        saveRecentDocuments(loadAllRecentDocuments().relocatedWorkspace(from, to))
        loadSession()?.let { session -> saveSession(session.relocatedWorkspace(from, to)) }
    }

    override fun forget(location: String) = forgetUnless { it != location }

    /** Everything kept for a workspace [keep] rejects. */
    private fun forgetUnless(keep: (workspaceLocation: String) -> Boolean) {
        writeLastOpened(lastOpened().filterKeys(keep))
        saveRecentDocuments(loadAllRecentDocuments().filter { keep(it.workspaceLocation) })
        loadSession()?.takeUnless { keep(it.workspaceLocation) }?.let { saveSession(null) }
    }

    override fun loadRecentDocuments(workspaceLocation: String): List<StoredRecentDocument> =
        loadAllRecentDocuments().filter { it.workspaceLocation == workspaceLocation }

    override fun touchRecentDocument(document: StoredRecentDocument) {
        saveRecentDocuments(touchRecentDocument(loadAllRecentDocuments(), document))
    }

    override fun updateRecentDocuments(change: (List<StoredRecentDocument>) -> List<StoredRecentDocument>) {
        saveRecentDocuments(change(loadAllRecentDocuments()))
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

    private fun saveRecentDocuments(updated: List<StoredRecentDocument>) {
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
        preferences.commitOrFail("Unable to save recent files") { putString(KEY_RECENT_DOCUMENTS, array.toString()) }
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

    private companion object {
        // Stored names; do not rename.
        const val PREFERENCES_NAME = "workspace_history"
        const val KEY_LAST_OPENED = "last_opened_v1"
        const val KEY_RECENT_DOCUMENTS = "recent_documents_v1"
        const val KEY_SESSION = "session_v1"
    }
}
