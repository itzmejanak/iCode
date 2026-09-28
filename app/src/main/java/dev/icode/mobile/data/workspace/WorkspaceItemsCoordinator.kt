package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkspaceNameRules
import java.io.File

/**
 * New files and folders inside the open workspace (S3), from the Explorer and
 * the editor's tab menu. The dialog that asked is answered by request id.
 */
internal class WorkspaceItemsCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val source: DirectFileWorkspaceSource,
    private val tree: WorkspaceTree,
    private val documents: DocumentSessionCoordinator,
    private val library: WorkspaceLibraryCoordinator,
    private val activeRoot: () -> String?,
    /** Tree rows, Git status and recents after the worktree changed. */
    private val refreshWorkspaceState: suspend (root: String) -> Unit,
    /** Quick Open's index is out of date once a file is added. */
    private val reindex: suspend (root: String) -> Unit,
) {
    suspend fun createFile(intent: WorkbenchIntent.CreateFile) {
        val root = activeRoot() ?: run {
            fail(intent.requestId, "Open a workspace before creating a file")
            return
        }
        if (intent.expectedWorkspace != null && intent.expectedWorkspace != root) {
            fail(intent.requestId, "The active workspace changed. Choose the destination again")
            return
        }
        val name = intent.name.trim()
        WorkspaceNameRules.fileValidationError(name)?.let { error ->
            fail(intent.requestId, error)
            return
        }
        val parent = intent.parentLocation ?: root
        if (!isCreationParent(root, parent)) {
            fail(intent.requestId, "Choose a folder inside the active workspace")
            return
        }
        runCatching { source.createFile(parent, name) }.onSuccess { location ->
            refreshAfterCreate(root, parent, "File", updateIndex = true)
            if (intent.requestId != null) workbench.emit(WorkbenchEffect.WorkspaceItemChanged(intent.requestId, location))
            documents.open(location)
        }.onFailure { error -> fail(intent.requestId, workbench.failureText("workspace", "Unable to create file", error)) }
    }

    suspend fun createFolder(intent: WorkbenchIntent.CreateWorkspaceFolder) {
        val root = activeRoot()
        if (root == null || root != intent.expectedWorkspace) {
            fail(intent.requestId, "The active workspace changed. Choose the destination again")
            return
        }
        val name = intent.name.trim()
        WorkspaceNameRules.workspaceFolderValidationError(name)?.let { error ->
            fail(intent.requestId, error)
            return
        }
        if (!isCreationParent(root, intent.parentLocation)) {
            fail(intent.requestId, "Choose a folder inside the active workspace")
            return
        }
        runCatching { source.createDirectory(intent.parentLocation, name) }.onSuccess { location ->
            refreshAfterCreate(root, intent.parentLocation, "Folder", updateIndex = false)
            workbench.emit(WorkbenchEffect.WorkspaceItemChanged(intent.requestId, location))
        }.onFailure { error -> fail(intent.requestId, workbench.failureText("workspace", "Unable to create folder", error)) }
    }

    suspend fun rename(intent: WorkbenchIntent.RenameWorkspaceItem) {
        val root = activeRoot() ?: return fail(intent.requestId, "Open a workspace first")
        val name = intent.newName.trim()
        val nameError = if (File(intent.location).isDirectory) WorkspaceNameRules.workspaceFolderValidationError(name)
        else WorkspaceNameRules.fileValidationError(name)
        nameError?.let { return fail(intent.requestId, it) }
        blocked(intent.location)?.let { return fail(intent.requestId, it) }
        runCatching { WorkspaceFiles(File(root)).rename(intent.location, name) }
            .onSuccess { target ->
                followMove(root, intent.location, target.path)
                workbench.emit(WorkbenchEffect.WorkspaceItemChanged(intent.requestId, target.path))
            }
            .onFailure { error -> fail(intent.requestId, workbench.failureText("workspace", "Unable to rename", error)) }
    }

    /** The second half of Cut: into [WorkbenchIntent.MoveWorkspaceItem.destinationFolder]. */
    suspend fun move(intent: WorkbenchIntent.MoveWorkspaceItem) {
        val root = activeRoot() ?: return
        blocked(intent.location)?.let { return workbench.message(it) }
        runCatching { WorkspaceFiles(File(root)).move(intent.location, intent.destinationFolder) }
            .onSuccess { target -> if (target.path != intent.location) followMove(root, intent.location, target.path) }
            .onFailure { error -> workbench.report("workspace", "Unable to move", error) }
    }

    suspend fun copy(intent: WorkbenchIntent.CopyWorkspaceItem) {
        val root = activeRoot() ?: return
        runCatching { WorkspaceFiles(File(root)).copy(intent.location, intent.destinationFolder) }
            .onSuccess { refreshAfterChange(root) }
            .onFailure { error -> workbench.report("workspace", "Unable to copy", error) }
    }

    /** Asked and confirmed in the Explorer first: this cannot be undone. */
    suspend fun delete(intent: WorkbenchIntent.DeleteWorkspaceItem) {
        val root = activeRoot() ?: return
        blocked(intent.location)?.let { return workbench.message(it) }
        runCatching { WorkspaceFiles(File(root)).delete(intent.location) }
            .onSuccess {
                documents.closeDeleted(intent.location)
                library.forgetRecentDocumentsUnder(root, intent.location)
                refreshAfterChange(root)
            }
            .onFailure { error ->
                workbench.report("workspace", "Unable to delete", error)
                // Part of a folder may be gone already: show what is left.
                refreshAfterChange(root)
            }
    }

    private fun blocked(location: String): String? = documents.blockingEdits(location)?.let { document ->
        "${document.name} has unsaved edits. Save or close it first"
    }

    private suspend fun followMove(root: String, from: String, to: String) {
        documents.relocate(from, to)
        tree.relocate(from, to)
        library.relocateRecentDocuments(root, from, to)
        refreshAfterChange(root)
    }

    private suspend fun refreshAfterChange(root: String) {
        runCatching {
            tree.reload(root)
            refreshWorkspaceState(root)
            reindex(root)
        }.onFailure { error ->
            workbench.report("workspace", "The change was made, but the workspace could not refresh", error)
        }
    }

    /** Inside the workspace and outside `.git`, which belongs to Git alone. */
    private fun isCreationParent(root: String, parent: String): Boolean = runCatching {
        val rootPath = File(root).canonicalFile.toPath()
        val parentPath = File(parent).canonicalFile.toPath()
        parentPath.startsWith(rootPath) && !rootPath.relativize(parentPath).any { it.toString() == ".git" }
    }.getOrDefault(false)

    private suspend fun refreshAfterCreate(root: String, parent: String, kind: String, updateIndex: Boolean) {
        runCatching {
            tree.reloadFolder(root, parent)
            refreshWorkspaceState(root)
            if (updateIndex) reindex(root)
        }.onFailure { error ->
            workbench.report("workspace", "$kind created, but the workspace could not refresh", error)
        }
    }

    private suspend fun fail(requestId: String?, message: String) {
        if (requestId == null) workbench.message(message)
        else workbench.emit(WorkbenchEffect.WorkspaceItemFailed(requestId, message))
    }
}
