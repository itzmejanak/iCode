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
import dev.icode.mobile.domain.TerminalSessionState
import dev.icode.mobile.domain.TerminalSettingRules
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the user's runtime work alive while iCode is not on screen, and says so.
 *
 * Android reclaims a backgrounded app aggressively; a foreground service with a
 * visible notification is the supported way to continue (PR-TERM-07). It runs
 * while there is work — a running terminal session, or a runtime install — and
 * stops itself when there is none. It observes that work instead of being told
 * by a screen, because terminal sessions outlive every screen
 * (docs/terminal-background-plan.md).
 *
 * The notification is a group: this service's own notification is the group's
 * parent, and each running session has a row with Exit and a wakelock toggle.
 * Tapping a session's row opens that session.
 */
class RuntimeForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var terminals: TerminalSessionManager
    private lateinit var notifications: NotificationManager
    private lateinit var shown: TerminalNotifications
    private var sessions: List<TerminalSessionState> = emptyList()
    private var selectedId: String? = null
    private var installing = false

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
        scope.launch {
            combine(terminals.snapshot, RuntimeWork.installing) { snapshot, installing -> snapshot to installing }
                .collect { (snapshot, isInstalling) ->
                    sessions = snapshot.sessions.filter { it.isRunning }
                    selectedId = snapshot.selectedId
                    installing = isInstalling
                    if (started) show()
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
        scope.cancel()
        super.onDestroy()
    }

    private val idle: Boolean get() = sessions.isEmpty() && !installing

    /** Shows the current work, or stops when there is none. */
    private fun show() {
        keptAwake.retainAll(sessions.map { it.id }.toSet())
        applyWakeLock()
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
        (postedRows.keys - current).forEach { gone ->
            notifications.cancel(TerminalNotifications.rowId(gone))
            postedRows.remove(gone)
            commandRuns.forget(gone)
        }
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
        postedRows.keys.forEach { notifications.cancel(TerminalNotifications.rowId(it)) }
        postedRows.clear()
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
        shown.parent(sessions.size, installing, selectedId?.takeIf { id -> sessions.any { it.id == id } })

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
