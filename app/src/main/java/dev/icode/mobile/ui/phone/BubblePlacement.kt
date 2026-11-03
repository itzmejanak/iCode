package dev.icode.mobile.ui.phone

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.icode.mobile.designsystem.IcodeSizes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Where the bubble's window sits (docs/phone-agent-plan.md §5.7), as Android's own floating button sits:
 * against the nearer edge — its bar opening towards the middle — tucked half behind it and faded while
 * nothing goes on, out again at a touch or anything new; dragged anywhere, it snaps back to an edge.
 */
internal class BubblePlacement(private val windows: WindowManager, private val view: View, density: Float) {
    private val size = (IcodeSizes.Bubble.value * density).toInt()
    private val margin = (EDGE_MARGIN_DP * density).toInt()

    /** Against the right edge: the bar opens to the bubble's left. */
    var dockRight by mutableStateOf(true)
        private set

    /** Half behind the edge, and faded. */
    var tucked by mutableStateOf(false)
        private set

    private var dragging = false

    /** Nothing goes on, and the bar is closed: it tucks after a while. */
    private var resting = false
    private var tucking: Job? = null

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        FLAGS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = margin
        y = screen().second / 3
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        title = "iCode bubble"
    }

    /** The bar takes the keyboard; otherwise the window takes no focus from the app under it. */
    fun focusable(focus: Boolean) {
        params.flags = FLAGS or (if (focus) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        // The bar opens in the upper half, so the keyboard does not cover it.
        if (focus) params.y = params.y.coerceAtMost(screen().second / 2)
        apply()
    }

    /** Idle a while, it tucks half behind its edge, as Android's floating button does; anything new brings it out. */
    fun rest(idle: Boolean, scope: CoroutineScope) {
        if (idle == resting) return
        resting = idle
        tucking?.cancel()
        if (!idle) return tuck(false)
        tucking = scope.launch {
            delay(TUCK_MILLIS)
            tuck(true)
        }
    }

    private fun tuck(tuck: Boolean) {
        if (tucked == tuck || dragging) return
        tucked = tuck
        apply()
    }

    fun drag(dx: Float, dy: Float) {
        if (!dragging) {
            // From an edge's offset to the screen's: dragged, it goes where the finger goes; let go, it rests anew.
            dragging = true
            tucked = false
            resting = false
            tucking?.cancel()
            val left = if (dockRight) screen().first - view.width else 0
            params.gravity = Gravity.TOP or Gravity.START
            params.x = left
        }
        params.x += dx.toInt()
        params.y = (params.y + dy.toInt()).coerceIn(0, screen().second - size)
        attached { windows.updateViewLayout(view, params) }
    }

    /** Let go: it goes to the nearer edge. */
    fun drop() {
        if (!dragging) return
        dragging = false
        dockRight = params.x + view.width / 2 > screen().first / 2
        apply()
    }

    /** While dragged: the window's centre, in screen pixels, to tell whether it is over the target. */
    fun centre(): Pair<Int, Int> = (params.x + view.width / 2) to (params.y + view.height / 2)

    fun screen(): Pair<Int, Int> = windows.currentWindowMetrics.bounds.let { it.width() to it.height() }

    private fun apply() {
        params.gravity = Gravity.TOP or (if (dockRight) Gravity.END else Gravity.START)
        params.x = if (tucked) -size / 2 else margin
        attached { windows.updateViewLayout(view, params) }
    }

    private fun attached(block: () -> Unit) {
        if (view.isAttachedToWindow) block()
    }

    private companion object {
        const val EDGE_MARGIN_DP = 4f
        const val TUCK_MILLIS = 3_000L
        const val FLAGS = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    }
}
