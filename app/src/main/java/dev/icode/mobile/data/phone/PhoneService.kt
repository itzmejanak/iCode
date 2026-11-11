package dev.icode.mobile.data.phone

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * iCode's accessibility service (docs/phone-agent-plan.md §5.1): what reads and operates the screen for
 * the assistant, and hosts its glow and bubble. It holds no chat and no model: the assistant reaches it
 * through [PhoneControl]. Android runs it while the user has it on in Settings › Accessibility, or
 * Shizuku turned it on for them.
 */
class PhoneService : AccessibilityService() {
    /** When a window of another app last changed: the screen has settled once this is quiet (§5.4). */
    @Volatile
    internal var lastEvent: Long = 0L
        private set

    /** The glow and the bubble, while the service runs ([overlayFactory]'s). */
    internal var overlays: PhoneOverlayHost? = null
        private set

    override fun onServiceConnected() {
        lastEvent = SystemClock.uptimeMillis()
        connected.value = this
        // Connected again without an unbind between: the earlier overlays are put away, not left drawing.
        overlays?.detach()
        overlays = overlayFactory?.invoke(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // iCode's own windows — the bubble, the glow — never count as the screen changing.
        if (event?.packageName?.toString() == packageName) return
        lastEvent = SystemClock.uptimeMillis()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        gone()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        gone()
        super.onDestroy()
    }

    private fun gone() {
        overlays?.detach()
        overlays = null
        connected.compareAndSet(this, null)
    }

    companion object {
        private val connected = MutableStateFlow<PhoneService?>(null)

        /** What draws the overlays: the UI's, registered by the app at its start; data never imports screens. */
        @Volatile
        var overlayFactory: ((PhoneService) -> PhoneOverlayHost)? = null

        /** The service while it is connected; null while phone control is off. */
        internal val current: StateFlow<PhoneService?> get() = connected
    }
}

/** The overlays a [PhoneService] hosts (docs/phone-agent-plan.md §5.6, §5.7): the glow and the bubble. */
interface PhoneOverlayHost {
    /** The service goes: every overlay with it. */
    fun detach()

    /** Hidden for a screenshot below Android 14, where it cannot be taken beneath them; back after. */
    suspend fun hideForCapture()

    suspend fun showAfterCapture()
}
