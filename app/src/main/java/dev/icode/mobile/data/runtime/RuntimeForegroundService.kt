package dev.icode.mobile.data.runtime

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import dev.icode.mobile.data.tasks.BackgroundWork
import dev.icode.mobile.data.tasks.WorkNotifications
import dev.icode.mobile.domain.BackgroundItem
import dev.icode.mobile.domain.TerminalSessionState
import dev.icode.mobile.domain.TerminalSettingRules
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the user's runtime work alive while iCode is not on screen, and says so.
 *
 * Android reclaims a backgrounded app aggressively; a foreground service with a
 * visible notification is the supported way to continue (PR-TERM-07). It runs
 * while there is work — a running terminal session, or background work: a task,
 * a Git transfer, the runtime install, a package install
 * (docs/long-operations-plan.md §6) — and stops itself when there is none. It
 * observes that work instead of being told by a screen, because the work
 * outlives every screen (docs/terminal-background-plan.md).
 *
 * Its own notification says what runs ("1 session · 1 task running") and is the
 * parent of the session rows, each with Exit and a wakelock toggle; tapping a
 * row opens that session. Background work has its own notifications
 * ([WorkNotifications]): progress, with Cancel where allowed, and failures.
 */
class RuntimeForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var terminals: TerminalSessionManager
    private lateinit var notifications: NotificationManager
    private lateinit var shown: TerminalNotifications
    private var sessions: List<TerminalSessionState> = emptyList()
    private var selectedId: String? = null
    private var work: List<BackgroundItem> = emptyList()
    private lateinit var workShown: WorkNotifications

    /** Nothing may stop the service before the start it was launched for is honoured. */
    private var started = false

    /**
     * Sessions whose row asked to keep the CPU awake. Android gives an app one
     * CPU wake lock, not one per session: it is held while any of these runs.
     */
    private val keptAwake = mutableSetOf<String>()
    private var wakeLock: PowerManager.WakeLock? = null

    /** What each posted session row shows, so an unchanged row is not re-posted. */
    private val postedRows = mutableMapOf<String, String>()

    private lateinit var alerts: TerminalAlerts
    private val commandRuns = CommandRuns()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        terminals = TerminalSessionManager.get(this)
        notifications = getSystemService(NotificationManager::class.java)
        shown = TerminalNotifications(this)
        shown.createChannel(notifications)
        workShown = WorkNotifications(this, notifications)
        scope.launch {
            // Leaving iCode is followed too: a question asked while it was in view pops up when it goes.
            combine(terminals.snapshot, BackgroundWork.items, terminals.screenVisible) { snapshot, items, _ -> snapshot to items }
                .conflate()
                .collect { (snapshot, items) ->
                    // A process's output tab is no session: its process is the work's own row.
                    sessions = snapshot.sessions.filter { it.isRunning && it.follows == null }
                    selectedId = snapshot.selectedId
                    work = items
                    if (started) show()
                    // Android drops a notification updated faster than a few times a second.
                    delay(NOTIFY_INTERVAL_MILLIS)
                }
        }
        alerts = TerminalAlerts(this, notifications)
        scope.launch { terminals.bells.collect(::bellRang) }
        // Looking at a session answers its alert.
        scope.launch { terminals.lookingAt.collect { id -> id?.let(alerts::clear) } }
        // What a session runs changes without any session event.
        scope.launch {
            while (isActive) {
                delay(FOREGROUND_REFRESH_MILLIS)
                if (started && !idle) showSessionRows()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started with startForegroundService: it must go to the foreground
        // now, even if the work it was started for has already ended.
        startForegroundCompat(parentNotification())
        started = true
        val session = intent?.getStringExtra(EXTRA_SESSION_ID)
        when (intent?.action) {
            ACTION_EXIT_SESSION -> session?.let(terminals::close)
            ACTION_TOGGLE_WAKE_LOCK -> session?.let { if (!keptAwake.remove(it)) keptAwake.add(it) }
            ACTION_CANCEL_WORK -> intent.getStringExtra(EXTRA_WORK_KEY)?.let(BackgroundWork::cancel)
        }
        show()
        // Sessions cannot be recreated after the process is gone, so a restart
        // would only show a notification about work that no longer exists.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        keptAwake.clear()
        applyWakeLock()
        clearSessionRows()
        workShown.clear()
        scope.cancel()
        super.onDestroy()
    }

    private val idle: Boolean get() = sessions.isEmpty() && work.none { it.running }

    /** Shows the current work, or stops when there is none. */
    private fun show() {
        keptAwake.retainAll(sessions.map { it.id }.toSet())
        applyWakeLock()
        // Before stopping: the last work may have just failed, and that is said once.
        workShown.show(work, terminals.screenVisible.value)
        if (idle) {
            clearSessionRows()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        // Updating a notification does not restart the service, so this is
        // safe while iCode is in the background.
        notifications.notify(TerminalNotifications.PARENT_ID, parentNotification())
        showSessionRows()
    }

    private fun showSessionRows() {
        val current = sessions.map { it.id }.toSet()
        (postedRows.keys - current).forEach(::forget)
        val now = SystemClock.elapsedRealtime()
        sessions.forEachIndexed { index, session ->
            val foreground = terminals.pidOf(session.id)?.let(TerminalForeground::of) ?: TerminalForeground.Unknown
            commandRuns.sample(session.id, foreground, now)?.let { finished -> commandFinished(session, finished) }
            val workspace = terminals.workspaceOf(session.id)?.let { File(it).name }
            val text = TerminalNotifications.rowText(workspace, foreground, session.id in keptAwake)
            if (postedRows[session.id] != text) {
                notifications.notify(
                    TerminalNotifications.rowId(session.id),
                    shown.sessionRow(session, index, text, session.id in keptAwake),
                )
                postedRows[session.id] = text
            }
        }
    }

    private fun clearSessionRows() {
        postedRows.keys.toList().forEach(::forget)
    }

    /** A session that no longer runs: its row, its command timing and its alert go. */
    private fun forget(sessionId: String) {
        notifications.cancel(TerminalNotifications.rowId(sessionId))
        postedRows.remove(sessionId)
        commandRuns.forget(sessionId)
        // Left behind, an alert would only open a session that has ended.
        alerts.clear(sessionId)
    }

    /** "bash · superpowers": which tab, in which workspace. */
    private fun whereIs(session: TerminalSessionState): String =
        TerminalNotifications.whereIs(session.title, terminals.workspaceOf(session.id)?.let { File(it).name })

    private fun commandFinished(session: TerminalSessionState, finished: FinishedCommand) {
        terminals.commandFinished(session.id)
        if (!terminals.settings.finishAlerts || terminals.lookingAt.value == session.id) return
        if (finished.durationMillis < TerminalSettingRules.FINISH_ALERT_MILLIS) return
        alerts.finished(session.id, finished, whereIs(session), shown.openSession(session.id))
    }

    private fun bellRang(sessionId: String) {
        if (!terminals.settings.bellAlerts || terminals.lookingAt.value == sessionId) return
        val session = sessions.firstOrNull { it.id == sessionId } ?: return
        val foreground = terminals.pidOf(sessionId)?.let(TerminalForeground::of)
        val program = (foreground as? TerminalForeground.Running)?.program ?: session.title
        alerts.bell(sessionId, program, whereIs(session), shown.openSession(sessionId))
    }

    private fun parentNotification(): Notification =
        shown.parent(sessions.size, work.filter { it.running }.map { it.title }, selectedId?.takeIf { id -> sessions.any { it.id == id } })

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(TerminalNotifications.PARENT_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(TerminalNotifications.PARENT_ID, notification)
        }
    }

    /**
     * Holds the CPU wake lock exactly while some running session asked for it.
     * A foreground service does not stop the CPU sleeping with the screen off,
     * so long work pauses; the lock is the user's choice, as in Termux, because
     * it costs battery.
     */
    // Released later by design: when no session wants it, or the work ends.
    @SuppressLint("WakelockTimeout", "Wakelock")
    private fun applyWakeLock() {
        val wanted = keptAwake.isNotEmpty()
        val held = wakeLock?.isHeld == true
        if (wanted && !held) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        } else if (!wanted && held) {
            wakeLock?.release()
            wakeLock = null
        }
    }

    companion object {
        internal const val ACTION_TOGGLE_WAKE_LOCK = "dev.icode.mobile.action.TOGGLE_WAKE_LOCK"
        internal const val ACTION_EXIT_SESSION = "dev.icode.mobile.action.EXIT_SESSION"
        internal const val EXTRA_SESSION_ID = "session"
        internal const val ACTION_CANCEL_WORK = "dev.icode.mobile.action.CANCEL_WORK"
        internal const val EXTRA_WORK_KEY = "work"
        private const val NOTIFY_INTERVAL_MILLIS = 500L
        private const val WAKE_LOCK_TAG = "iCode:terminal-sessions"
        private const val FOREGROUND_REFRESH_MILLIS = 5_000L

        /**
         * Makes sure the service runs; it stops by itself when the work ends.
         * Call when work starts, which is always a user action in the
         * foreground — the only time Android lets an app start one.
         */
        fun ensureRunning(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, RuntimeForegroundService::class.java))
            }
        }
    }
}
