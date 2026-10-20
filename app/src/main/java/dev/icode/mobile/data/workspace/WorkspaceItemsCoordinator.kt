package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.ItemClipboard
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.WorkspaceNameRules
import java.io.File

/**
 * New files and folders (S3), from the Explorer, the editor's tab menu and the file explorer; their
 * rename and delete; and the one clipboard (docs/workspaces-page-plan.md §2.7) — cut or copy
 * anywhere, paste into a folder or as a workspace. Each works in a workspace and on phone storage
 * alike (docs/storage-explorer-plan.md §4); between the two places a paste is [transfers]'. The
 * dialog that asked is answered by request id. Copy and delete can take long: they run as tasks,
 * with their own dialog, off the mutation lock. What may change now is [WorkspaceUsage]'s answer,
 * and a workspace that stops being one is released and retired by the [lifecycle], as a delete is.
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
    private val storage: PhoneStorage,
    private val transfers: StorageTransfers,
    private val activeRoot: () -> String?,
    /** Tree rows, Git status and recents after the worktree changed. */
    private val refreshWorkspaceState: suspend (root: String) -> Unit,
    /** Quick Open's index is out of date once a file is added. */
    private val reindex: suspend (root: String) -> Unit,
) {
    /**
     * A new file in any workspace's folder or on phone storage — the Explorer's, the editor's tab
     * menu's, the file explorer's. Made in the open workspace, it opens in the editor.
     */
    suspend fun createFile(intent: ICodeIntent.CreateFile) {
        val parent = intent.parentLocation ?: activeRoot() ?: return fail(intent.requestId, "Open a workspace before creating a file")
        val root = creationRoot(parent, intent.expectedWorkspace, intent.requestId) ?: return
        val name = intent.name.trim()
        WorkspaceNameRules.fileValidationError(name)?.let { return fail(intent.requestId, it) }
        runCatching {
            if (root == storage.root.path) shared().create(parent, name, isFolder = false).path else source.createFile(parent, name)
        }.onSuccess { location ->
            refreshAfterCreate(root, parent, "File", updateIndex = true)
            if (intent.requestId != null) stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, location))
            if (root == activeRoot()) documents.open(location)
        }.onFailure { error -> failed(intent.requestId, "Unable to create file", error) }
    }

    /** A new folder in any workspace's folder, or on phone storage. */
    suspend fun createFolder(intent: ICodeIntent.CreateWorkspaceFolder) {
        val root = creationRoot(intent.parentLocation, intent.expectedWorkspace, intent.requestId) ?: return
        val name = intent.name.trim()
        WorkspaceNameRules.workspaceFolderValidationError(name)?.let { return fail(intent.requestId, it) }
        runCatching {
            if (root == storage.root.path) shared().create(intent.parentLocation, name, isFolder = true).path
            else source.createDirectory(intent.parentLocation, name)
        }.onSuccess { location ->
            refreshAfterCreate(root, intent.parentLocation, "Folder", updateIndex = false)
            stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, location))
        }.onFailure { error -> failed(intent.requestId, "Unable to create folder", error) }
    }

    /**
     * The workspace [parent] is in, when something may be made there: inside a workspace, outside
     * `.git`, and — when the dialog named one — in the workspace it was opened for. On phone storage,
     * its top, once it may be changed.
     */
    private suspend fun creationRoot(parent: String, expectedWorkspace: String?, requestId: String?): String? {
        if (storage.contains(parent)) return storage.root.path.takeIf { sharedRefusal(requestId) == null }
        val root = source.workspaceOf(parent)
        return when {
            root == null || !isCreationParent(root, parent) -> null.also { fail(requestId, "Choose a folder inside a workspace") }
            expectedWorkspace != null && expectedWorkspace != root -> null.also { fail(requestId, "The workspace changed. Choose the folder again") }
            else -> root
        }
    }

    suspend fun rename(intent: ICodeIntent.RenameWorkspaceItem) {
        val files = filesOf(intent.location, intent.requestId) ?: return
        val name = intent.newName.trim()
        val nameError = if (File(intent.location).isDirectory) WorkspaceNameRules.workspaceFolderValidationError(name)
        else WorkspaceNameRules.fileValidationError(name)
        nameError?.let { return fail(intent.requestId, it) }
        usage.holding(intent.location, whole = false)?.let { return fail(intent.requestId, it) }
        runCatching { files.rename(intent.location, name) }
            .onSuccess { target ->
                moved(intent.location, target.path)
                stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, target.path))
            }
            .onFailure { error -> failed(intent.requestId, "Unable to rename", error) }
    }

    suspend fun clipboard(intent: ICodeIntent.ClipboardIntent) = when (intent) {
        is ICodeIntent.CutItem -> hold(intent.location, cut = true)
        is ICodeIntent.CopyItem -> hold(intent.location, cut = false)
        is ICodeIntent.PasteInto -> paste(intent.folder)
        ICodeIntent.PasteAsWorkspace -> paste(source.workspacesRoot.path)
        ICodeIntent.ClearClipboard -> stateHolder.update { it.copy(clipboard = null) }
    }

    /** Held for Paste; nothing moves yet. */
    private suspend fun hold(location: String, cut: Boolean) {
        val file = File(location)
        if (!file.exists()) return stateHolder.notify(NoticeTone.HeadsUp, "${file.name} no longer exists")
        val item = ItemClipboard(location, file.name, cut, folder = file.isDirectory, workspace = source.isWorkspace(location))
        stateHolder.update { it.copy(clipboard = item) }
    }

    /**
     * The clipboard's item into [destination]: a folder, or the workspaces folder, where it becomes
     * a workspace. In one place a cut moves in one step and leaves the clipboard; a copy is a task
     * that may be cancelled, and stays on the clipboard to paste again.
     */
    private suspend fun paste(destination: String) {
        val held = stateHolder.current.clipboard ?: return
        val asWorkspace = destination == source.workspacesRoot.path
        refusal(held, destination, asWorkspace)?.let { return stateHolder.notify(NoticeTone.HeadsUp, it) }
        if (transfers.crosses(held.location, destination)) return pasteAcross(held, destination, asWorkspace)
        val files = if (storage.contains(destination)) shared() else WorkspaceFiles(source.workspacesRoot)
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

    /**
     * Between iCode's storage and phone storage: a task that copies, and for a cut then deletes the
     * source ([StorageTransfers]); what follows a move in one place follows it once all was carried.
     */
    private suspend fun pasteAcross(held: ItemClipboard, destination: String, asWorkspace: Boolean) {
        if (held.cut && held.workspace) lifecycle.release(held.location)
        transfers.paste(held, destination) { target ->
            if (target != null && held.cut) {
                stateHolder.update { it.copy(clipboard = null) }
                if (held.workspace) lifecycle.retire(held.location) else moved(held.location, target.path)
            }
            if (asWorkspace) library.refresh()
            refreshOpen(destination)
        }
    }

    /** Why the clipboard's item cannot go into [destination] now; null when it can. */
    private suspend fun refusal(held: ItemClipboard, destination: String, asWorkspace: Boolean): String? {
        if ((storage.contains(held.location) || storage.contains(destination)) && !storage.hasAccess()) return NO_ACCESS
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

    /**
     * Asked and confirmed in the Explorer first: this cannot be undone. As the reference file manager:
     * counted first, then deleted with the item in hand shown, and no Cancel — a delete stopped midway leaves half a folder.
     */
    suspend fun delete(intent: ICodeIntent.DeleteWorkspaceItem) {
        val files = filesOf(intent.location, requestId = null) ?: return
        val root = source.workspaceOf(intent.location)
        usage.holding(intent.location, whole = false)?.let { return stateHolder.notify(NoticeTone.HeadsUp, it) }
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
                if (failure == null && root != null) {
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
        val home = source.workspaceOf(from) ?: return refreshOpen(from, to)
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
        }.onFailure { error -> stateHolder.report("workspace", "The change was made, but the workspace could not refresh", error) }
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

    /**
     * [location]'s place, to change it in: its workspace's files, or phone storage's once it may be
     * changed; null — and the dialog told — when it is in neither.
     */
    private suspend fun filesOf(location: String, requestId: String?): WorkspaceFiles? {
        if (storage.contains(location)) return shared().takeIf { sharedRefusal(requestId) == null }
        val root = source.workspaceOf(location) ?: return null.also { fail(requestId, "It is not in a workspace") }
        return WorkspaceFiles(File(root))
    }

    /** Phone storage can be changed only with All Files Access: why not now, told to the dialog; null when it can. */
    private suspend fun sharedRefusal(requestId: String?): String? =
        NO_ACCESS.takeUnless { storage.hasAccess() }?.also { fail(requestId, it) }

    /** Phone storage's files: a `.git` there is the user's data like any other. */
    private fun shared() = WorkspaceFiles(storage.root, gitOwnsDotGit = false)

    /** What was refused: to the dialog that asked, by [requestId], else on the bell. */
    private suspend fun fail(requestId: String?, message: String) =
        if (requestId == null) stateHolder.notify(NoticeTone.HeadsUp, message) else stateHolder.emit(ICodeEffect.WorkspaceItemFailed(requestId, message))

    /** What failed: to the dialog that asked, in the error's words, else on the bell as [fallback]. */
    private suspend fun failed(requestId: String?, fallback: String, error: Throwable) = stateHolder.failureText("workspace", fallback, error).let { words ->
        if (requestId == null) stateHolder.failed(fallback, words) else stateHolder.emit(ICodeEffect.WorkspaceItemFailed(requestId, words))
    }

    private companion object {
        const val NO_ACCESS = "Allow access to phone storage first"
    }
}
