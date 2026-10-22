package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.FileViewer
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.WorkspaceNameRules
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Whole workspaces made, renamed and deleted (docs/workspace-storage-plan.md §4.3): New Folder,
 * and the Workspaces page's Rename and Delete. A workspace in use is not pulled from under what
 * uses it ([WorkspaceUsage]). The open workspace is closed first, and a renamed one opens again
 * with its tabs. A workspace that stops being one — deleted, or cut into another — is released
 * before and retired after, the same two steps either way.
 */
internal class WorkspaceLifecycleCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val tasks: TaskRunner,
    private val source: WorkspaceFileSource,
    private val library: WorkspaceLibraryCoordinator,
    private val session: WorkspaceSessionCoordinator,
    private val usage: WorkspaceUsage,
    /** A file opened in the editor, in the open workspace. */
    private val openFile: suspend (location: String, lineNumber: Int?, column: Int?) -> Unit,
    /** A workspace's own settings follow a rename, and go with a delete. */
    private val moveSettings: suspend (from: String, to: String) -> Unit,
    private val forgetSettings: suspend (location: String) -> Unit,
) {
    /** New Folder: an empty workspace, which appears in the list as a file manager's new folder does. */
    suspend fun create(intent: ICodeIntent.CreateWorkspace) {
        val name = intent.name.trim()
        WorkspaceNameRules.newWorkspaceError(name, ::exists)?.let { return fail(intent.requestId, it) }
        val target = File(source.workspacesRoot, name)
        runCatching {
            source.workspacesRoot.mkdirs()
            if (!target.mkdir()) throw IOException(WorkspaceNameRules.newWorkspaceError(name, ::exists) ?: "The workspace could not be created")
        }.onSuccess {
            library.refresh()
            stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, target.path))
        }.onFailure { error -> fail(intent.requestId, stateHolder.failureText("workspace", "Unable to create the workspace", error)) }
    }

    suspend fun rename(intent: ICodeIntent.RenameWorkspace) {
        val from = intent.location
        val name = intent.newName.trim()
        if (!source.isWorkspace(from)) return fail(intent.requestId, "This workspace no longer exists")
        val to = File(source.workspacesRoot, name).path
        if (to == from) return stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, from))
        WorkspaceNameRules.newWorkspaceError(name, ::exists)?.let { return fail(intent.requestId, it) }
        usage.holding(from, whole = true)?.let { return fail(intent.requestId, it) }
        val open = session.activeRoot == from
        val tabs = if (open) stateHolder.current.openDocuments.map { it.location } else emptyList()
        val selected = if (open) stateHolder.current.activeDocument?.location else null
        if (open) session.close()
        // One filesystem: the folder is renamed whole, in one step.
        val renamed = runCatching { Files.move(File(from).toPath(), File(to).toPath()) }
        if (renamed.isFailure) {
            if (open) session.openWithTabs(from, tabs, selected)
            val cause = renamed.exceptionOrNull() ?: IOException("Unable to rename the workspace")
            return fail(intent.requestId, stateHolder.failureText("workspace", "Unable to rename the workspace", cause))
        }
        runCatching { moveSettings(from, to) }
            .onFailure { error -> stateHolder.report("workspace", "The workspace's settings could not follow the rename", error) }
        library.relocate(from, to)
        stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, to))
        if (open) session.openWithTabs(to, tabs.map { it.relocated(from, to) }, selected?.relocated(from, to))
    }

    /** Confirmed on the launcher first: this cannot be undone. Runs as the delete task, without Cancel. */
    suspend fun delete(location: String) {
        if (!source.isWorkspace(location)) {
            stateHolder.notify(NoticeTone.HeadsUp, "This workspace no longer exists")
            library.refresh()
            return
        }
        usage.holding(location, whole = true)?.let { return stateHolder.notify(NoticeTone.HeadsUp, it) }
        release(location)
        val files = WorkspaceFiles(source.workspacesRoot)
        tasks.start(
            title = "Deleting…",
            subject = File(location).name,
            paths = listOf(location),
            cancellable = false,
            failure = "Unable to delete the workspace",
            work = { progress ->
                progress.counted(files.count(location))
                files.delete(location, progress::item)
            },
            finished = { failure ->
                // Part of it may be gone after a failure: show what is left.
                if (failure == null) retire(location) else library.refresh()
            },
        )
    }

    /**
     * What is at [location], shown, from wherever it was asked — Quick Open, search, the file
     * explorer, a finished clone or import. A file opens in the editor, its workspace opened for it
     * first, or in a viewer of its own, the workspace as it is (docs/file-viewers-plan.md); a folder
     * opens its workspace with it selected in the Explorer — or, in the open one, shows it. One that
     * is not there — a link to a file since moved — is said so.
     */
    suspend fun reveal(location: String, lineNumber: Int? = null, column: Int? = null) {
        val workspace = source.workspaceOf(location) ?: return
        if (!File(location).exists()) return stateHolder.notify(NoticeTone.HeadsUp, "${File(location).name} is not there")
        val file = File(location).isFile
        if (file) {
            FileViewer.of(File(location).name)?.let { return stateHolder.emit(ICodeEffect.ViewFile(location, it)) }
            if (workspace != session.activeRoot) session.open(workspace, persist = true, announce = false)
            return openFile(location, lineNumber, column)
        }
        stateHolder.update { it.copy(initialExplorerSelection = location.takeIf { it != workspace }) }
        if (workspace != session.activeRoot) return session.open(workspace, persist = true)
        session.requestRefresh(rebuildIndex = true)
        stateHolder.emit(ICodeEffect.WorkspaceOpened(workspace))
    }

    /** Before [location] stops being a workspace: closed, if it is the open one. */
    suspend fun release(location: String) {
        if (session.activeRoot == location) session.close()
    }

    /** After [location] stopped being a workspace: its settings and its history go. */
    suspend fun retire(location: String) {
        runCatching { forgetSettings(location) }
            .onFailure { error -> stateHolder.report("workspace", "The workspace's settings could not be cleared", error) }
        library.forget(location)
    }

    private fun exists(name: String) = File(source.workspacesRoot, name).exists()

    private suspend fun fail(requestId: String, message: String) =
        stateHolder.emit(ICodeEffect.WorkspaceItemFailed(requestId, message))
}
