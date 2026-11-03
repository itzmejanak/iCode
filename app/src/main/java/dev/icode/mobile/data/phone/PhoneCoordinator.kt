package dev.icode.mobile.data.phone

import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import dev.icode.mobile.data.phone.shizuku.ShizukuShell
import dev.icode.mobile.data.runtime.TerminalSessionManager
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.PhoneIntent
import dev.icode.mobile.domain.phone.PhoneAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Phone control (docs/phone-agent-plan.md §5.9, §5.11): what iCode may do on the phone, read live into
 * the snapshot; Shizuku's permission and its one-tap service; and whether the bubble is out. The
 * assistant's tools reach the phone through [control].
 */
internal class PhoneCoordinator(
    context: Context,
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val shizuku = ShizukuShell(app)
    private val preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    val control: PhoneControl = AndroidPhoneControl(PhoneLauncher(app), shizuku, app.packageName)

    /** iCode's service as Android's list of enabled services names it. */
    private val component = ComponentName(app, PhoneService::class.java).flattenToString()

    /**
     * Read again whenever the service connects or goes, and as Shizuku comes and goes. While it is on,
     * the foreground service runs, started again with the microphone each time iCode is on screen.
     */
    fun start() {
        scope.launch { PhoneService.current.collect { refresh() } }
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { refresh() }
        val onScreen = TerminalSessionManager.get(app).screenVisible
        val microphone = stateHolder.snapshot.map { it.voice.microphone }
        scope.launch {
            combine(PhoneService.current.map { it != null }, onScreen, microphone) { on, front, allowed -> Triple(on, front, allowed) }
                .distinctUntilChanged()
                .collect { (on, front, _) ->
                    when {
                        !on -> PhoneForegroundService.stop(app)
                        front -> PhoneForegroundService.start(app, microphone = true)
                        // Away, a running service keeps the microphone it holds; one Android stopped comes back without it.
                        !PhoneForegroundService.running -> PhoneForegroundService.start(app, microphone = false)
                    }
                }
        }
    }

    fun handle(intent: PhoneIntent) {
        when (intent) {
            PhoneIntent.Refresh -> refresh()
            PhoneIntent.RequestShizuku -> shizuku.requestPermission { refresh() }
            PhoneIntent.TurnOnWithShizuku -> scope.launch(Dispatchers.IO) {
                if (!shizuku.turnOnService(component)) stateHolder.notify(NoticeTone.Problem, "Shizuku could not turn phone control on", "Turn it on in Android's Accessibility settings instead")
                refresh()
            }
            PhoneIntent.TurnOff -> PhoneService.current.value?.disableSelf()
            is PhoneIntent.ShowBubble -> {
                preferences.edit { putBoolean(BUBBLE, intent.shown) }
                refresh()
            }
        }
    }

    private fun refresh() = scope.launch(Dispatchers.IO) {
        val service = PhoneService.current.value != null
        val access = PhoneAccess(service = service, shizuku = shizuku.state(), bubble = service && preferences.getBoolean(BUBBLE, true))
        stateHolder.update { it.copy(phone = access) }
    }

    private companion object {
        const val PREFERENCES = "phone"
        const val BUBBLE = "bubble"
    }
}
