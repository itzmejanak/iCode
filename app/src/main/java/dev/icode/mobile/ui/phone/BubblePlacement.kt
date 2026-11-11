package dev.icode.mobile.ui.phone

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Insets
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import dev.icode.mobile.designsystem.IcodeResting
import dev.icode.mobile.designsystem.IcodeSizes
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The edges the bubble docks on. Not the top: the status bar is there, and the target that puts it away. */
internal enum class BubbleEdge { Bottom, Left, Right }

/**
 * An overlay window's root, telling [touched] each touch before its content has it: where the finger
 * is on the screen (`rawX`, `rawY`), which a window moved under it does not change.
 */
internal class OverlayFrame(context: Context, private val touched: (MotionEvent) -> Unit) : FrameLayout(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        touched(event)
        return super.dispatchTouchEvent(event)
    }
}

/**
 * Where the bubble's window, the bar's and the target's sit (docs/phone-agent-plan.md §5.7), in the
 * screen's own pixels — the finger's: what keeps them clear of the system bars is added here. The
 * bubble docks on the bottom edge or a side, where it was let go along it, kept for the next time it
 * shows. The bar and the voice bubble open in a window of their own at the bottom — where the bubble
 * sits on the bottom edge, in the middle from a side, by the keyboard — so the bubble's window never
 * moves for them. Dragged, the bubble follows the finger; let go, it glides to the nearest edge. Idle
 * a while, it glides half behind its edge and fades; anything new brings it out.
 */
