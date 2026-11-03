package dev.icode.mobile.ui.phone

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import dev.icode.mobile.domain.phone.PhoneOverlay

/**
 * The screen's edges in the accent, faded towards the middle (docs/phone-agent-plan.md §5.6): four thin
 * accessibility overlays that take no touch, so nothing of the screen is ever covered. It breathes
 * slowly while the agent acts — not when the system's animations are off — and dims while it waits.
 */
internal class PhoneGlow(private val context: Context, private val windows: WindowManager) {
    private val thickness = (THICKNESS_DP * context.resources.displayMetrics.density).toInt()
    private val strips = Edge.entries.map { Strip(context, it) }
    private var shown = false
    private var accent = 0
    private val breathing = ValueAnimator.ofFloat(BREATH_LOW, 1f).apply {
        duration = BREATH_MILLIS
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { animator -> strips.forEach { it.alpha = animator.animatedValue as Float * level } }
    }
    private var level = 1f

    fun show(glow: PhoneOverlay.Glow, accent: Int) {
        if (accent != this.accent) {
            this.accent = accent
            strips.forEach { it.invalidate() }
        }
        if (glow == PhoneOverlay.Glow.Off) return hide()
        level = if (glow == PhoneOverlay.Glow.Dim) DIM else 1f
        if (!shown) {
            strips.forEach { windows.addView(it, it.params()) }
            shown = true
        }
        if (glow == PhoneOverlay.Glow.On && ValueAnimator.areAnimatorsEnabled()) {
            if (!breathing.isStarted) breathing.start()
        } else {
            breathing.cancel()
            strips.forEach { it.alpha = level }
        }
    }

    fun hide() {
        breathing.cancel()
        if (!shown) return
        strips.forEach { runCatching { windows.removeViewImmediate(it) } }
        shown = false
    }

    /** Hidden for a screenshot, and back: the strips stay in place. */
    fun capture(hidden: Boolean) = strips.forEach { it.visibility = if (hidden) View.INVISIBLE else View.VISIBLE }

    private enum class Edge(val gravity: Int) {
        Left(Gravity.START or Gravity.TOP),
        Top(Gravity.TOP or Gravity.START),
        Right(Gravity.END or Gravity.TOP),
        Bottom(Gravity.BOTTOM or Gravity.START),
    }

    private inner class Strip(context: Context, private val edge: Edge) : View(context) {
        private val paint = Paint()

        fun params() = WindowManager.LayoutParams(
            if (edge == Edge.Left || edge == Edge.Right) thickness else WindowManager.LayoutParams.MATCH_PARENT,
            if (edge == Edge.Top || edge == Edge.Bottom) thickness else WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = edge.gravity
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "iCode glow"
        }

        override fun onDraw(canvas: Canvas) {
            val solid = (accent and 0x00FFFFFF) or (EDGE_ALPHA shl 24)
            val clear = accent and 0x00FFFFFF
            val w = width.toFloat()
            val h = height.toFloat()
            paint.shader = when (edge) {
                Edge.Left -> LinearGradient(0f, 0f, w, 0f, solid, clear, Shader.TileMode.CLAMP)
                Edge.Right -> LinearGradient(w, 0f, 0f, 0f, solid, clear, Shader.TileMode.CLAMP)
                Edge.Top -> LinearGradient(0f, 0f, 0f, h, solid, clear, Shader.TileMode.CLAMP)
                Edge.Bottom -> LinearGradient(0f, h, 0f, 0f, solid, clear, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, w, h, paint)
        }
    }

    private companion object {
        const val THICKNESS_DP = 22f
        /** The accent's strength at the very edge: a faded glow, never a border. */
        const val EDGE_ALPHA = 0x99
        const val DIM = 0.35f
        const val BREATH_LOW = 0.55f
        const val BREATH_MILLIS = 1_600L
    }
}
