package dev.icode.mobile.data.workspace

/** The workspace store in memory, for tests: what is kept, as the real one keeps it. */
internal class MemoryWorkspaceStore : WorkspaceStore {
    private var times = emptyMap<String, Long>()
    private var recent = emptyList<StoredRecentDocument>()
    private var favorites = emptyList<String>()
    private var session: StoredSession? = null

    override fun lastOpened() = times
    override fun touch(location: String, at: Long) { times = times + (location to at) }
    override fun relocate(from: String, to: String) {
        times[from]?.let { times = times - from + (to to it) }
        session = session?.relocatedWorkspace(from, to)
    }
    override fun forget(location: String) {
        times = times - location
        if (session?.workspaceLocation == location) session = null
    }
    override fun loadRecentDocuments() = recent
    override fun updateRecentDocuments(change: (List<StoredRecentDocument>) -> List<StoredRecentDocument>) { recent = change(recent) }
    override fun loadFavorites() = favorites
    override fun updateFavorites(change: (List<String>) -> List<String>) { favorites = change(favorites) }
    override fun loadSession() = session
    override fun saveSession(session: StoredSession?) { this.session = session }
}
