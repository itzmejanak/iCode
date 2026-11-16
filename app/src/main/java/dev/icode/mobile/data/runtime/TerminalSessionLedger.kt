package dev.icode.mobile.data.runtime

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import dev.icode.mobile.domain.LostTerminalSession
import dev.icode.mobile.domain.LostTerminalSessions
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.TerminalNotices
import dev.icode.mobile.domain.ProcessEndCause
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/**
 * The running terminal sessions, kept on disk so that the next process can say
 * what ended when Android ended this one: a killed process cannot be brought
 * back, and its sessions are reported as stopped, never as resumed
 * (docs/runtime-and-terminal.md).
 *
 * One per process, owned by [TerminalSessionManager]. Android's own record of
 * the process's exit says why it ended; a session list does not fit in that
 * record, and a phone restart leaves no record at all, hence this one.
 */
internal class TerminalSessionLedger(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val bootCount = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, UNKNOWN_BOOT)

    /** What the previous process left, taken at once: reported once, never overwritten unread. */
    private val previous: Record? = read()?.also { preferences.edit { clear() } }
    private var written: List<LostTerminalSession> = emptyList()
    private val taken = AtomicBoolean(false)

    /** Keeps [running] — the sessions with a live process, in tab order — on disk. */
    @MainThread
    fun record(running: List<LostTerminalSession>) {
        if (running == written) return
        written = running
        preferences.edit {
            if (running.isEmpty()) {
                clear()
            } else {
                putInt(KEY_PID, Process.myPid())
                putInt(KEY_BOOT, bootCount)
                putString(KEY_SESSIONS, encode(running))
            }
        }
    }

    /**
     * What the previous process lost, as a notice — handed over once per
     * process, to whoever asks first. It asks Android why that process
     * ended, so not on the main thread.
     */
    @WorkerThread
    fun takePrevious(): Notice? {
        if (!taken.compareAndSet(false, true)) return null
        val record = previous ?: return null
        val restarted = record.boot != bootCount
        val exit = if (restarted) null else exitOf(record.pid)
        val lost = LostTerminalSessions(
            sessions = record.sessions,
            cause = ProcessEnds.cause(restarted, exit?.reason, Build.VERSION.SDK_INT),
            endedAt = exit?.timestamp,
        )
        // The dead process's boot and pid: the same end is never stored twice.
        return TerminalNotices.sessionsEnded(lost).copy(id = "terminal-sessions-ended:${record.boot}:${record.pid}", at = exit?.timestamp ?: System.currentTimeMillis())
    }

    private fun exitOf(pid: Int): ApplicationExitInfo? = runCatching {
        app.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(app.packageName, pid, 1)
            .firstOrNull()
    }.getOrNull()

    /** A damaged record is dropped: there is nothing truthful left to report from it. */
    private fun read(): Record? = runCatching {
        val sessions = decode(preferences.getString(KEY_SESSIONS, null) ?: return null)
        if (sessions.isEmpty()) return null
        Record(preferences.getInt(KEY_PID, 0), preferences.getInt(KEY_BOOT, UNKNOWN_BOOT), sessions)
    }.getOrNull()

    private fun encode(sessions: List<LostTerminalSession>): String = JSONArray().apply {
        sessions.forEach { session ->
            put(JSONObject().put(KEY_TITLE, session.title).put(KEY_WORKSPACE, session.workspace ?: JSONObject.NULL))
        }
    }.toString()

    private fun decode(text: String): List<LostTerminalSession> {
        val array = JSONArray(text)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            LostTerminalSession(item.getString(KEY_TITLE), item.optString(KEY_WORKSPACE).takeIf { !item.isNull(KEY_WORKSPACE) })
        }
    }

    private class Record(val pid: Int, val boot: Int, val sessions: List<LostTerminalSession>)

    private companion object {
        // Stored keys; do not rename.
        const val PREFERENCES_NAME = "terminal_sessions"
        const val KEY_PID = "pid"
        const val KEY_BOOT = "boot"
        const val KEY_SESSIONS = "sessions"
        const val KEY_TITLE = "title"
        const val KEY_WORKSPACE = "workspace"
        const val UNKNOWN_BOOT = -1
    }
}

/** Android's exit reasons, in the terms iCode reports them. */
internal object ProcessEnds {
    fun cause(restarted: Boolean, exitReason: Int?, sdk: Int): ProcessEndCause = when {
        restarted -> ProcessEndCause.PhoneRestarted
        exitReason == null -> ProcessEndCause.Unrecorded
        else -> when (exitReason) {
            // Before Android 14 this reason also meant "updated".
            ApplicationExitInfo.REASON_USER_REQUESTED ->
                if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ProcessEndCause.StoppedByUser
                else ProcessEndCause.StoppedByUserOrUpdated
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> ProcessEndCause.Updated
            ApplicationExitInfo.REASON_LOW_MEMORY -> ProcessEndCause.LowMemory
            ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE -> ProcessEndCause.Crashed
            ApplicationExitInfo.REASON_ANR -> ProcessEndCause.NotResponding
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> ProcessEndCause.PermissionChanged
            ApplicationExitInfo.REASON_SIGNALED -> ProcessEndCause.Killed
            else -> ProcessEndCause.Other
        }
    }
}
