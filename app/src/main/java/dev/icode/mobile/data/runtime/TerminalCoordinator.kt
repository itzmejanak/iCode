package dev.icode.mobile.data.runtime

import androidx.annotation.MainThread
import dev.icode.mobile.domain.isAtOrUnder
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.ManagedProcess
import dev.icode.mobile.domain.notices.NoticePlace
import dev.icode.mobile.domain.notices.NoticePlaces
import dev.icode.mobile.domain.notices.TerminalNotices
import dev.icode.mobile.domain.TerminalSettings
import dev.icode.mobile.domain.TerminalSnapshot
import dev.icode.mobile.domain.ICodeEffect
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A tab removed because its shell exited by itself. */
internal data class TerminalEnding(val id: String, val title: String, val exitCode: Int, val workspace: String? = null)

/** A command that ran long ([FinishedCommand.long]) in session [id], ended. */
internal data class LongCommandEnded(val id: String, val program: String, val durationMillis: Long)

/**
 * A process's output, for a tab of its own: [key] is the process, [title] its name, [log] where its
 * output is written in the runtime, [workspaceHostPath] the workspace it runs in.
 */
internal data class FollowedOutput(val key: String, val title: String, val workspaceHostPath: String, val log: String)

/** What iCode asks of the process's terminal sessions; tests put a fake in their place. */
internal interface TerminalSessions {
    val snapshot: StateFlow<TerminalSnapshot>
    val endings: SharedFlow<TerminalEnding>
    val commandsFinished: SharedFlow<String>
    val longCommandsEnded: SharedFlow<LongCommandEnded>

    /** The session the user is looking at: iCode on screen and that session's view attached; null otherwise. */
    val lookingAt: StateFlow<String?>

    /** Opens a session, or selects a running one of the workspace when [reuseExisting]; null without a runtime. */
    suspend fun open(workspaceHostPath: String?, reuseExisting: Boolean): String?

    /** Selects the tab following [output], or opens one; null without a runtime. */
    suspend fun follow(output: FollowedOutput): String?

    /**
     * A chat's tab (docs/agent-control-plan.md §3 A4): its running one unless [fresh], else a new one;
     * the user's own selection stays. Null without a runtime.
     */
    suspend fun openAssistant(workspaceHostPath: String, chat: String, fresh: Boolean): String?

    /** [id]'s commands as its shell's marks bound them; null once it is gone. Any thread. */
    fun commands(id: String): TerminalCommands?

    /** Types [text] into [id] as its keyboard would; false when it no longer runs. */
    @MainThread fun write(id: String, text: String): Boolean

    /** What [id] shows: its last [lines] of screen and scrollback, as text; null once it is gone. */
    @MainThread fun screen(id: String, lines: Int): String?

    @MainThread fun select(id: String)
    @MainThread fun reorder(movingId: String, targetId: String)
    @MainThread fun close(id: String)
    @MainThread fun applySettings(next: TerminalSettings)
    @MainThread fun workspaceOf(id: String): String?
}

/**
 * The terminal as iCode sees it (S6): opening and closing tabs,
 * showing a session from its notification, following the sessions into the
 * snapshot, and what a finished command means for the open workspace. The
 * sessions themselves belong to the process ([TerminalSessionManager]) and
 * outlive every screen.
 */
