package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.files.FileHistory
import dev.icode.mobile.data.files.FilePlaces
import dev.icode.mobile.data.runtime.GuestPaths
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.notices.NoticeTone
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.FileWords
import java.io.File

/**
 * New files and folders (S3), from the Explorer, the editor's tab menu and the file explorer, and an
 * item's rename — in a workspace and on phone storage alike (docs/storage-explorer-plan.md §4). The
 * sheet that asked is answered by request id; each change is kept to undo, and followed by
 * [follower]. A whole workspace is renamed by the [lifecycle]. Everything else an explorer does to
 * its files is the FileOperationsCoordinator's.
 */
internal class WorkspaceItemsCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val source: WorkspaceFileSource,
    private val documents: DocumentSessionCoordinator,
    private val usage: WorkspaceUsage,
    private val lifecycle: WorkspaceLifecycleCoordinator,
    private val places: FilePlaces,
    /** Links followed as the runtime follows them. */
    private val paths: GuestPaths,
    private val follower: FileChangeFollower,
    private val history: FileHistory,
    private val activeRoot: () -> String?,
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
        runCatching { places.of(parent).create(parent, name, isFolder = false).path }
            .onSuccess { location ->
                made(location, name, folder = false)
                if (intent.requestId != null) stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, location))
                if (root == activeRoot()) documents.open(location)
            }
            .onFailure { error -> failed(intent.requestId, "Unable to create file", error) }
    }

    /** A new folder in any workspace's folder, or on phone storage. */
    suspend fun createFolder(intent: ICodeIntent.CreateWorkspaceFolder) {
        creationRoot(intent.parentLocation, intent.expectedWorkspace, intent.requestId) ?: return
        val name = intent.name.trim()
        WorkspaceNameRules.workspaceFolderValidationError(name)?.let { return fail(intent.requestId, it) }
        runCatching { places.of(intent.parentLocation).create(intent.parentLocation, name, isFolder = true).path }
            .onSuccess { location ->
                made(location, name, folder = true)
                stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, location))
            }
            .onFailure { error -> failed(intent.requestId, "Unable to create folder", error) }
    }

    /**
     * The workspace [parent] is in, when something may be made there: inside a workspace, outside
     * `.git`, and — when the sheet named one — in the workspace it was opened for. On phone storage,
     * its top, once it may be changed.
     */
    private suspend fun creationRoot(parent: String, expectedWorkspace: String?, requestId: String?): String? {
        if (places.storage.contains(parent)) return places.storage.root.path.takeIf { accessRefusal(parent, requestId) == null }
        val root = source.workspaceOf(parent)
        return when {
            root == null || !isCreationParent(root, parent) -> null.also { fail(requestId, "Choose a folder inside a workspace") }
            expectedWorkspace != null && expectedWorkspace != root -> null.also { fail(requestId, "The workspace changed. Choose the folder again") }
            else -> root
        }
    }

    /** An item renamed in its folder; a whole workspace by the [lifecycle], its settings following. */
    suspend fun rename(intent: ICodeIntent.RenameWorkspaceItem) {
        if (follower.isWorkspace(intent.location)) return lifecycle.rename(intent.location, intent.newName, intent.requestId)
        if (!places.storage.contains(intent.location) && source.workspaceOf(intent.location) == null) return fail(intent.requestId, "It is not in a workspace")
        accessRefusal(intent.location, intent.requestId)?.let { return }
        val name = intent.newName.trim()
        val nameError = if (File(intent.location).isDirectory) WorkspaceNameRules.workspaceFolderValidationError(name)
        else WorkspaceNameRules.fileValidationError(name)
        nameError?.let { return fail(intent.requestId, it) }
        usage.holding(intent.location, whole = false)?.let { return fail(intent.requestId, it) }
        runCatching { places.of(intent.location).rename(intent.location, name).path }
            .onSuccess { target ->
                follower.moved(intent.location, target, reopen = null)
                history.record(FileWords.renamed(File(intent.location).name, name).copy(steps = listOf(FileStep.Moved(intent.location, target))))
                follower.refresh(listOf(intent.location, target))
                stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, target))
            }
            .onFailure { error -> failed(intent.requestId, "Unable to rename", error) }
    }

    /** What follows a new file or folder: kept to undo, and shown where it was made. */
    private suspend fun made(location: String, name: String, folder: Boolean) {
        history.record(FileWords.made(name, folder).copy(steps = listOf(FileStep.Made(location))))
        follower.created(location)
    }

    /** Inside the workspace and outside `.git`, which belongs to Git alone. */
    private fun isCreationParent(root: String, parent: String): Boolean =
        paths.within(root, parent)?.none { it.toString() == ".git" } == true

    /** Phone storage can be changed only with All Files Access: why not now, told to the sheet; null when it can. */
    private suspend fun accessRefusal(location: String, requestId: String?): String? =
        places.accessRefusal(listOf(location))?.also { fail(requestId, it) }

    /** What was refused: to the sheet that asked, by [requestId], else on the bell. */
    private suspend fun fail(requestId: String?, message: String) =
        if (requestId == null) stateHolder.notify(NoticeTone.HeadsUp, message) else stateHolder.emit(ICodeEffect.WorkspaceItemFailed(requestId, message))

    /** What failed: to the sheet that asked, in the error's words, else on the bell as [fallback]. */
    private suspend fun failed(requestId: String?, fallback: String, error: Throwable) = stateHolder.failureText("workspace", fallback, error).let { words ->
        if (requestId == null) stateHolder.failed(fallback, words) else stateHolder.emit(ICodeEffect.WorkspaceItemFailed(requestId, words))
    }
}
