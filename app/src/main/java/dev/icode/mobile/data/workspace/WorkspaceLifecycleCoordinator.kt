package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchIntent
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
    private val workbench: WorkbenchStateHolder,
    private val tasks: TaskRunner,
    private val source: WorkspaceFileSource,
    private val library: WorkspaceLibraryCoordinator,
    private val session: WorkspaceSessionCoordinator,
    private val usage: WorkspaceUsage,
    /** A workspace's own settings follow a rename, and go with a delete. */
    private val moveSettings: suspend (from: String, to: String) -> Unit,
    private val forgetSettings: suspend (location: String) -> Unit,
) {
    suspend fun create(rawName: String) {
        val name = rawName.trim()
        WorkspaceNameRules.newWorkspaceError(name, ::exists)?.let { return workbench.message(it) }
        val target = File(source.workspacesRoot, name)
        runCatching {
            source.workspacesRoot.mkdirs()
            if (!target.mkdir()) throw IOException(WorkspaceNameRules.newWorkspaceError(name, ::exists) ?: "The workspace could not be created")
        }.onSuccess { session.open(target.path, persist = true) }
            .onFailure { error -> workbench.report("workspace", "Unable to create the workspace", error) }
    }

    suspend fun rename(intent: WorkbenchIntent.RenameWorkspace) {
        val from = intent.location
        val name = intent.newName.trim()
        if (!source.isWorkspace(from)) return fail(intent.requestId, "This workspace no longer exists")
        val to = File(source.workspacesRoot, name).path
        if (to == from) return workbench.emit(WorkbenchEffect.WorkspaceItemChanged(intent.requestId, from))
        WorkspaceNameRules.newWorkspaceError(name, ::exists)?.let { return fail(intent.requestId, it) }
        usage.holding(from, whole = true)?.let { return fail(intent.requestId, it) }
        val open = session.activeRoot == from
        val tabs = if (open) workbench.current.openDocuments.map { it.location } else emptyList()
        val selected = if (open) workbench.current.activeDocument?.location else null
        if (open) session.close()
        // One filesystem: the folder is renamed whole, in one step.
        val renamed = runCatching { Files.move(File(from).toPath(), File(to).toPath()) }
        if (renamed.isFailure) {
            if (open) session.openWithTabs(from, tabs, selected)
            return fail(intent.requestId, workbench.failureText("workspace", "Unable to rename the workspace", renamed.exceptionOrNull()!!))
        }
        runCatching { moveSettings(from, to) }
            .onFailure { error -> workbench.report("workspace", "The workspace's settings could not follow the rename", error) }
        library.relocate(from, to)
        workbench.emit(WorkbenchEffect.WorkspaceItemChanged(intent.requestId, to))
        if (open) session.openWithTabs(to, tabs.map { it.relocated(from, to) }, selected?.relocated(from, to))
    }

    /** Confirmed on the launcher first: this cannot be undone. Runs as the delete task, without Cancel. */
    suspend fun delete(location: String) {
        if (!source.isWorkspace(location)) {
            workbench.message("This workspace no longer exists")
            library.refresh()
            return
        }
        usage.holding(location, whole = true)?.let { return workbench.message(it) }
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

    /** Before [location] stops being a workspace: closed, if it is the open one. */
    suspend fun release(location: String) {
        if (session.activeRoot == location) session.close()
    }

    /** After [location] stopped being a workspace: its settings and its history go. */
    suspend fun retire(location: String) {
        runCatching { forgetSettings(location) }
            .onFailure { error -> workbench.report("workspace", "The workspace's settings could not be cleared", error) }
        library.forget(location)
    }

    private fun exists(name: String) = File(source.workspacesRoot, name).exists()

    private suspend fun fail(requestId: String, message: String) =
        workbench.emit(WorkbenchEffect.WorkspaceItemFailed(requestId, message))
}