internal class TerminalCoordinator(
    private val stateHolder: ICodeStateHolder,
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
    /** Follows the sessions, their endings and the terminal settings for as long as [scope] lives. */
    fun follow(scope: CoroutineScope, settings: Flow<TerminalSettings>) {
        scope.launch {
            terminals.snapshot.collect { terminal -> stateHolder.update { it.copy(terminal = terminal) } }
        }
        // A tab closes when its shell exits. A clean exit needs no comment; any
        // other code is said once, so a shell that failed on its own is not silent.
        scope.launch {
            terminals.endings.collect { ending ->
                if (ending.exitCode != 0) stateHolder.tell(TerminalNotices.exited(ending.title, ending.exitCode, ending.workspace?.let(::placeOf)))
            }
        }
        scope.launch {
            terminals.commandsFinished.collect { sessionId ->
                val workspace = withContext(main) { terminals.workspaceOf(sessionId) }
                if (workspace != null && workspace == activeRoot()) workspaceChanged()
            }
        }
        scope.launch { terminals.longCommandsEnded.collect { ended -> tellFinished(ended) } }
        scope.launch {
            settings.collect { next ->
                finishAlerts = next.finishAlerts
                withContext(main) { terminals.applySettings(next) }
            }
        }
    }

    /** The user's Alert When a Long Command Ends, as last applied. */
    @Volatile private var finishAlerts = TerminalSettings().finishAlerts

    /**
     * A long command's end (docs/notices-redesign-plan.md D9), unless the user watched it end: kept on
     * the Notifications page, where it ran, and — when the user asked for it — posted to Finished
     * while iCode is out of view.
     */
    private suspend fun tellFinished(ended: LongCommandEnded) {
        if (terminals.lookingAt.value == ended.id) return
        val (tab, workspace) = withContext(main) { terminals.snapshot.value.sessions.firstOrNull { it.id == ended.id }?.title to terminals.workspaceOf(ended.id) }
        val where = workspace?.let(::placeOf) ?: NoticePlace(NoticePlaces.RUNTIME)
        stateHolder.tell(TerminalNotices.finished(ended.program, ended.durationMillis, where.copy(subject = tab), ended.id, alert = finishAlerts))
    }

    /** "+": always a new session, in the open workspace, or outside it ([inWorkspace] false) in none: `$HOME`. */
    suspend fun open(inWorkspace: Boolean) {
        if (!runtimeInstalled()) return stateHolder.tell(TerminalNotices.needsRuntime())
        if (terminals.open(workspace(inWorkspace), reuseExisting = false) == null) stateHolder.tell(TerminalNotices.notStarted(where(inWorkspace)))
    }

    /** A new session in the open workspace with [command] typed at its prompt, as the user would type it; false when none opened. */
    suspend fun type(command: String): Boolean {
        if (!runtimeInstalled()) return false
        val id = terminals.open(activeRoot(), reuseExisting = false) ?: return false
        // Typed before bash has read its profile: the terminal holds it until the prompt reads it.
        withContext(main) { terminals.write(id, command + "\r") }
        stateHolder.update { it.copy(terminal = terminals.snapshot.value) }
        return true
    }

    /**
     * The panel opened with no session to show. Requests reach here one at a
     * time (the gateway's lock), so reusing a running session is enough to make
     * a panel drawn twice, or drawn while "Open Terminal" runs, meet in one shell.
     */
    suspend fun ensure(inWorkspace: Boolean) {
        if (!runtimeInstalled()) return stateHolder.tell(TerminalNotices.needsRuntime())
        if (terminals.open(workspace(inWorkspace), reuseExisting = true) == null) stateHolder.tell(TerminalNotices.notStarted(where(inWorkspace)))
    }

    /** Where a tab asked for starts: the open workspace, or none, so it starts where a login does. */
    private fun workspace(inWorkspace: Boolean): String? = if (inWorkspace) activeRoot() else null

    /** Where a tab that could not start was asked for, as a tab in no workspace is told ([tellFinished]). */
    private fun where(inWorkspace: Boolean): NoticePlace? =
        if (inWorkspace) NoticePlaces.workspace(stateHolder.current) else NoticePlace(NoticePlaces.RUNTIME)

    /** The workspace [location] was opened for its terminal: its running session, or a new one. */
    suspend fun openIn(location: String) {
        if (!runtimeInstalled()) return
        runCatching { terminals.open(location, reuseExisting = true) }
            .onSuccess { stateHolder.update { it.copy(terminal = terminals.snapshot.value) } }
            .onFailure { error -> stateHolder.tell(TerminalNotices.notStarted(placeOf(location)), "terminal", error) }
    }

    /** Whether a session opened in [location], or in a folder inside it, still runs. */
    suspend fun runsIn(location: String): Boolean = withContext(main) {
        terminals.snapshot.value.sessions.any { it.isRunning && terminals.workspaceOf(it.id)?.isAtOrUnder(location) == true }
    }

    suspend fun select(id: String) = withContext(main) { terminals.select(id) }

    suspend fun reorder(movingId: String, targetId: String) = withContext(main) { terminals.reorder(movingId, targetId) }

    suspend fun close(id: String) = withContext(main) { terminals.close(id) }

    /**
     * [process]'s output on the Terminal panel, in a tab of its own that follows [log] — the one
     * already following it, else a new one — in the workspace it runs in.
     */
    suspend fun showOutput(process: ManagedProcess, log: String) {
        if (!runtimeInstalled()) return stateHolder.tell(TerminalNotices.needsRuntime())
        val id = terminals.follow(FollowedOutput(process.id, process.name, process.workspace, log))
            ?: return stateHolder.tell(TerminalNotices.outputNotShown(process.name, placeOf(process.workspace)))
        show(id)
    }

    /**
     * Selects the session, then opens its workspace on the Terminal panel. The
     * workspace path reuses the running session the selection now names, and
     * goes through the same dirty-document confirmation as any switch. A session in
     * no workspace is shown outside the workspace, where it was opened.
     */
    suspend fun show(id: String) {
        val workspace = withContext(main) {
            if (terminals.snapshot.value.sessions.none { it.id == id && it.isRunning }) return@withContext null
            terminals.select(id)
            terminals.workspaceOf(id) ?: ""
        }
        when (workspace) {
            null -> stateHolder.tell(TerminalNotices.ended())
            "" -> stateHolder.emit(ICodeEffect.TerminalOutsideWorkspace)
            else -> openWorkspaceTerminal(workspace)
        }
    }

    /** A workspace, by where it is: its name, when it is one. */
    private fun placeOf(workspace: String) = NoticePlaces.of(stateHolder.current, workspace, inside = false)
}
