package dev.icode.mobile.data.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import com.termux.terminal.TerminalSession
import dev.icode.mobile.domain.LostTerminalSession
import dev.icode.mobile.domain.TerminalSessionNames
import dev.icode.mobile.domain.TerminalSessionState
import dev.icode.mobile.domain.TerminalSettings
import dev.icode.mobile.domain.TerminalSnapshot
import dev.icode.mobile.domain.TerminalStatus
import dev.icode.mobile.domain.reorderTerminalIds
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Owns every terminal session.
 *
 * A session is a Termux [TerminalSession]: the pty, the shell PRoot runs, and
 * the emulator that interprets its output. Termux's JNI only spawns the
 * process; the command line comes from [RuntimeLayout.prootCommand], the one
 * builder for every runtime launch.
 *
 * Confined to the main thread, where Termux delivers output and exits and
 * where a session must be created. Only [open] may be called from elsewhere.
 *
 * A tab lives exactly as long as its shell (docs/terminal-maturity-plan.md
 * section 4): a shell that exits is removed and reported on [endings]; one
 * killed by a signal stays so the user can see what happened; closing a tab
 * hangs its session up the way a closing terminal does. A process's output has
 * a tab of its own ([follow]), which closes without a word.
 *
 * One per process ([get]), not per screen: closing iCode's screen must not end
 * a shell the user left running (docs/terminal-background-plan.md). Only the
 * user ends sessions — by closing a tab, `exit`, or Exit in the notification.
 */
