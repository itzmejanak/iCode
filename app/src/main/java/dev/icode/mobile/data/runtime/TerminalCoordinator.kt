package dev.icode.mobile.data.runtime

import androidx.annotation.MainThread
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.TerminalSettings
import dev.icode.mobile.domain.TerminalSnapshot
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkspacePreferences
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A tab removed because its shell exited by itself. */
internal data class TerminalEnding(val id: String, val title: String, val exitCode: Int)

/** What the workbench asks of the process's terminal sessions; tests put a fake in their place. */
internal interface TerminalSessions {
    val snapshot: StateFlow<TerminalSnapshot>
    val endings: SharedFlow<TerminalEnding>
    val commandsFinished: SharedFlow<String>

    /** Opens a session, or selects a running one of the workspace when [reuseExisting]; null without a runtime. */
    suspend fun open(workspaceHostPath: String?, reuseExisting: Boolean): String?

    @MainThread fun select(id: String)
    @MainThread fun reorder(movingId: String, targetId: String)
    @MainThread fun close(id: String)
    @MainThread fun applySettings(next: TerminalSettings)
    /** The prompt's colour: the app's accent in effect. File work, off the main thread. */
    suspend fun applyAccent(argb: Int)
    @MainThread fun workspaceOf(id: String): String?
}

/**
 * The terminal as the workbench sees it (S6): opening and closing tabs,
 * showing a session from its notification, following the sessions into the
 * snapshot, and what a finished command means for the open workspace. The
 * sessions themselves belong to the process ([TerminalSessionManager]) and
 * outlive every screen.
 */
internal class TerminalCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val terminals: TerminalSessions,
    private val runtimeInstalled: () -> Boolean,
    private val activeRoot: () -> String?,
    /** Opens [location] on the Terminal panel, with the same questions as any workspace switch. */
    private val openWorkspaceTerminal: suspend (location: String) -> Unit,
    /** A command that ran in the open workspace may have written to it. */
    private val workspaceChanged: () -> Unit,
    /** Sessions are confined to the main thread. */
    private val main: CoroutineContext = Dispatchers.Main.immediate,
) {
    /** Follows the sessions, their endings, the terminal settings and the accent for as long as [scope] lives. */
    fun follow(scope: CoroutineScope, preferences: Flow<WorkspacePreferences>) {
        scope.launch {
            terminals.snapshot.collect { terminal -> workbench.update { it.copy(terminal = terminal) } }
        }
        // A tab closes when its shell exits. A clean exit needs no comment; any
        // other code is said once, so a shell that failed on its own is not silent.
        scope.launch {
            terminals.endings.collect { ending ->
                if (ending.exitCode != 0) workbench.message("${ending.title} exited with code ${ending.exitCode}")
            }
        }
        scope.launch {
            terminals.commandsFinished.collect { sessionId ->
                val workspace = withContext(main) { terminals.workspaceOf(sessionId) }
                if (workspace != null && workspace == activeRoot()) workspaceChanged()
            }
        }
        scope.launch {
            preferences.map { it.terminal }.distinctUntilChanged()
                .collect { next -> withContext(main) { terminals.applySettings(next) } }
        }
        scope.launch {
            preferences.map { it.appearance.effectiveAccent }.distinctUntilChanged().collect { terminals.applyAccent(it) }
        }
    }

    /** "+": always a new session, in the open workspace. */
    suspend fun open() {
        if (!runtimeInstalled()) {
            workbench.message("Install the Linux runtime before opening a terminal")
            return
        }
        if (terminals.open(activeRoot(), reuseExisting = false) == null) {
            workbench.message("Unable to start a terminal session")
        }
    }

    /**
     * The panel opened with no session to show. Requests reach here one at a
     * time (the gateway's lock), so reusing a running session is enough to make
     * a panel drawn twice, or drawn while "Open Terminal" runs, meet in one shell.
     */
    suspend fun ensure() {
        if (!runtimeInstalled()) {
            workbench.message("Install the Linux runtime before opening a terminal")
            return
        }
        if (terminals.open(activeRoot(), reuseExisting = true) == null) {
            workbench.message("Unable to start a terminal session")
        }
    }

    /** The workspace [location] was opened for its terminal: its running session, or a new one. */
    suspend fun openIn(location: String) {
        if (!runtimeInstalled()) return
        runCatching { terminals.open(location, reuseExisting = true) }
            .onSuccess { workbench.update { it.copy(terminal = terminals.snapshot.value) } }
            .onFailure { error -> workbench.report("terminal", "Unable to start terminal", error) }
    }

    suspend fun select(id: String) = withContext(main) { terminals.select(id) }

    suspend fun reorder(movingId: String, targetId: String) = withContext(main) { terminals.reorder(movingId, targetId) }

    suspend fun close(id: String) = withContext(main) { terminals.close(id) }

    /**
     * Selects the session, then opens its workspace on the Terminal panel. The
     * workspace path reuses the running session the selection now names, and
     * goes through the same dirty-document confirmation as any switch.
     */
    suspend fun show(id: String) {
        val workspace = withContext(main) {
            if (terminals.snapshot.value.sessions.none { it.id == id && it.isRunning }) return@withContext null
            terminals.select(id)
            terminals.workspaceOf(id) ?: ""
        }
        when (workspace) {
            null -> workbench.message("That terminal session has ended")
            "" -> workbench.emit(WorkbenchEffect.WorkspaceTerminalOpened(activeRoot().orEmpty()))
            else -> openWorkspaceTerminal(workspace)
        }
    }
}
