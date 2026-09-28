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
            if (intent.requestId != null) workbench.emit(WorkbenchEffect.WorkspaceItemCreated(intent.requestId, location))
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
            workbench.emit(WorkbenchEffect.WorkspaceItemCreated(intent.requestId, location))
        }.onFailure { error -> fail(intent.requestId, workbench.failureText("workspace", "Unable to create folder", error)) }
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
        else workbench.emit(WorkbenchEffect.WorkspaceItemCreationFailed(requestId, message))
    }
}