internal class TerminalSessionManager private constructor(
    private val context: Context,
    private val layout: RuntimeLayout,
) : TerminalSessions {
    private val entries = linkedMapOf<String, Entry>()
    /** Each running session's commands, for any thread (docs/agent-control-plan.md §4.1). */
    private val records = ConcurrentHashMap<String, TerminalCommands>()
    private val sessionNames = TerminalSessionNames()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nextId = 1

    /** Read on [open]'s background half, written on the main thread. */
    @Volatile
    var settings = TerminalSettings()
        private set

    private val mutableSnapshot = MutableStateFlow(TerminalSnapshot())
    override val snapshot: StateFlow<TerminalSnapshot> = mutableSnapshot.asStateFlow()

    private val mutableEndings = MutableSharedFlow<TerminalEnding>(extraBufferCapacity = ENDINGS_BUFFER)
    override val endings: SharedFlow<TerminalEnding> = mutableEndings.asSharedFlow()

    /** What the running sessions are, kept for the next process should Android end this one. */
    val ledger = TerminalSessionLedger(context)

    private val mutableBells = MutableSharedFlow<String>(extraBufferCapacity = ENDINGS_BUFFER)
    /** Sessions whose program rang the terminal bell. */
    val bells: SharedFlow<String> = mutableBells.asSharedFlow()

    private val mutableCommandsFinished = MutableSharedFlow<String>(extraBufferCapacity = ENDINGS_BUFFER)
    /** Sessions whose foreground command just ended: what it ran may have changed the workspace. */
    override val commandsFinished: SharedFlow<String> = mutableCommandsFinished.asSharedFlow()

    /** Reported by the foreground service, which samples each session's program; a shell with marks says so itself. */
    fun commandFinished(sessionId: String) {
        if (records[sessionId]?.integrated != true) mutableCommandsFinished.tryEmit(sessionId)
    }

    private val attention = TerminalAttention()
    val screenVisible: StateFlow<Boolean> get() = attention.screenVisible
    val lookingAt: StateFlow<String?> get() = attention.lookingAt

    @MainThread
    fun setScreenVisible(visible: Boolean) = attention.setScreenVisible(visible)

    /**
     * Opens a session, or selects a running one for the same workspace when
     * [reuseExisting]. Returns null when the runtime is not installed.
     */
    override suspend fun open(workspaceHostPath: String?, reuseExisting: Boolean): String? =
        openOr({ if (reuseExisting) selectRunning(workspaceHostPath) else null }) { TerminalLaunch.prepare(layout, settings, workspaceHostPath) }

    override suspend fun openAssistant(workspaceHostPath: String, chat: String, fresh: Boolean): String? =
        openOr({ if (fresh) null else entries.values.firstOrNull { it.assistant == chat && it.state.isRunning }?.id }, select = false) {
            TerminalLaunch.assistant(layout, settings, workspaceHostPath, chat)
        }

    override suspend fun follow(output: FollowedOutput): String? =
        openOr({ entries.values.firstOrNull { it.follows == output.key && it.state.isRunning }?.also { publish(selectedId = it.id) }?.id }) {
            TerminalLaunch.following(layout, settings, output)
        }

    /** The running tab [existing] finds, else one of [prepare]'s, prepared off the main thread; null without a runtime. */
    private suspend fun openOr(existing: () -> String?, select: Boolean = true, prepare: () -> TerminalLaunch?): String? {
        withContext(Dispatchers.Main.immediate) { existing() }?.let { return it }
        val launch = withContext(Dispatchers.IO) { prepare() } ?: return null
        return withContext(Dispatchers.Main.immediate) { start(launch, select) }
    }

    override fun commands(id: String): TerminalCommands? = records[id]

    @MainThread
    override fun write(id: String, text: String): Boolean = entries[id]?.session?.type(text) == true

    @MainThread
    override fun screen(id: String, lines: Int): String? = entries[id]?.session?.lastLines(lines)

    @MainThread
    override fun select(id: String) {
        if (entries.containsKey(id)) publish(selectedId = id)
    }

    @MainThread
    override fun reorder(movingId: String, targetId: String) {
        val original = entries.keys.toList()
        val reordered = reorderTerminalIds(original, movingId, targetId)
        if (reordered == original) return
        val moved = reordered.map { it to entries.getValue(it) }
        entries.clear()
        moved.forEach { (id, entry) -> entries[id] = entry }
        publish(selectedId = mutableSnapshot.value.selectedId)
    }

    @MainThread
    override fun applySettings(next: TerminalSettings) {
        val cursorChanged = next.cursorStyle != settings.cursorStyle
        settings = next
        if (cursorChanged) {
            entries.values.forEach { entry ->
                entry.session?.emulator?.setCursorStyle()
                entry.observer?.invoke()
            }
        }
    }

    /** Closes a tab the user dismissed, hanging up whatever still runs in it. */
    @MainThread
    override fun close(id: String) {
        val entry = entries.remove(id) ?: return
        records.remove(id)?.close()
        sessionNames.release(id)
        entry.session?.hangUp(mainHandler)
        val selected = mutableSnapshot.value.selectedId?.takeIf { it != id && entries.containsKey(it) }
        publish(selectedId = selected ?: entries.keys.lastOrNull())
    }

    /** True while any session still has a live process. Safe from any thread. */
    fun hasRunningSessions(): Boolean = mutableSnapshot.value.sessions.any { it.isRunning }

    /** The workspace a session was opened in, or null when it has none or is gone. */
    @MainThread
    override fun workspaceOf(id: String): String? = entries[id]?.workspaceHostPath

    /** The PRoot process a running session runs under, or null. */
    @MainThread
    fun pidOf(id: String): Int? = entries[id]?.session?.takeIf { it.isRunning }?.pid

    /** The live session behind a tab, for the view that draws it. */
    @MainThread
    fun session(id: String): TerminalSession? = entries[id]?.session

    /** Calls [onScreenChanged] whenever the screen of [id] changes, until the handle is closed. */
    @MainThread
    fun observe(id: String, onScreenChanged: () -> Unit): AutoCloseable {
        entries[id]?.observer = onScreenChanged
        attention.viewed(id)
        return AutoCloseable {
            entries[id]?.let { if (it.observer === onScreenChanged) it.observer = null }
            attention.left(id)
        }
    }

    /**
     * A running session of [workspaceHostPath]: the selected one when it
     * belongs there — which is how "open this session" from the notification
     * lands on that session — otherwise the first.
     */
    private fun selectRunning(workspaceHostPath: String?): String? {
        val candidates = entries.values.filter { it.workspaceHostPath == workspaceHostPath && it.state.isRunning && it.assistant == null }
        val chosen = candidates.firstOrNull { it.id == mutableSnapshot.value.selectedId } ?: candidates.firstOrNull()
        return chosen?.also { publish(selectedId = it.id) }?.id
    }

    @MainThread
    private fun start(launch: TerminalLaunch, select: Boolean = true): String {
        val id = "session-${nextId++}"
        val title = launch.title ?: sessionNames.allocate(id, if (launch.assistant != null) TerminalSessionNames.ASSISTANT else TerminalSessionNames.SHELL)
        val state = TerminalSessionState(id, title, TerminalStatus.Running, follows = launch.follows, assistant = launch.assistant)
        val entry = Entry(id, launch.workspaceHostPath, state, launch.follows, launch.assistant)
        entries[id] = entry
        val commands = TerminalCommands { mutableCommandsFinished.tryEmit(id) }.also { records[id] = it }
        val callbacks = TerminalSessionCallbacks(
            context = context,
            cursorStyle = { settings.cursorStyle },
            onScreenChanged = { entries[id]?.observer?.invoke() },
            onFinished = { exitStatus -> finished(id, exitStatus) },
            onBell = { mutableBells.tryEmit(id) },
        )
        val session = terminalSession(launch, layout.runtimeRoot.absolutePath, callbacks, commands)
        // Start now rather than on the view's first layout, so a session
        // opened from elsewhere is already running when it is shown. The
        // view resizes it to the real window when it attaches.
        runCatching { session.initializeEmulator(INITIAL_COLUMNS, INITIAL_ROWS, INITIAL_CELL_PX, INITIAL_CELL_PX) }
            .onSuccess {
                entry.session = session
                // Keeps this session alive while iCode is not on screen;
                // the service stops by itself once no session runs.
                RuntimeForegroundService.ensureRunning(context)
            }
            .onFailure { error ->
                entry.state = entry.state.copy(
                    status = TerminalStatus.Failed,
                    failure = "Unable to start the terminal: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        publish(selectedId = if (select) id else mutableSnapshot.value.selectedId)
        return id
    }

    @MainThread
    private fun finished(id: String, exitStatus: Int) {
        // Closed by the user: already removed, and nothing to report.
        val entry = entries[id] ?: return
        entry.observer?.invoke()
        // A process's output, closed with Ctrl-C: a view, whose ending is nothing to report.
        if (entry.follows != null) return close(id)
        records.remove(id)?.close()
        if (exitStatus < 0) {
            // Killed by a signal: keep the tab, whose screen shows the end.
            entry.state = entry.state.copy(status = TerminalStatus.Exited, exitSignal = -exitStatus)
            publish(selectedId = mutableSnapshot.value.selectedId)
            return
        }
        val title = entry.state.title
        close(id)
        mutableEndings.tryEmit(TerminalEnding(id, title, exitStatus))
    }

    private fun publish(selectedId: String?) {
        val states = entries.values.map { it.state }
        val resolved = selectedId?.takeIf { candidate -> states.any { it.id == candidate } }
            ?: states.lastOrNull()?.id
        mutableSnapshot.value = TerminalSnapshot(sessions = states, selectedId = resolved)
        ledger.record(
            entries.values.filter { it.state.isRunning && it.follows == null && it.assistant == null }
                .map { LostTerminalSession(it.state.title, it.workspaceHostPath?.let { path -> File(path).name }) },
        )
    }

    /** [follows]: the process whose output it shows; null for a shell. [assistant]: the chat whose tab it is. */
    private class Entry(val id: String, val workspaceHostPath: String?, var state: TerminalSessionState, val follows: String?, val assistant: String?) {
        var session: TerminalSession? = null
        var observer: (() -> Unit)? = null
    }

    companion object {
        // Holds the application context only (see get), which lives as long
        // as the process anyway.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: TerminalSessionManager? = null

        /** The process's sessions, created on first use. */
        fun get(context: Context): TerminalSessionManager = instance ?: synchronized(this) {
            instance ?: context.applicationContext.let { app ->
                TerminalSessionManager(app, RuntimeLayout(app)).also { instance = it }
            }
        }

        private const val ENDINGS_BUFFER = 8
        private const val INITIAL_COLUMNS = 80
        private const val INITIAL_ROWS = 24
        private const val INITIAL_CELL_PX = 16
    }
}
