package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.files.FileHistory
import dev.icode.mobile.domain.relocated
import dev.icode.mobile.domain.FileViewer
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.notices.WorkspaceNotices
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.FileWords
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Whole workspaces made and renamed (docs/workspaces-and-files.md): New Folder, and Rename
 * from any list. A workspace in use is not pulled from under what uses it ([WorkspaceUsage]). The
 * open workspace is closed first, and a renamed one opens again with its tabs. A workspace that
 * moves or goes — renamed, cut into another, put in Trash — is [release]d before, and [renamed] or
 * [retire]d after, the same steps whoever moves it (FileChangeFollower). Each is kept to undo.
 */
internal class WorkspaceLifecycleCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val source: WorkspaceFileSource,
    private val library: WorkspaceLibraryCoordinator,
    private val session: WorkspaceSessionCoordinator,
    private val usage: WorkspaceUsage,
    private val history: FileHistory,
    /** A file opened in the editor, in the open workspace. */
    private val openFile: suspend (location: String, lineNumber: Int?, column: Int?) -> Unit,
    /** A workspace's own settings follow a rename, and go when it is no workspace any more. */
    private val moveSettings: suspend (from: String, to: String) -> Unit,
    private val forgetSettings: suspend (location: String) -> Unit,
) {
    /** What to open again once a workspace closed for a move is back: its tabs, and the one selected. */
    class Reopen(val tabs: List<String>, val selected: String?)

    /** New Folder: an empty workspace, which appears in the list as a file manager's new folder does. */
    suspend fun create(intent: ICodeIntent.CreateWorkspace) {
        val name = intent.name.trim()
        WorkspaceNameRules.newWorkspaceError(name, ::exists)?.let { return fail(intent.requestId, it) }
        val target = File(source.workspacesRoot, name)
        runCatching {
            source.workspacesRoot.mkdirs()
            if (!target.mkdir()) throw IOException(WorkspaceNameRules.newWorkspaceError(name, ::exists) ?: "The workspace could not be created")
        }.onSuccess {
            history.record(FileWords.made(name, folder = true).copy(steps = listOf(FileStep.Made(target.path))))
            library.refresh()
            stateHolder.emit(ICodeEffect.WorkspaceItemChanged(intent.requestId, target.path))
        }.onFailure { error -> fail(intent.requestId, stateHolder.failureText("workspace", "Unable to create the workspace", error)) }
    }

    /** The workspace [from] renamed [newName], in one step on its one filesystem; the sheet that asked is answered by [requestId]. */
    suspend fun rename(from: String, newName: String, requestId: String) {
        val name = newName.trim()
        if (!source.isWorkspace(from)) return fail(requestId, "This workspace no longer exists")
        val to = File(source.workspacesRoot, name).path
        if (to == from) return stateHolder.emit(ICodeEffect.WorkspaceItemChanged(requestId, from))
        WorkspaceNameRules.newWorkspaceError(name, ::exists)?.let { return fail(requestId, it) }
        usage.holding(from, whole = true)?.let { return fail(requestId, it) }
        val reopen = release(from)
        runCatching { Files.move(File(from).toPath(), File(to).toPath()) }.exceptionOrNull()?.let { error ->
            stayed(from, reopen)
            return fail(requestId, stateHolder.failureText("workspace", "Unable to rename the workspace", error))
        }
        renamed(from, to, reopen)
        history.record(FileWords.renamed(File(from).name, name).copy(steps = listOf(FileStep.Moved(from, to))))
        stateHolder.emit(ICodeEffect.WorkspaceItemChanged(requestId, to))
    }

    /**
     * What is at [location], shown, from wherever it was asked — Quick Open, search, the file
     * explorer, a finished clone or import. A file opens in the editor, its workspace opened for it
     * first, or in a viewer of its own, the workspace as it is (docs/workspaces-and-files.md); a folder
     * opens its workspace with it selected in the Explorer — or, in the open one, shows it. One that
     * is not there — a link to a file since moved — is said so.
     */
    suspend fun reveal(location: String, lineNumber: Int? = null, column: Int? = null) {
        val workspace = source.workspaceOf(location) ?: return
        if (!File(location).exists()) return stateHolder.tell(WorkspaceNotices.notThere(stateHolder.current, location))
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

    /** Before [location] moves or goes: closed, if it is the open one. What to open again, or null. */
    suspend fun release(location: String): Reopen? {
        if (session.activeRoot != location) return null
        val state = stateHolder.current
        val reopen = Reopen(state.openDocuments.map { it.location }, state.activeDocument?.location)
        session.close()
        return reopen
    }

    /** [location] stayed where it was: opened again as it was, when it was closed for the move. */
    suspend fun stayed(location: String, reopen: Reopen?) {
        reopen?.let { session.openWithTabs(location, it.tabs, it.selected) }
    }

    /** After [from] was renamed [to] at the workspaces' top: its settings and history follow, and it opens again as it was. */
    suspend fun renamed(from: String, to: String, reopen: Reopen?) {
        runCatching { moveSettings(from, to) }
            .onFailure { error -> stateHolder.tell(WorkspaceNotices.settingsNotMoved(to), "workspace", error) }
        library.relocate(from, to)
        reopen?.let { session.openWithTabs(to, it.tabs.map { tab -> tab.relocated(from, to) }, it.selected?.relocated(from, to)) }
    }

    /** After [location] stopped being a workspace — moved into a folder, or gone: its settings and its history go. */
    suspend fun retire(location: String) {
        runCatching { forgetSettings(location) }
            .onFailure { error -> stateHolder.tell(WorkspaceNotices.settingsNotCleared(location), "workspace", error) }
        library.forget(location)
    }

    private fun exists(name: String) = File(source.workspacesRoot, name).exists()

    private suspend fun fail(requestId: String, message: String) =
        stateHolder.emit(ICodeEffect.WorkspaceItemFailed(requestId, message))
}
