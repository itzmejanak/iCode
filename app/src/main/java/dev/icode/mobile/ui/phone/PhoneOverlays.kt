package dev.icode.mobile.ui.phone

import android.accessibilityservice.AccessibilityButtonController
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.icode.mobile.data.phone.PhoneOverlayHost
import dev.icode.mobile.data.phone.PhoneService
import dev.icode.mobile.data.runtime.TerminalSessionManager
import dev.icode.mobile.data.settings.AndroidWorkspacePreferencesRepository
import dev.icode.mobile.data.tasks.WorkNotifications
import dev.icode.mobile.data.voice.Voice
import android.widget.Toast
import dev.icode.mobile.data.workspace.ICodeProcess
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeTheme
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.BackgroundPlace
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.PhoneIntent
import dev.icode.mobile.domain.WorkspacePreferences
import dev.icode.mobile.domain.phone.PhoneOverlay
import dev.icode.mobile.ui.appearancePalette
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The glow and the bubble over every app (docs/phone-agent-plan.md §5.6, §5.7), in the phone service's
 * own trusted windows: no "draw over other apps" permission, and no touch of the screen blocked. They
 * show what the process's one backend says ([ICodeProcess]), so the assistant answers from any screen.
 */
internal class PhoneOverlays(private val service: PhoneService) : PhoneOverlayHost {
    private val gateway = ICodeProcess.attach(service)
    private val windows = service.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()
    private val scope = MainScope()
    private val glow = PhoneGlow(service, windows)
    private val bubble = ComposeView(service)
    private val target = ComposeView(service)
    private val density = service.resources.displayMetrics.density
    private val bubbleParams = params(focusable = false).apply {
        x = 0
        y = service.resources.displayMetrics.heightPixels / 3
    }

    private var overlay by mutableStateOf(PhoneOverlay(bubble = false, glow = PhoneOverlay.Glow.Off, working = false, asking = false, step = null))
    private var assistant by mutableStateOf(AssistantState())
    private var preferences by mutableStateOf(WorkspacePreferences(workspaceLocation = null, loaded = false))
    private var expanded by mutableStateOf(false)
    private var overTarget by mutableStateOf(false)
    private var bubbleOut = false
    private var targetOut = false

