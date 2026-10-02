package dev.icode.mobile.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.text.Spanned
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
import io.noties.markwon.ext.tables.TableRowSpan
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
    val textColor = IcodeColors.TextPrimary.toArgb()
    val linkColor = IcodeColors.Blue.toArgb()
    val markwon = rememberMarkwon()
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
                it.setTextColor(textColor)
                it.setLinkTextColor(linkColor)
                it.show(markwon, text, fontSizeSp * fontScale)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Markdown as running text in a page that scrolls by itself — the assistant's answers: the same
 * renderer as the preview, selectable, links followed, without the preview's own scroll and zoom.
 */
@Composable
fun MarkdownText(text: String, fontSizeSp: Float, modifier: Modifier = Modifier) {
    val textColor = IcodeColors.TextPrimary.toArgb()
    val linkColor = IcodeColors.Blue.toArgb()
    val markwon = rememberMarkwon()
    AndroidView(
        factory = { ScalableMarkdownTextView(it) },
        update = {
            it.setTextColor(textColor)
            it.setLinkTextColor(linkColor)
            it.show(markwon, text, fontSizeSp)
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** One Markdown renderer per context: CommonMark with tables, strikethrough and task lists. */
@Composable
private fun rememberMarkwon(): Markwon {
    val context = LocalContext.current
    return remember(context) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .build()
    }
}

private class SelectableLinkMovementMethod : LinkMovementMethod() {
    override fun canSelectArbitrarily(): Boolean = true
}

internal class ScalableMarkdownTextView(context: Context) : TextView(context) {
    private var renderedMarkdown: String? = null
    private var renderedSize = 0f
    var onScale: (Float) -> Unit = {}

    /**
     * [markdown] at [sizeSp]. Text, headings and lists read the size as they are drawn; a table does
     * not: Markwon's `TableRowSpan` lays its cells out once, again only when the width it is drawn
     * at changes (4.6.2, `recreateLayouts`). A new size — a pinch — renders a table anew, its cells
     * laid out at that size.
     */
    fun show(markwon: Markwon, markdown: String, sizeSp: Float) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (markdown != renderedMarkdown || (sizeSp != renderedSize && holdsTable())) {
            markwon.setMarkdown(this, markdown)
            renderedMarkdown = markdown
        }
        renderedSize = sizeSp
    }

    private fun holdsTable(): Boolean = (text as? Spanned)?.let { it.getSpans(0, it.length, TableRowSpan::class.java).isNotEmpty() } == true
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

    // A single tap goes to TextView's own onTouchEvent, which calls performClick
    // (selectable text is clickable); a two-finger pinch is not a click. Lint
    // only sees a performClick call written here.
    @SuppressLint("ClickableViewAccessibility")
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
}
