package dev.icode.mobile.data.phone

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.notify.IcodeChannel
import dev.icode.mobile.data.notify.IcodeNotifications
import dev.icode.mobile.data.notify.NotificationKey
import dev.icode.mobile.data.notify.NotificationLink
import dev.icode.mobile.data.workspace.AndroidICodeGateway
import dev.icode.mobile.data.workspace.ICodeProcess
import dev.icode.mobile.domain.BackgroundPlace
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
 * without the microphone ([microphone] false), and the bubble says so. Its notification says phone
 * control is on, with Show bubble while the bubble is put away, and Turn off. A turn in hand is not
 * repeated here: it is the work's own notification, with its step, Stop and its question.
 */
class PhoneForegroundService : Service() {
    private val scope = MainScope()
    private lateinit var gateway: AndroidICodeGateway
    private lateinit var shade: IcodeNotifications
    /** The bubble is out: the notification offers Show bubble only while it is put away. */
    private var bubble = true
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        shade = IcodeNotifications(this)
        // Android's deadline is five seconds from startForegroundService(): take the foreground
        // here, before attaching to the process, so a busy main thread cannot miss it. A start()
        // upgrades to the microphone in onStartCommand; a notification's action keeps the base.
        runCatching { startForeground(NotificationKey.PhoneControl.id, notification(), baseType()) }
            .onFailure { BackendLog.w(TAG, "Phone control's service could not take the foreground (${it.javaClass.simpleName})") }
        gateway = ICodeProcess.attach(this)
        active = true
        scope.launch {
            gateway.snapshot.map { it.phone.bubble }.distinctUntilChanged().collect {
                bubble = it
                if (started) shade.post(NotificationKey.PhoneControl, notification())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
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
        val base = baseType()
        val held = try {
            startForeground(NotificationKey.PhoneControl.id, notification(), base or (if (allowed) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0))
            allowed
        } catch (refused: SecurityException) {
            // Android refused the microphone (iCode left the screen meanwhile): the service runs without it.
            BackendLog.w(TAG, "The microphone was refused (${refused.javaClass.simpleName})")
            startForeground(NotificationKey.PhoneControl.id, notification(), base)
            false
        }
        started = true
        heldMicrophone.value = held
    }

    private fun baseType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun notification(): Notification {
        fun action(title: String, action: String) =
            shade.action(title, shade.toService(PhoneForegroundService::class.java, action, "icode://phone/$action"))
        return shade.builder(IcodeChannel.PhoneControl)
            .setContentTitle("Phone control is on")
            .setContentText("The assistant can use this phone")
            .setContentIntent(shade.open(NotificationLink.Work(BackgroundPlace.Assistant)))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .apply {
                if (!bubble) addAction(action("Show bubble", ACTION_SHOW_BUBBLE))
                addAction(action("Turn off", ACTION_TURN_OFF))
            }
            .build()
    }

    companion object {
        private const val TAG = "PhoneForegroundService"
        private const val EXTRA_MICROPHONE = "microphone"
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
