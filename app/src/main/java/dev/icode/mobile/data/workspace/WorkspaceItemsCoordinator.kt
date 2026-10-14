package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.ItemClipboard
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.WorkspaceNameRules
import java.io.File

/**
 * New files and folders inside the open workspace (S3), from the Explorer and the editor's tab
 * menu; the Explorer's rename and delete; and the one clipboard (docs/workspaces-page-plan.md
 * §2.7) — cut or copy anywhere in the workspaces, paste into a folder or as a workspace. The dialog
 * that asked is answered by request id. Copy and delete can take long: they run as tasks, with
 * their own dialog, off the mutation lock. What may change now is [WorkspaceUsage]'s answer, and a
 * workspace that stops being one is released and retired by the [lifecycle], as a delete is.
 */
internal class WorkspaceItemsCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val tasks: TaskRunner,
    private val source: WorkspaceFileSource,
    private val tree: WorkspaceTree,
    private val documents: DocumentSessionCoordinator,
    private val library: WorkspaceLibraryCoordinator,
    private val usage: WorkspaceUsage,
    private val lifecycle: WorkspaceLifecycleCoordinator,
    private val activeRoot: () -> String?,
    /** Tree rows, Git status and recents after the worktree changed. */
    private val refreshWorkspaceState: suspend (root: String) -> Unit,
    /** Quick Open's index is out of date once a file is added. */
    private val reindex: suspend (root: String) -> Unit,
) {
    /**
     * A new file in any workspace's folder — the Explorer's, the editor's tab menu's, the file
     * explorer's. Made in the open workspace, it opens in the editor, as the Explorer's always has.
     */
    suspend fun createFile(intent: ICodeIntent.CreateFile) {
        val parent = intent.parentLocation ?: activeRoot() ?: return fail(intent.requestId, "Open a workspace before creating a file")
        val root = creationRoot(parent, intent.expectedWorkspace, intent.requestId) ?: return
        val name = intent.name.trim()
        WorkspaceNameRules.fileValidationError(name)?.let { return fail(intent.requestId, it) }
        runCatching { source.createFile(parent, name) }.onSuccess { location ->
            refreshAfterCreate(root, parent, "File", updateIndex = true)
            if (intent.requestId != null) stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, location))
            if (root == activeRoot()) documents.open(location)
        }.onFailure { error -> fail(intent.requestId, stateHolder.failureText("workspace", "Unable to create file", error)) }
    }

    /** A new folder in any workspace's folder. */
    suspend fun createFolder(intent: ICodeIntent.CreateWorkspaceFolder) {
        val root = creationRoot(intent.parentLocation, intent.expectedWorkspace, intent.requestId) ?: return
        val name = intent.name.trim()
        WorkspaceNameRules.workspaceFolderValidationError(name)?.let { return fail(intent.requestId, it) }
        runCatching { source.createDirectory(intent.parentLocation, name) }.onSuccess { location ->
            refreshAfterCreate(root, intent.parentLocation, "Folder", updateIndex = false)
            stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, location))
        }.onFailure { error -> fail(intent.requestId, stateHolder.failureText("workspace", "Unable to create folder", error)) }
    }

    /**
     * The workspace [parent] is in, when something may be made there: inside a workspace, outside
     * `.git`, and — when the dialog named one — in the workspace it was opened for.
     */
    private suspend fun creationRoot(parent: String, expectedWorkspace: String?, requestId: String?): String? {
        val root = source.workspaceOf(parent)
        return when {
            root == null || !isCreationParent(root, parent) -> null.also { fail(requestId, "Choose a folder inside a workspace") }
            expectedWorkspace != null && expectedWorkspace != root -> null.also { fail(requestId, "The workspace changed. Choose the folder again") }
            else -> root
        }
    }

    suspend fun rename(intent: ICodeIntent.RenameWorkspaceItem) {
        val root = source.workspaceOf(intent.location) ?: return fail(intent.requestId, "It is not in a workspace")
        val name = intent.newName.trim()
        val nameError = if (File(intent.location).isDirectory) WorkspaceNameRules.workspaceFolderValidationError(name)
        else WorkspaceNameRules.fileValidationError(name)
        nameError?.let { return fail(intent.requestId, it) }
        usage.holding(intent.location, whole = false)?.let { return fail(intent.requestId, it) }
        runCatching { WorkspaceFiles(File(root)).rename(intent.location, name) }
            .onSuccess { target ->
                moved(intent.location, target.path)
                stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, target.path))
            }
            .onFailure { error -> fail(intent.requestId, stateHolder.failureText("workspace", "Unable to rename", error)) }
    }

    suspend fun clipboard(intent: ICodeIntent.ClipboardIntent) {
        when (intent) {
            is ICodeIntent.CutItem -> hold(intent.location, cut = true)
            is ICodeIntent.CopyItem -> hold(intent.location, cut = false)
            is ICodeIntent.PasteInto -> paste(intent.folder)
            ICodeIntent.PasteAsWorkspace -> paste(source.workspacesRoot.path)
            ICodeIntent.ClearClipboard -> stateHolder.update { it.copy(clipboard = null) }
        }
    }

    /** Held for Paste; nothing moves yet. */
    private suspend fun hold(location: String, cut: Boolean) {
        val file = File(location)
        if (!file.exists()) return stateHolder.message("${file.name} no longer exists")
        val item = ItemClipboard(location, file.name, cut, folder = file.isDirectory, workspace = source.isWorkspace(location))
        stateHolder.update { it.copy(clipboard = item) }
    }

    /**
     * The clipboard's item into [destination]: a folder of a workspace, or the workspaces folder,
     * where it becomes a workspace. A cut moves — one filesystem, one step — and leaves the
     * clipboard; a copy is a task that may be cancelled, and stays on the clipboard to paste again.
     */
    private suspend fun paste(destination: String) {
        val held = stateHolder.current.clipboard ?: return
        val asWorkspace = destination == source.workspacesRoot.path
        refusal(held, destination, asWorkspace)?.let { return stateHolder.message(it) }
        val files = WorkspaceFiles(source.workspacesRoot)
        if (held.cut) {
            if (held.workspace) lifecycle.release(held.location)
            runCatching { files.move(held.location, destination) }
                .onSuccess { target ->
                    stateHolder.update { it.copy(clipboard = null) }
                    if (held.workspace) lifecycle.retire(held.location) else moved(held.location, target.path)
                    if (asWorkspace) library.refresh()
                    refreshOpen(target.path)
                }
                .onFailure { error -> stateHolder.report("workspace", "Unable to move", error) }
            return
        }
        tasks.start(
            title = "Copying…",
            subject = held.name,
            paths = listOf(held.location),
            cancellable = true,
            failure = "Unable to copy",
            work = { progress ->
                progress.counted(files.count(held.location))
                files.copy(held.location, destination, progress::item)
            },
            // A copy that stopped removed what it made; the list and the tree show what is there.
            finished = {
                if (asWorkspace) library.refresh()
                refreshOpen(destination)
            },
        )
    }

    /** Why the clipboard's item cannot go into [destination] now; null when it can. */
    private suspend fun refusal(held: ItemClipboard, destination: String, asWorkspace: Boolean): String? {
        if (!File(held.location).exists()) return "${held.name} no longer exists"
        if (destination.isAtOrUnder(held.location)) return "${held.name} cannot go inside itself"
        if (asWorkspace) {
            held.notAWorkspace?.let { return it }
            WorkspaceNameRules.newWorkspaceError(held.name) { held.cut && File(destination, it).exists() }?.let { return it }
        } else if (held.cut && File(destination, held.name).exists()) {
            return "“${held.name}” is already there"
        }
        if (held.cut) usage.holding(held.location, whole = held.workspace)?.let { return it }
        return tasks.waitReason(held.location) ?: tasks.waitReason(destination)
    }

    /** Asked and confirmed in the Explorer first: this cannot be undone. */
    /**
     * As the reference file manager: counted first, then deleted with the item in hand shown,
     * and no Cancel — a delete stopped midway leaves half a folder.
     */
    suspend fun delete(intent: ICodeIntent.DeleteWorkspaceItem) {
        val root = source.workspaceOf(intent.location) ?: return
        usage.holding(intent.location, whole = false)?.let { return stateHolder.message(it) }
        val files = WorkspaceFiles(File(root))
        tasks.start(
            title = "Deleting…",
            subject = File(intent.location).name,
            paths = listOf(intent.location),
            cancellable = false,
            failure = "Unable to delete",
            work = { progress ->
                progress.counted(files.count(intent.location))
                files.delete(intent.location, progress::item)
            },
            finished = { failure ->
                if (failure == null) {
                    library.forgetRecentDocumentsUnder(root, intent.location)
                    documents.closeDeleted(intent.location)
                }
                // Part of a folder may be gone after a failure: show what is left.
                refreshOpen(intent.location)
            },
        )
    }

    /**
     * What follows a move: inside one workspace, its tabs, tree and recent files follow it; out of
     * a workspace, its tabs close and its recent files go. The open workspace shows either.
     */
    private suspend fun moved(from: String, to: String) {
        val home = source.workspaceOf(from) ?: return
        if (home == source.workspaceOf(to)) {
            documents.relocate(from, to)
            tree.relocate(from, to)
            library.relocateRecentDocuments(home, from, to)
        } else {
            documents.closeDeleted(from)
            library.forgetRecentDocumentsUnder(home, from)
        }
        refreshOpen(from, to)
    }

    /**
     * After a change: the file explorer reads its folder again, and the open workspace its tree,
     * Git and index when the change touched it.
     */
    private suspend fun refreshOpen(vararg touched: String) {
        library.refreshFiles()
        val active = activeRoot() ?: return
        if (touched.any { it.isAtOrUnder(active) }) refreshAfterChange(active)
    }

    private suspend fun refreshAfterChange(root: String) {
        runCatching {
            tree.reload(root)
            refreshWorkspaceState(root)
            reindex(root)
        }.onFailure { error ->
            stateHolder.report("workspace", "The change was made, but the workspace could not refresh", error)
        }
    }

    /** Inside the workspace and outside `.git`, which belongs to Git alone. */
    private fun isCreationParent(root: String, parent: String): Boolean = runCatching {
        val rootPath = File(root).canonicalFile.toPath()
        val parentPath = File(parent).canonicalFile.toPath()
        parentPath.startsWith(rootPath) && !rootPath.relativize(parentPath).any { it.toString() == ".git" }
    }.getOrDefault(false)

    private suspend fun refreshAfterCreate(root: String, parent: String, kind: String, updateIndex: Boolean) {
        library.refreshFiles()
        if (root != activeRoot()) return
        runCatching {
            tree.reloadFolder(root, parent)
            refreshWorkspaceState(root)
            if (updateIndex) reindex(root)
        }.onFailure { error ->
            stateHolder.report("workspace", "$kind created, but the workspace could not refresh", error)
        }
    }

    private suspend fun fail(requestId: String?, message: String) {
        if (requestId == null) stateHolder.message(message)
        else stateHolder.emit(ICodeEffect.WorkspaceItemFailed(requestId, message))
    }
}
