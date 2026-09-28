package dev.icode.mobile.data.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.system.OsConstants
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** A tab removed because its shell exited by itself. */
internal data class TerminalEnding(val id: String, val title: String, val exitCode: Int)

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
 * hangs its session up the way a closing terminal does.
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

    /** Reported by the foreground service, which samples each session's program. */
    fun commandFinished(sessionId: String) {
        mutableCommandsFinished.tryEmit(sessionId)
    }

    private var screenVisible = false
    private var viewedId: String? = null
    private val mutableLookingAt = MutableStateFlow<String?>(null)
    /** The session the user is looking at: iCode on screen and that session's view attached. */
    val lookingAt: StateFlow<String?> = mutableLookingAt.asStateFlow()

    /** iCode's screen came into view or left it. */
    @MainThread
    fun setScreenVisible(visible: Boolean) {
        screenVisible = visible
        mutableLookingAt.value = viewedId.takeIf { screenVisible }
    }

    /**
     * Opens a session, or selects a running one for the same workspace when
     * [reuseExisting]. Returns null when the runtime is not installed.
     */
    override suspend fun open(workspaceHostPath: String?, reuseExisting: Boolean): String? {
        if (reuseExisting) {
            withContext(Dispatchers.Main.immediate) { selectRunning(workspaceHostPath) }?.let { return it }
        }
        val launch = withContext(Dispatchers.IO) { TerminalLaunch.prepare(layout, settings, workspaceHostPath) } ?: return null
        return withContext(Dispatchers.Main.immediate) { start(launch) }
    }

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
        sessionNames.release(id)
        entry.session?.let(::hangUp)
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
        viewed(id)
        return AutoCloseable {
            entries[id]?.let { if (it.observer === onScreenChanged) it.observer = null }
            if (viewedId == id) viewed(null)
        }
    }

    private fun viewed(id: String?) {
        viewedId = id
        mutableLookingAt.value = id.takeIf { screenVisible }
    }

    /**
     * A running session of [workspaceHostPath]: the selected one when it
     * belongs there — which is how "open this session" from the notification
     * lands on that session — otherwise the first.
     */
    private fun selectRunning(workspaceHostPath: String?): String? {
        val candidates = entries.values.filter { it.workspaceHostPath == workspaceHostPath && it.state.isRunning }
        val chosen = candidates.firstOrNull { it.id == mutableSnapshot.value.selectedId } ?: candidates.firstOrNull()
        return chosen?.also { publish(selectedId = it.id) }?.id
    }

    @MainThread
    private fun start(launch: TerminalLaunch): String {
        val id = "session-${nextId++}"
        val title = sessionNames.allocate(id)
        val entry = Entry(id, launch.workspaceHostPath, TerminalSessionState(id, title, TerminalStatus.Running))
        entries[id] = entry
        if (launch.failure != null) {
            entry.state = entry.state.copy(status = TerminalStatus.Failed, failure = launch.failure)
        } else {
            val session = TerminalSession(
                launch.argv.first(),
                layout.runtimeRoot.absolutePath,
                launch.argv.toTypedArray(),
                launch.environment.toTypedArray(),
                launch.transcriptRows,
                TerminalSessionCallbacks(
                    context = context,
                    cursorStyle = { settings.cursorStyle },
                    onScreenChanged = { entries[id]?.observer?.invoke() },
                    onFinished = { exitStatus -> finished(id, exitStatus) },
                    onBell = { mutableBells.tryEmit(id) },
                ),
            )
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
        }
        publish(selectedId = id)
        return id
    }

    /**
     * SIGHUP to PRoot, which passes it on to every program in the session and
     * kills those that ignore it two seconds later (third_party/patches/proot/0002).
     * Should PRoot itself still be running after that, it is killed, and
     * PTRACE_O_EXITKILL takes its programs with it.
     */
    private fun hangUp(session: TerminalSession) {
        if (!session.isRunning) return
        runCatching { Os.kill(session.pid, OsConstants.SIGHUP) }
        mainHandler.postDelayed({ session.finishIfRunning() }, HANG_UP_TIMEOUT_MILLIS)
    }

    @MainThread
    private fun finished(id: String, exitStatus: Int) {
        // Closed by the user: already removed, and nothing to report.
        val entry = entries[id] ?: return
        entry.observer?.invoke()
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
            entries.values.filter { it.state.isRunning }
                .map { LostTerminalSession(it.state.title, it.workspaceHostPath?.let { path -> File(path).name }) },
        )
    }

    private class Entry(val id: String, val workspaceHostPath: String?, var state: TerminalSessionState) {
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
        private const val HANG_UP_TIMEOUT_MILLIS = 5_000L
        private const val INITIAL_COLUMNS = 80
        private const val INITIAL_ROWS = 24
        private const val INITIAL_CELL_PX = 16
    }
}
