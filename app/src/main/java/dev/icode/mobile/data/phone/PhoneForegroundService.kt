package dev.icode.mobile.data.phone

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import dev.icode.mobile.R
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.tasks.WorkNotifications
import dev.icode.mobile.data.workspace.AndroidICodeGateway
import dev.icode.mobile.data.workspace.ICodeProcess
import dev.icode.mobile.domain.BackgroundPlace
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.PhoneIntent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Phone control's foreground service (docs/phone-agent-plan.md §5.8, §5.9): it keeps the agent alive
 * while phone control is on, and holds the microphone for the bubble while iCode is away. Android
 * gives that only to a service started while iCode is on screen; one started again without it runs
 * without the microphone ([microphone] false), and the bubble says so. Its notification has Stop, Show
 * bubble and Turn off.
 */
class PhoneForegroundService : Service() {
    private val scope = MainScope()
    private lateinit var gateway: AndroidICodeGateway
    private lateinit var notifications: NotificationManager
    private var shown = Shown(working = null, bubble = true)
    private var started = false

    /** What the notification says: the step of a turn in hand, and whether the bubble is out. */
    private data class Shown(val working: String?, val bubble: Boolean)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Phone control", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while the assistant can use this phone"
                setShowBadge(false)
            },
        )
        gateway = ICodeProcess.attach(this)
        active = true
        scope.launch {
            gateway.snapshot.map { Shown(it.assistant.turn?.step, it.phone.bubble) }.distinctUntilChanged().collect {
                shown = it
                if (started) notifications.notify(NOTIFICATION, notification())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> gateway.dispatch(ICodeIntent.StopAssistant)
            ACTION_SHOW_BUBBLE -> gateway.dispatch(PhoneIntent.ShowBubble(true))
            ACTION_TURN_OFF -> gateway.dispatch(PhoneIntent.TurnOff)
            // Once asked for while iCode was on screen, the microphone is never given up: a start
            // without it that was already in flight must not take it back (the bubble would lose it).
            else -> foreground(intent?.getBooleanExtra(EXTRA_MICROPHONE, false) == true || wanted)
        }
        // A notification's button reaching a service Android had stopped: nothing to keep alive.
        if (!started) stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        heldMicrophone.value = false
        wanted = false
        active = false
        scope.cancel()
        ICodeProcess.detach()
        super.onDestroy()
    }

    /** In the foreground, holding the microphone when asked, allowed, and while iCode is on screen. */
    private fun foreground(microphone: Boolean) {
        val allowed = microphone && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val held = try {
            startForeground(NOTIFICATION, notification(), base or (if (allowed) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0))
            allowed
        } catch (refused: SecurityException) {
            // Android refused the microphone (iCode left the screen meanwhile): the service runs without it.
            BackendLog.w(TAG, "The microphone was refused (${refused.javaClass.simpleName})")
            startForeground(NOTIFICATION, notification(), base)
            false
        }
        started = true
        heldMicrophone.value = held
    }

    private fun notification(): Notification {
        fun action(title: String, action: String) = Notification.Action.Builder(
            null, title,
            PendingIntent.getService(this, action.hashCode(), Intent(this, PhoneForegroundService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE),
        ).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.notification_accent))
            .setContentTitle(if (shown.working != null) "Assistant working" else "Phone control is on")
            .setContentText(shown.working ?: "The assistant can use this phone")
            .setContentIntent(WorkNotifications.open(this, BackgroundPlace.Assistant))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .apply {
                if (shown.working != null) addAction(action("Stop", ACTION_STOP))
                if (!shown.bubble) addAction(action("Show bubble", ACTION_SHOW_BUBBLE))
                addAction(action("Turn off", ACTION_TURN_OFF))
            }
            .build()
    }

    companion object {
        private const val TAG = "PhoneForegroundService"
        private const val CHANNEL = "icode.phone-control"
        // Clear of the terminal's (4711, 5000s, 6000s) and the work's (7000s to 9000s).
        private const val NOTIFICATION = 10_000
        private const val EXTRA_MICROPHONE = "microphone"
        private const val ACTION_STOP = "dev.icode.mobile.phone.STOP"
        private const val ACTION_SHOW_BUBBLE = "dev.icode.mobile.phone.SHOW_BUBBLE"
        private const val ACTION_TURN_OFF = "dev.icode.mobile.phone.TURN_OFF"

        private val heldMicrophone = MutableStateFlow(false)

        /** Asked for while iCode was on screen: a later start without it must not take it back. */
        @Volatile private var wanted = false

        @Volatile private var active = false

        /** It runs: created, and not yet destroyed. */
        internal val running: Boolean get() = active

        /** The service holds the microphone: the bubble may listen while iCode is away. */
        internal val microphone: StateFlow<Boolean> = heldMicrophone.asStateFlow()

        /** Started, or started again with the microphone now that iCode is on screen. */
        internal fun start(context: Context, microphone: Boolean) {
            if (microphone) wanted = true
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, PhoneForegroundService::class.java).putExtra(EXTRA_MICROPHONE, microphone))
            }.onFailure { BackendLog.w(TAG, "Phone control's service could not start (${it.javaClass.simpleName})") }
        }

        internal fun stop(context: Context) {
            context.stopService(Intent(context, PhoneForegroundService::class.java))
        }
    }
}
