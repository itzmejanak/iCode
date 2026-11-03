package dev.icode.mobile.data.phone

import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import dev.icode.mobile.data.phone.shizuku.ShizukuShell
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.PhoneIntent
import dev.icode.mobile.domain.phone.PhoneAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    /** Read again whenever the service connects or goes, and as Shizuku comes and goes. */
    fun start() {
        scope.launch { PhoneService.current.collect { refresh() } }
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { refresh() }
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