internal class BubblePlacement(
    private val windows: OverlayWindows,
    private val view: View,
    private val bar: View,
    private val density: Float,
    private val saved: SharedPreferences,
) {
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

    /** Where the finger holds the window while it drags it, from the window's top left; null when none does. */
    private var grip: Pair<Float, Float>? = null
    val dragging: Boolean get() = grip != null

    /** Nothing goes on, and the bar is closed: it tucks after a while. */
    private var resting = false
    private var tucking: Job? = null
    private var gliding: ValueAnimator? = null

    val params = overlay(FLAGS).apply { title = "iCode bubble" }

    /** The bar's window: above the keyboard and the navigation bar, which lift it — and only from the bottom. */
    val barParams = overlay(BAR_FLAGS or CLOSED).apply {
        fitInsetsTypes = WindowInsets.Type.navigationBars() or WindowInsets.Type.ime()
        fitInsetsSides = WindowInsets.Side.BOTTOM
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        title = "iCode bubble bar"
    }

    /** The target dragging shows, under the status bar: the bubble let go on it is put away. */
    val targetParams = overlay(CLOSED or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        title = "iCode bubble target"
    }

    init {
        apply(animate = false)
    }

    /**
     * The bar's window opened for [mode], or closed: open, it takes touches — the keyboard only for the
     * bar, not the voice bubble — while the bubble's window takes none and stays where it is.
     */
    fun open(mode: BubbleMode) {
        params.flags = if (mode == BubbleMode.Closed) FLAGS else FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        barParams.flags = BAR_FLAGS or when (mode) {
            BubbleMode.Closed -> CLOSED
            BubbleMode.Typing -> 0
            BubbleMode.Voice -> WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        val (width, _) = screen()
        barParams.x = (centre(width) - width / 2f).toInt()
        barParams.y = lift
        update(view, params)
        update(bar, barParams)
    }

    /** Idle a while, it sinks half behind its edge, as Gemini's overlay recedes; anything new brings it out. */
    fun rest(idle: Boolean, scope: CoroutineScope) {
        if (idle == resting) return
        resting = idle
        tucking?.cancel()
        if (!idle) return tuck(false)
        tucking = scope.launch {
            delay(IcodeResting.AFTER_MILLIS)
            tuck(true)
        }
    }

    private fun tuck(tuck: Boolean) {
        if (tucked == tuck || dragging) return
        tucked = tuck
        apply()
    }

    /** The finger at ([x], [y]) on the screen: the window goes where it goes, held where it was taken. */
    fun dragTo(x: Float, y: Float) {
        if (grip == null) {
            gliding?.cancel()
            tucking?.cancel()
            resting = false
            tucked = false
            val (left, top) = topLeft()
            grip = (x - left) to (y - top)
            params.gravity = Gravity.TOP or Gravity.LEFT
            targetParams.y = insets().top + (TARGET_MARGIN_DP * density).toInt()
        }
        val (heldX, heldY) = grip ?: return
        params.x = (x - heldX).roundToInt()
        params.y = (y - heldY).roundToInt().coerceIn(0, screen().second - size)
        update(view, params)
    }

    /** While dragged: whether the bubble is over the target's circle, a bubble's size at its top. */
    fun overTarget(): Boolean {
        val (width, _) = screen()
        val (w, h) = measured()
        return hypot(params.x + w / 2f - width / 2f, params.y + h / 2f - targetParams.y - size / 2f) < size * TARGET_REACH
    }

    /**
     * Let go: it glides to the nearest of the bottom and the sides, where it was let go along it, and
     * keeps that for the next time — unless it was let go on the target, which puts it away: it comes
     * back where it was.
     */
    fun drop(away: Boolean) {
        if (grip == null) return
        grip = null
        // Rested again from now: what went on while it was held tucks it no sooner.
        resting = false
        if (!away) {
            val (width, height) = screen()
            val (w, h) = measured()
            edge = listOf(
                BubbleEdge.Bottom to height - params.y - h,
                BubbleEdge.Left to params.x,
                BubbleEdge.Right to width - params.x - w,
            ).minBy { it.second }.first
            along = if (edge == BubbleEdge.Bottom) (params.x + w / 2f) / width else (params.y + h / 2f) / height
            saved.edit { putString(KEY_EDGE, edge.name).putFloat(KEY_ALONG, along) }
        }
        apply(animate = !away)
    }

    /**
     * Docked on [edge], [along] it, clear of the system bars and the gesture area; tucked half behind
     * the edge when idle. [animate], it glides there from where it is.
     */
    private fun apply(animate: Boolean = true) {
        val (width, height) = screen()
        val insets = insets()
        val sunk = -size / 2
        val low = lift + insets.bottom
        val from = topLeft()
        if (edge == BubbleEdge.Bottom) {
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            params.x = (centre(width) - width / 2f).toInt()
            params.y = if (tucked) sunk else low
        } else {
            params.gravity = Gravity.BOTTOM or if (edge == BubbleEdge.Left) Gravity.LEFT else Gravity.RIGHT
            params.x = if (tucked) sunk else side + if (edge == BubbleEdge.Left) insets.left else insets.right
            // Its bottom, from the screen's: no lower than on the bottom edge, no higher than under the top's bars.
            val highest = (height - insets.top - side - size).coerceAtLeast(low)
            params.y = (height - along * height - size / 2f).toInt().coerceIn(low, highest)
        }
        gliding?.cancel()
        if (!animate || !view.isAttachedToWindow || view.width == 0 || !ValueAnimator.areAnimatorsEnabled()) return update(view, params)
        // In the gravity it docks in, from where it is to where it docks: it stays where it is until the glide moves it.
        val to = topLeft()
        place(from.first, from.second)
        gliding = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GLIDE_MILLIS
            interpolator = DecelerateInterpolator()
            addUpdateListener { glide ->
                val t = glide.animatedValue as Float
                place(from.first + (to.first - from.first) * t, from.second + (to.second - from.second) * t)
                update(view, params)
            }
            start()
        }
    }

    /** The middle the bubble on the bottom edge, and the bar, sit on: the whole of the panel on screen. */
    private fun centre(width: Int): Float {
        val half = minOf(panel, (width - 2 * side).toFloat()).coerceAtLeast(size.toFloat()) / 2f
        val lowest = side + half
        val highest = width - side - half
        return if (edge == BubbleEdge.Bottom && highest > lowest) (along * width).coerceIn(lowest, highest) else width / 2f
    }

    /** The window's top left on the screen, from its params in the gravity they hold, at its size as laid out. */
    private fun topLeft(): Pair<Float, Float> {
        val (width, height) = screen()
        val (w, h) = measured()
        val top = if (params.gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.BOTTOM) height - params.y - h else params.y
        val left = when (params.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.RIGHT -> width - params.x - w
            Gravity.CENTER_HORIZONTAL -> width / 2 + params.x - w / 2
            else -> params.x
        }
        return left.toFloat() to top.toFloat()
    }

    /** The params that put the window's top left at ([left], [top]), in the gravity they hold. */
    private fun place(left: Float, top: Float) {
        val (width, height) = screen()
        val (w, h) = measured()
        params.y = (if (params.gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.BOTTOM) height - top - h else top).roundToInt()
        params.x = when (params.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.RIGHT -> width - left - w
            Gravity.CENTER_HORIZONTAL -> left + w / 2f - width / 2f
            else -> left
        }.roundToInt()
    }

    fun screen(): Pair<Int, Int> = windows.manager.currentWindowMetrics.bounds.let { it.width() to it.height() }

    /** The window's size as laid out, or the bubble's before it is. */
    private fun measured(): Pair<Int, Int> = (view.width.takeIf { it > 0 } ?: size) to (view.height.takeIf { it > 0 } ?: size)

    /** How far the system bars and the cutout reach on each side. */
    private fun insets(): Insets = windows.manager.currentWindowMetrics.windowInsets
        .getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())

    private fun update(window: View, params: WindowManager.LayoutParams) = windows.update(window, params)

    private companion object {
        const val SIDE_MARGIN_DP = 12f
        const val BOTTOM_MARGIN_DP = 10f
        const val TARGET_MARGIN_DP = 48f
        /** How near the target's middle the bubble's is put away, in bubbles. */
        const val TARGET_REACH = 1.5f
        const val GLIDE_MILLIS = 220L
        const val FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        const val BAR_FLAGS = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        /** A window that takes nothing: no touch, no keyboard. */
        const val CLOSED = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        const val KEY_EDGE = "edge"
        const val KEY_ALONG = "along"

        /** A window that wraps its content, laid out on the whole screen: no system bar or cutout moves it. */
        fun overlay(flags: Int) = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }
}
