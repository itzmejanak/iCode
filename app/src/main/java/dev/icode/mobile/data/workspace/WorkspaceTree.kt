package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.git.GitWorkspaceSnapshot
import dev.icode.mobile.domain.isAtOrUnder
import dev.icode.mobile.domain.relocated
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.GitTreeStatus
import dev.icode.mobile.domain.notices.WorkspaceNotices
import java.io.File

/**
 * The Explorer's tree of the open workspace (S3): folders read so far, which
 * are expanded, and the rows that makes — each marked with its Git status;
 * hidden files listed as the setting says.
 *
 * Its own lock keeps taps responsive outside the gateway's slow Git mutation
 * queue while still serializing two folder reads and a concurrent refresh.
 */
internal class WorkspaceTree(
    private val stateHolder: ICodeStateHolder,
    source: WorkspaceFileSource,
    private val listChildren: (String) -> List<DocumentNode> = { source.listChildren(it, stateHolder.current.explorer.settings.showHidden) },
) {
    private val treeLock = Any()
    private val childrenByParent = mutableMapOf<String, List<DocumentNode>>()
    private val expanded = mutableSetOf<String>()
    private var currentRoot: String? = null

    /** No workspace is open. */
    fun clear() = synchronized(treeLock) {
        currentRoot = null
        childrenByParent.clear()
        expanded.clear()
    }

    /** A newly opened workspace: its top level only, nothing expanded. */
    fun reset(root: String, children: List<DocumentNode>) = synchronized(treeLock) {
        currentRoot = root
        childrenByParent.clear()
        childrenByParent[root] = children
        expanded.clear()
    }

    /** The visible rows, Git status included. */
    fun rows(root: String, gitState: GitWorkspaceSnapshot): List<FileEntry> {
        val visible = synchronized(treeLock) {
            projectVisibleFiles(root, childrenByParent, expanded)
        }
        return decorate(visible, gitState)
    }

    suspend fun toggle(root: String, location: String) {
        val error = synchronized(treeLock) {
            if (root != currentRoot) return@synchronized null
            if (expanded.remove(location)) return@synchronized null
            runCatching {
                if (location !in childrenByParent) childrenByParent[location] = listChildren(location)
                expanded += location
            }.exceptionOrNull()
        }
        if (error != null) stateHolder.tell(WorkspaceNotices.folderUnread(stateHolder.current, location), "workspace", error)
        if (synchronized(treeLock) { root == currentRoot }) {
            stateHolder.update { snapshot ->
                if (snapshot.activeWorkspaceLocation != root) snapshot
                else snapshot.copy(
                    files = rows(root, GitWorkspaceSnapshot(snapshot.gitRepository, snapshot.gitChanges)),
                )
            }
        }
    }

    /**
     * Reads again the top level and every expanded folder. A folder deleted
     * meanwhile — by an agent, say — is simply no longer shown open, rather
     * than failing the refresh. Throws when one that exists cannot be read.
     */
    fun reload(root: String) = synchronized(treeLock) {
        if (root != currentRoot) return@synchronized
        expanded.removeAll { !File(it).isDirectory }
        childrenByParent.keys.retainAll { it == root || it in expanded }
        childrenByParent[root] = listChildren(root)
        expanded.toList().forEach { location ->
            childrenByParent[location] = listChildren(location)
        }
    }

    /** Reads [parent] again after something was created in it, and shows it expanded. */
    fun reloadFolder(root: String, parent: String) = synchronized(treeLock) {
        if (root != currentRoot) return@synchronized
        childrenByParent[parent] = listChildren(parent)
        if (parent != root) expanded += parent
    }

    /** Open folders stay open when they, or a folder above them, are renamed or moved. */
    fun relocate(from: String, to: String) = synchronized(treeLock) {
        val moved = expanded.filter { it.isAtOrUnder(from) }
        expanded.removeAll(moved.toSet())
        moved.forEach { expanded += it.relocated(from, to) }
    }

    private fun decorate(files: List<FileEntry>, gitState: GitWorkspaceSnapshot) =
        gitState.repository?.let { repository ->
            val statuses = gitState.changes.associate { change ->
                File(repository.workTreePath, change.path).canonicalPath to change.status
            }
            GitTreeStatus.decorate(files, statuses)
        } ?: files
}
