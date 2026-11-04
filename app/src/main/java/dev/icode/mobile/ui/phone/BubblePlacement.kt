package dev.icode.mobile.ui.phone

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
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
 * Where the bubble's window sits (docs/phone-agent-plan.md §5.7; redesigned 2026-11-04, after Gemini):
 * anchored at the bottom of the screen, centred unless dragged aside along it. The window is centred on
 * its own middle, so the bar the bubble expands into grows from that middle and the bubble stays put
 * under it. Idle a while, it sinks half behind the bottom edge and fades; anything new brings it out.
 * Dragged anywhere, it drops back to the bottom. The bar holds the keyboard: the window, anchored above
 * it as it opens, needs no lifting of its own.
 */
internal class BubblePlacement(private val windows: WindowManager, private val view: View, density: Float) {
    private val size = (IcodeSizes.Bubble.value * density).toInt()
    private val scale = density
    private val side = (SIDE_MARGIN_DP * density).toInt()
    private val lift = (BOTTOM_MARGIN_DP * density).toInt()

    /** Half sunk behind the bottom edge, and faded. */
    var tucked by mutableStateOf(false)
        private set

    private var dragging = false

    /** Nothing goes on, and the bar is closed: it tucks after a while. */
    private var resting = false
    private var tucking: Job? = null

    /** Where the overlay's middle sits across the screen: the middle unless dragged aside. */
    private var centreX = -1f

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        BASE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        title = "iCode bubble"
    }

    init {
        apply()
    }

    /** The bar takes the keyboard — the window resizes above it — otherwise it takes no focus from the app under it. */
    fun focusable(focus: Boolean) {
        params.flags = BASE or (
            if (focus) 0
            else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        apply()
    }

    /** Idle a while, it sinks half behind the bottom edge, as Gemini's overlay recedes; anything new brings it out. */
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
            // From the bottom's offsets to the screen's: dragged, it goes where the finger goes; let go, it rests anew.
            dragging = true
            tucked = false
            resting = false
            tucking?.cancel()
            params.gravity = Gravity.TOP or Gravity.START
            params.x = anchoredLeft()
            params.y = screen().second - (view.height.takeIf { it > 0 } ?: size) - params.y
        }
        params.x += dx.toInt()
        params.y = (params.y + dy.toInt()).coerceIn(0, screen().second - size)
        attached { windows.updateViewLayout(view, params) }
    }

    /** Let go: it settles at the bottom, where it was let go across the screen. */
    fun drop() {
        if (!dragging) return
        dragging = false
        centreX = params.x + (view.width.takeIf { it > 0 } ?: size) / 2f
        apply()
    }

    /** While dragged: the window's centre, in screen pixels, to tell whether it is over the target. */
    fun centre(): Pair<Int, Int> = (params.x + view.width / 2) to (params.y + view.height / 2)

    fun screen(): Pair<Int, Int> = windows.currentWindowMetrics.bounds.let { it.width() to it.height() }

    /** Anchored at the bottom: the window centred on [centreX], clear of the gesture area, tucked half behind the edge when idle. */
    private fun apply() {
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        val (width, _) = screen()
        if (centreX < 0) centreX = width / 2f
        // The bar may open wider than the bubble: the whole overlay, from its middle, stays on screen.
        val panel = minOf(IcodeSizes.BubblePanel.value * scale, (width - 2 * side).toFloat()).coerceAtLeast(size.toFloat())
        val lowest = side + panel / 2f
        val highest = width - side - panel / 2f
        centreX = if (highest > lowest) centreX.coerceIn(lowest, highest) else width / 2f
        params.x = (centreX - width / 2f).toInt()
        params.y = if (tucked) -size / 2 else lift + inset()
        attached { windows.updateViewLayout(view, params) }
    }

    /** The window's left edge, in screen pixels, while it is anchored. */
    private fun anchoredLeft(): Int = (centreX - (view.width.takeIf { it > 0 } ?: size) / 2f).toInt()

    /** How far the bottom's system bars and cutout reach: the bubble sits above the gesture area. */
    private fun inset() = windows.currentWindowMetrics.windowInsets
        .getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).bottom

    private fun attached(block: () -> Unit) {
        if (view.isAttachedToWindow) block()
    }

    private companion object {
        const val SIDE_MARGIN_DP = 12f
        const val BOTTOM_MARGIN_DP = 10f
        const val TUCK_MILLIS = 3_000L
        const val BASE = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    }
}
