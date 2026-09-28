package dev.icode.mobile.ui.editor

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.icode.mobile.designsystem.IcodeColors
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin

/** Native Markdown rendering; preview zoom does not change the editor setting or document. */
@Composable
fun MarkdownPreview(
    text: String,
    fontSizeSp: Int,
    fontScale: Float,
    onScale: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val textColor = IcodeColors.TextPrimary.toArgb()
    val linkColor = IcodeColors.Blue.toArgb()
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .build()
    }
    Box(modifier.fillMaxSize().background(IcodeColors.BackgroundDeep).verticalScroll(rememberScrollState())) {
        AndroidView(
            factory = {
                ScalableMarkdownTextView(it).apply {
                    setTextColor(textColor)
                    setLinkTextColor(linkColor)
                    val density = resources.displayMetrics.density
                    setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (24 * density).toInt())
                }
            },
            update = {
                it.onScale = onScale
                it.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp * fontScale)
                it.setTextColor(textColor)
                it.setLinkTextColor(linkColor)
                if (it.renderedMarkdown != text) {
                    markwon.setMarkdown(it, text)
                    it.renderedMarkdown = text
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private class SelectableLinkMovementMethod : LinkMovementMethod() {
    override fun canSelectArbitrarily(): Boolean = true
}

internal class ScalableMarkdownTextView(context: Context) : TextView(context) {
    var renderedMarkdown: String? = null
    var onScale: (Float) -> Unit = {}
    private var handlingPinch = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            onScale(detector.scaleFactor)
            return true
        }
    })

    init {
        setTextIsSelectable(true)
        // Keep native selection handles while retaining Markwon link taps.
        movementMethod = SelectableLinkMovementMethod()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && event.pointerCount >= 2) {
            handlingPinch = true
            parent?.requestDisallowInterceptTouchEvent(true)
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.onTouchEvent(cancel)
            cancel.recycle()
        }
        if (handlingPinch) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                handlingPinch = false
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    // A single tap reaches TextView's own handling above, which clicks through
    // here — as do accessibility services; a two-finger pinch is not a click.
    override fun performClick(): Boolean = super.performClick()
}
