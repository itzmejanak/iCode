package dev.icode.mobile.ui.phone

import android.content.SharedPreferences
import android.graphics.Insets
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import dev.icode.mobile.designsystem.IcodeSizes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The edges the bubble docks on. Not the top: the status bar is there, and the target that puts it away. */
internal enum class BubbleEdge { Bottom, Left, Right }

/**
 * Where the bubble's window sits (docs/phone-agent-plan.md §5.7): docked on the bottom edge or a side,
 * where it was let go along it, and kept there for the next time it shows. At the bottom the window
 * is centred on its own middle, so the bar the bubble expands into grows from that middle; on a side
 * it grows away from the edge. The bar holds the keyboard, so on a side it opens at the bottom, as on
 * the bottom edge: the keyboard is there. Idle a while, the bubble sinks half behind its edge and
 * fades; anything new brings it out. Dragged anywhere, it docks on the nearest edge.
 */
internal class BubblePlacement(private val windows: WindowManager, private val view: View, density: Float, private val saved: SharedPreferences) {
    private val size = (IcodeSizes.Bubble.value * density).toInt()
    private val panel = IcodeSizes.BubblePanel.value * density
    private val side = (SIDE_MARGIN_DP * density).toInt()
    private val lift = (BOTTOM_MARGIN_DP * density).toInt()

    /** The edge it docks on, as it was last let go. */
    var edge by mutableStateOf(BubbleEdge.entries.firstOrNull { it.name == saved.getString(KEY_EDGE, null) } ?: BubbleEdge.Bottom)
        private set

    /** Where along [edge] it sits, as a share of the screen: its middle across at the bottom, down on a side. */
    private var along = saved.getFloat(KEY_ALONG, 0.5f)

    /** Half sunk behind its edge, and faded. */
    var tucked by mutableStateOf(false)
        private set

    private var dragging = false

    /** The bar is open, holding the keyboard. */
    private var typing = false

    /** Nothing goes on, and the bar is closed: it tucks after a while. */
    private var resting = false
    private var tucking: Job? = null

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        BASE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        title = "iCode bubble"
    }

    init {
        apply()
    }

    /** The bar takes the keyboard — the window resizes above it — otherwise it takes no focus from the app under it. */
    fun focusable(focus: Boolean) {
        typing = focus
        params.flags = BASE or (
            if (focus) 0
            else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        apply()
    }

    /** Idle a while, it sinks half behind its edge, as Gemini's overlay recedes; anything new brings it out. */
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
            // From the edge's offsets to the screen's: dragged, it goes where the finger goes; let go, it docks anew.
            dragging = true
            tucked = false
            resting = false
            tucking?.cancel()
            val (width, height) = screen()
            val (w, h) = measured()
            params.gravity = Gravity.TOP or Gravity.LEFT
            params.x = when (edge) {
                BubbleEdge.Bottom -> (width - w) / 2 + params.x
                BubbleEdge.Left -> params.x
                BubbleEdge.Right -> width - w - params.x
            }
            params.y = height - h - params.y
        }
        params.x += dx.toInt()
        params.y = (params.y + dy.toInt()).coerceIn(0, screen().second - size)
        attached { windows.updateViewLayout(view, params) }
    }

    /**
     * Let go: it docks on the nearest of the bottom and the sides, where it was let go along it, and
     * keeps that for the next time — unless it was let go on the target, which puts it away: it comes
     * back where it was.
     */
    fun drop(away: Boolean) {
        if (!dragging) return
        dragging = false
        if (!away) {
            val (width, height) = screen()
            val (w, h) = measured()
            edge = listOf(
                BubbleEdge.Bottom to height - params.y - h,
                BubbleEdge.Left to params.x,
                BubbleEdge.Right to width - params.x - w,
            ).minBy { it.second }.first
            // The bubble sits at the bottom of the window: its middle is half its size above the window's bottom.
            along = if (edge == BubbleEdge.Bottom) (params.x + w / 2f) / width else (params.y + h - size / 2f) / height
            saved.edit { putString(KEY_EDGE, edge.name).putFloat(KEY_ALONG, along) }
        }
        apply()
    }

    /** While dragged: the window's centre, in screen pixels, to tell whether it is over the target. */
    fun centre(): Pair<Int, Int> = (params.x + view.width / 2) to (params.y + view.height / 2)

    fun screen(): Pair<Int, Int> = windows.currentWindowMetrics.bounds.let { it.width() to it.height() }

    /**
     * Docked on [edge], [along] it, clear of the system bars and the gesture area; tucked half behind
     * the edge when idle. The window grows up from the bubble's bottom and away from its edge, so the
     * voice bubble opens from where it sits; the bar too at the bottom, and on a side at the bottom of it.
     */
    private fun apply() {
        val (width, height) = screen()
        val insets = insets()
        val sunk = -size / 2
        // Off the bottom edge as the bubble sits there; the bar, holding the keyboard, sits there on any edge.
        val low = lift + insets.bottom
        if (edge == BubbleEdge.Bottom) {
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            // The bar may open wider than the bubble: the whole overlay, from its middle, stays on screen.
            val half = minOf(panel, (width - 2 * side).toFloat()).coerceAtLeast(size.toFloat()) / 2f
            val lowest = side + half
            val highest = width - side - half
            val centre = if (highest > lowest) (along * width).coerceIn(lowest, highest) else width / 2f
            params.x = (centre - width / 2f).toInt()
            params.y = if (tucked) sunk else low
        } else {
            params.gravity = Gravity.BOTTOM or if (edge == BubbleEdge.Left) Gravity.LEFT else Gravity.RIGHT
            params.x = if (tucked) sunk else side + if (edge == BubbleEdge.Left) insets.left else insets.right
            // Its bottom, from the screen's: no lower than on the bottom edge, no higher than under the top's bars.
            val highest = (height - insets.top - side - size).coerceAtLeast(low)
            params.y = if (typing) low else (height - along * height - size / 2f).toInt().coerceIn(low, highest)
        }
        attached { windows.updateViewLayout(view, params) }
    }

    /** The window's size as laid out, or the bubble's before it is. */
    private fun measured(): Pair<Int, Int> = (view.width.takeIf { it > 0 } ?: size) to (view.height.takeIf { it > 0 } ?: size)

    /** How far the system bars and the cutout reach on each side. */
    private fun insets(): Insets = windows.currentWindowMetrics.windowInsets
        .getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())

    private fun attached(block: () -> Unit) {
        if (view.isAttachedToWindow) block()
    }

    private companion object {
        const val SIDE_MARGIN_DP = 12f
        const val BOTTOM_MARGIN_DP = 10f
        const val TUCK_MILLIS = 3_000L
        const val BASE = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        const val KEY_EDGE = "edge"
        const val KEY_ALONG = "along"
    }
}
