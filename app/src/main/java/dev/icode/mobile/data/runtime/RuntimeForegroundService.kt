package dev.icode.mobile.data.runtime

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.net.toUri
import dev.icode.mobile.MainActivity
import dev.icode.mobile.R
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
    private val createdAt = System.currentTimeMillis() - ORDER_PARENT * ORDER_STEP_MILLIS
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
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Development runtime", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while terminal sessions or runtime installation are active"
                setShowBadge(false)
            },
        )
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
        notifications.notify(PARENT_ID, parentNotification())
        showSessionRows()
    }

    private fun showSessionRows() {
        val current = sessions.map { it.id }.toSet()
        (postedRows.keys - current).forEach { gone ->
            notifications.cancel(rowId(gone))
            postedRows.remove(gone)
            commandRuns.forget(gone)
        }
        val now = SystemClock.elapsedRealtime()
        sessions.forEachIndexed { index, session ->
            val foreground = terminals.pidOf(session.id)?.let(TerminalForeground::of) ?: TerminalForeground.Unknown
            commandRuns.sample(session.id, foreground, now)?.let { finished -> commandFinished(session, finished) }
            val text = rowText(session, foreground)
            if (postedRows[session.id] != text) {
                notifications.notify(rowId(session.id), sessionNotification(session, index, text))
                postedRows[session.id] = text
            }
        }
    }

    private fun clearSessionRows() {
        postedRows.keys.forEach { notifications.cancel(rowId(it)) }
        postedRows.clear()
    }

    /** "superpowers · running opencode · wake lock held". */
    private fun rowText(session: TerminalSessionState, foreground: TerminalForeground): String {
        val workspace = terminals.workspaceOf(session.id)?.let { File(it).name }
        val activity = when (foreground) {
            TerminalForeground.Idle -> "idle"
            is TerminalForeground.Running -> "running ${foreground.program}"
            TerminalForeground.Unknown -> null
        }
        val awake = "wake lock held".takeIf { session.id in keptAwake }
        return listOfNotNull(workspace, activity, awake).joinToString(" · ")
    }

    /** "bash · superpowers": which tab, in which workspace. */
    private fun whereIs(session: TerminalSessionState): String =
        listOfNotNull(session.title, terminals.workspaceOf(session.id)?.let { File(it).name }).joinToString(" · ")

    private fun commandFinished(session: TerminalSessionState, finished: FinishedCommand) {
        terminals.commandFinished(session.id)
        if (!terminals.settings.finishAlerts || terminals.lookingAt.value == session.id) return
        if (finished.durationMillis < TerminalSettingRules.FINISH_ALERT_MILLIS) return
        alerts.finished(session.id, finished, whereIs(session), openSession(session.id))
    }

    private fun bellRang(sessionId: String) {
        if (!terminals.settings.bellAlerts || terminals.lookingAt.value == sessionId) return
        val session = sessions.firstOrNull { it.id == sessionId } ?: return
        val foreground = terminals.pidOf(sessionId)?.let(TerminalForeground::of)
        val program = (foreground as? TerminalForeground.Running)?.program ?: session.title
        alerts.bell(sessionId, program, whereIs(session), openSession(sessionId))
    }

    /**
     * The group's parent, and this service's foreground notification. With
     * session rows present Android shows it only as the group's header, and
     * shows no buttons on it (measured on this XOS phone, as on stock
     * Android), so it has none; on its own — while the runtime installs — it
     * is the whole notification.
     */
    private fun parentNotification(): Notification {
        val text = listOfNotNull(
            when (sessions.size) {
                0 -> null
                1 -> "1 session"
                else -> "${sessions.size} sessions"
            },
            "Installing the Linux runtime".takeIf { installing },
        ).joinToString(" · ")
        return baseBuilder(ORDER_PARENT)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setGroupSummary(true)
            .setContentIntent(openSession(selectedId?.takeIf { id -> sessions.any { it.id == id } }))
            .build()
    }

    private fun sessionNotification(session: TerminalSessionState, index: Int, text: String): Notification {
        val awake = session.id in keptAwake
        return baseBuilder(ORDER_PARENT - 1 - index)
            .setContentTitle(session.title)
            .setContentText(text)
            .setContentIntent(openSession(session.id))
            .addAction(action("Exit", serviceIntent(ACTION_EXIT_SESSION, session.id)))
            .addAction(
                action(
                    if (awake) "Release wakelock" else "Acquire wakelock",
                    serviceIntent(ACTION_TOGGLE_WAKE_LOCK, session.id),
                ),
            )
            .build()
    }

    /**
     * [order] places the row: Android lists a group newest first, using a
     * notification's own time when it is in the past. Fixed times keep the
     * sessions in tab order however often a row is updated; the times
     * themselves are never shown.
     */
    private fun baseBuilder(order: Int): Notification.Builder =
        Notification.Builder(this, CHANNEL_ID)
            .setWhen(createdAt + order * ORDER_STEP_MILLIS)
            .setShowWhen(false)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.notification_accent))
            .setGroup(GROUP)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

    private fun action(title: String, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(null, title, intent).build()

    /** Opens iCode on [sessionId]'s terminal, reusing the screen that is already open. */
    private fun openSession(sessionId: String?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (sessionId != null) {
            intent.setAction(MainActivity.ACTION_SHOW_TERMINAL_SESSION)
                .putExtra(MainActivity.EXTRA_TERMINAL_SESSION_ID, sessionId)
                // Distinct data keeps one pending intent per session.
                .setData("icode://terminal/$sessionId".toUri())
        }
        return PendingIntent.getActivity(
            this,
            sessionId?.let(::rowId) ?: REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun serviceIntent(action: String, sessionId: String): PendingIntent {
        val intent = Intent(this, RuntimeForegroundService::class.java)
            .setAction(action)
            .putExtra(EXTRA_SESSION_ID, sessionId)
            // Distinct data keeps one pending intent per session and action.
            .setData("icode://terminal/$sessionId/$action".toUri())
        return PendingIntent.getService(
            this,
            rowId(sessionId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Stable per session: "session-3" is always row 5003. */
    private fun rowId(sessionId: String): Int =
        SESSION_ROW_BASE + (sessionId.substringAfterLast('-').toIntOrNull() ?: (sessionId.hashCode() and 0xFFF))

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(PARENT_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(PARENT_ID, notification)
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
        private const val CHANNEL_ID = "icode.runtime"
        private const val GROUP = "icode.terminal"
        private const val PARENT_ID = 4711
        private const val SESSION_ROW_BASE = 5000
        private const val ORDER_PARENT = 1000
        private const val ORDER_STEP_MILLIS = 1000L
        private const val REQUEST_OPEN_APP = 1
        private const val ACTION_TOGGLE_WAKE_LOCK = "dev.icode.mobile.action.TOGGLE_WAKE_LOCK"
        private const val ACTION_EXIT_SESSION = "dev.icode.mobile.action.EXIT_SESSION"
        private const val EXTRA_SESSION_ID = "session"
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
