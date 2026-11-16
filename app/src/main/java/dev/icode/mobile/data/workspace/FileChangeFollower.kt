package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.files.FileFollower
import dev.icode.mobile.domain.isAtOrUnder
import dev.icode.mobile.domain.notices.WorkspaceNotices
import dev.icode.mobile.data.workspace.WorkspaceLifecycleCoordinator.Reopen
import java.io.File

/**
 * What follows a change to files in iCode's own state (docs/workspaces-and-files.md), whoever made
 * it — a rename, an operation, an undo. Inside one workspace, its tabs and tree follow a move; out
 * of it, its tabs close. A whole workspace is closed before it moves or goes, opened again where it
 * was renamed, and retired once it is no workspace any more — the [lifecycle]'s steps. Each runs
 * under the mutation lock as its change is made; [refresh] once the whole change is done.
 */
internal class FileChangeFollower(
    private val stateHolder: ICodeStateHolder,
    private val source: WorkspaceFileSource,
    private val documents: DocumentSessionCoordinator,
    private val tree: WorkspaceTree,
    private val library: WorkspaceLibraryCoordinator,
    private val lifecycle: WorkspaceLifecycleCoordinator,
    private val activeRoot: () -> String?,
    /** Git status and recents after the worktree changed. */
    private val refreshWorkspaceState: suspend (root: String) -> Unit,
    /** Quick Open's index is out of date once a file is added. */
    private val reindex: suspend (root: String) -> Unit,
) : FileFollower {
    /** [location] is a whole workspace: right under the workspaces' folder. */
    override fun isWorkspace(location: String): Boolean = source.workspaceOf(location) == location

    /** Before [location] moves or goes: a workspace open is closed. What to open again, or null. */
    override suspend fun leaving(location: String): Reopen? =
        if (isWorkspace(location)) lifecycle.release(location) else null

    /** [location] stayed where it was — its move failed: a workspace closed for it opens again. */
    override suspend fun stayed(location: String, reopen: Reopen?) = lifecycle.stayed(location, reopen)

    /** After [from] became [to]. */
    override suspend fun moved(from: String, to: String, reopen: Reopen?) {
        if (isWorkspace(from)) return if (isWorkspace(to)) lifecycle.renamed(from, to, reopen) else lifecycle.retire(from)
        val home = source.workspaceOf(from) ?: return
        if (home != source.workspaceOf(to)) return removed(from)
        documents.relocate(from, to)
        tree.relocate(from, to)
    }

    /** After [location] went: to Trash, or an empty folder removed. */
    override suspend fun removed(location: String) {
        if (isWorkspace(location)) return lifecycle.retire(location)
        if (source.workspaceOf(location) != null) documents.closeDeleted(location)
    }

    /** After a new file or folder at [location]: the explorer, and the open workspace's tree at its folder. */
    suspend fun created(location: String) {
        library.refreshFiles()
        val root = source.workspaceOf(location)?.takeIf { it == activeRoot() } ?: return
        val folder = File(location).isDirectory
        runCatching {
            tree.reloadFolder(root, File(location).parent ?: root)
            refreshWorkspaceState(root)
            if (!folder) reindex(root)
        }.onFailure { error ->
            stateHolder.tell(WorkspaceNotices.changeNotShown(stateHolder.current, root, made = location), "workspace", error)
        }
    }

    /**
     * Once a change is done: the file explorer reads its folder again, the workspaces' list when one
     * came or went, and the open workspace its tree, Git and index when the change [touched] it.
     */
    override suspend fun refresh(touched: Collection<String>) {
        if (touched.any(::isWorkspace)) library.refresh()
        library.refreshFiles()
        val active = activeRoot() ?: return
        if (touched.none { it.isAtOrUnder(active) }) return
        runCatching {
            tree.reload(active)
            refreshWorkspaceState(active)
            reindex(active)
        }.onFailure { error -> stateHolder.tell(WorkspaceNotices.changeNotShown(stateHolder.current, active), "workspace", error) }
    }

    /** Hidden files shown or not: the explorer's folder, and the open workspace's tree, read again. */
    override suspend fun relist() {
        library.refreshFiles()
        val active = activeRoot() ?: return
        runCatching {
            tree.reload(active)
            refreshWorkspaceState(active)
        }.onFailure { error -> stateHolder.tell(WorkspaceNotices.notListed(stateHolder.current, active), "workspace", error) }
    }
}