    private val button = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) {
            if (!overlay.bubble) gateway.dispatch(PhoneIntent.ShowBubble(true))
            expand(true)
        }
    }

    init {
        owner.start()
        listOf(bubble, target).forEach {
            it.setViewTreeLifecycleOwner(owner)
            it.setViewTreeSavedStateRegistryOwner(owner)
        }
        bubble.setContent { Themed { PhoneBubble(overlay, assistant, expanded, actions()) } }
        target.setContent { Themed { PhoneBubbleTarget(overlay.working, overTarget) } }
        bubble.setOnTouchListener(::outside)
        val visible = TerminalSessionManager.get(service).screenVisible
        val appearance = AndroidWorkspacePreferencesRepository(service).values(null)
        scope.launch {
            combine(gateway.snapshot, visible, appearance) { snapshot, onScreen, prefs -> Triple(snapshot, onScreen, prefs) }.collect { (snapshot, onScreen, prefs) ->
                assistant = snapshot.assistant
                preferences = prefs
                show(PhoneOverlay.of(snapshot.assistant, snapshot.phone, onScreen))
            }
        }
        service.accessibilityButtonController.registerAccessibilityButtonCallback(button)
    }

    override fun detach() {
        service.accessibilityButtonController.unregisterAccessibilityButtonCallback(button)
        scope.cancel()
        glow.hide()
        listOfNotNull(bubble.takeIf { bubbleOut }, target.takeIf { targetOut }).forEach { runCatching { windows.removeViewImmediate(it) } }
        owner.stop()
        ICodeProcess.detach()
    }

    override suspend fun hideForCapture() = withContext(Dispatchers.Main) {
        glow.capture(hidden = true)
        bubble.visibility = View.INVISIBLE
        // A frame for the windows to be drawn without them.
        delay(FRAME_MILLIS)
    }

    override suspend fun showAfterCapture() = withContext(Dispatchers.Main) {
        glow.capture(hidden = false)
        bubble.visibility = View.VISIBLE
    }

    private fun show(next: PhoneOverlay) {
        overlay = next
        glow.show(next.glow, appearancePalette(preferences.appearance.takeIf { preferences.loaded }).accent.toArgb())
        if (next.bubble && !bubbleOut) {
            windows.addView(bubble, bubbleParams)
            bubbleOut = true
        } else if (!next.bubble && bubbleOut) {
            expand(false)
            windows.removeViewImmediate(bubble)
            bubbleOut = false
        }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        val appearance = preferences.appearance.takeIf { preferences.loaded }
        IcodeTheme(palette = appearancePalette(appearance), uiTextScalePercent = appearance?.uiTextScalePercent ?: 100, content = content)
    }

    private fun actions() = PhoneBubbleActions(
        expand = ::expand,
        drag = ::drag,
        dragEnd = ::dropped,
        dispatch = gateway::dispatch,
        open = {
            expand(false)
            // Started straight from the service: a bound accessibility service may start activities from the background.
            service.startActivity(WorkNotifications.intentFor(service, BackgroundPlace.Assistant))
        },
        listen = { onHeard ->
            Voice.listen(service, onHeard) { problem -> Toast.makeText(service, problem, Toast.LENGTH_SHORT).show() }
        },
    )

    /** Open, the panel takes the keyboard; closed, the mark takes no focus from the app under it. */
    private fun expand(open: Boolean) {
        expanded = open
        if (!bubbleOut) return
        val metrics = service.resources.displayMetrics
        bubbleParams.flags = params(focusable = open).flags
        if (open) {
            // The panel opens from the upper half, so it fits above the keyboard.
            bubbleParams.x = (MARGIN_DP * density).toInt()
            bubbleParams.y = bubbleParams.y.coerceIn(0, metrics.heightPixels / 2)
        } else {
            bubbleParams.x = snapped(bubbleParams.x, metrics.widthPixels)
        }
        windows.updateViewLayout(bubble, bubbleParams)
    }

    private fun drag(dx: Float, dy: Float) {
        bubbleParams.x += dx.toInt()
        bubbleParams.y += dy.toInt()
        windows.updateViewLayout(bubble, bubbleParams)
        if (!targetOut) {
            windows.addView(target, params(focusable = false).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (TARGET_MARGIN_DP * density).toInt()
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            })
            targetOut = true
        }
        val metrics = service.resources.displayMetrics
        val size = IcodeSizes.Bubble.value * density
        val targetY = metrics.heightPixels - (TARGET_MARGIN_DP + IcodeSizes.Bubble.value / 2) * density
        overTarget = hypot(bubbleParams.x + size / 2 - metrics.widthPixels / 2f, bubbleParams.y + size / 2 - targetY) < size * 1.5f
    }

    /** Let go: on the target, put away — and a turn on the phone stopped — else snapped to the nearer edge. */
    private fun dropped() {
        if (targetOut) {
            windows.removeViewImmediate(target)
            targetOut = false
        }
        if (overTarget) {
            overTarget = false
            if (overlay.working) {
                Voice.stopped = true
                gateway.dispatch(ICodeIntent.StopAssistant)
            }
            Voice.stopSpeaking()
            gateway.dispatch(PhoneIntent.ShowBubble(false))
            return
        }
        bubbleParams.x = snapped(bubbleParams.x, service.resources.displayMetrics.widthPixels)
        windows.updateViewLayout(bubble, bubbleParams)
    }

    private fun snapped(x: Int, width: Int): Int {
        val size = (IcodeSizes.Bubble.value * density).toInt()
        return if (x + size / 2 < width / 2) 0 else width - size
    }

    /** A touch outside the open panel closes it, and goes on to the app under it. */
    @SuppressLint("ClickableViewAccessibility")
    private fun outside(view: View, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE && expanded) expand(false)
        return false
    }

    private fun params(focusable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        title = "iCode bubble"
    }

    private companion object {
        const val MARGIN_DP = 12f
        const val TARGET_MARGIN_DP = 48f
        const val FRAME_MILLIS = 50L
    }
}
