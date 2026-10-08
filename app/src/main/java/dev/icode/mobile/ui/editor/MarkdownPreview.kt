package dev.icode.mobile.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.TextView
import java.lang.reflect.Field
import java.lang.reflect.Method
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
     * not: Markwon's `TableRowSpan` lays its cells out once, once the width it is drawn at changes
     * (4.6.2, `recreateLayouts`). A new size — a pinch — renders a table anew, its cells laid out
     * at that size.
     *
     * Rendering always pre-lays any table rows at the text area's real width, before the text is
     * set. Markwon itself is two-pass: a row measures at width 0 on the first layout, gets its real
     * width (and height) only when drawn, then an `Invalidator`'s `setText(same)` forces a correct
     * second layout. During streaming the second pass races the next chunk, so rows stay one line
     * tall and overlap. Setting each row's width before [setText] makes the first layout correct.
     */
    fun show(markwon: Markwon, markdown: String, sizeSp: Float) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (markdown != renderedMarkdown || (sizeSp != renderedSize && holdsTable())) {
            setRenderedMarkdown(markwon, markdown)
            renderedMarkdown = markdown
        }
        renderedSize = sizeSp
    }

    private fun setRenderedMarkdown(markwon: Markwon, markdown: String) {
        // `setMarkdown` = `setParsedMarkdown(toMarkdown(source))`; render first so the TableRowSpans
        // can be laid out at the text area's width before the text is set. `setParsedMarkdown`
        // keeps the plugins' hooks: CorePlugin attaches TextViewSpan (the width draw() measures a
        // row at), TablePlugin reprograms its rows' Invalidators.
        val available = width - paddingLeft - paddingRight
        if (available > 0) {
            val spanned = markwon.toMarkdown(markdown)
            layOutTableRows(spanned, available)
            markwon.setParsedMarkdown(this, spanned)
        } else {
            markwon.setMarkdown(this, markdown)
        }
    }

    /**
     * Markwon measures a `TableRowSpan` at its stored `width`; before the first draw that is 0, and
     * the row is one line tall. Laying each row out at [available] here makes `draw`'s
     * `recreateLayouts(width)` a no-op (width already matches), so the row is correct on the very
     * first layout pass instead of being repaired one frame late (which streaming never waits for).
     */
    private fun layOutTableRows(spanned: Spanned, available: Int) {
        val rows = spanned.getSpans(0, spanned.length, TableRowSpan::class.java)
        if (rows.isEmpty()) return
        // Markwon's draw() mutates its `textPaint` in place; do the same so the copy carries the
        // view's text size and colors when each cell's StaticLayout is created.
        val viewPaint = TextPaint(paint)
        for (row in rows) {
            try {
                TableRowSpans.WIDTH.setInt(row, available)
                (TableRowSpans.TEXT_PAINT.get(row) as TextPaint).set(viewPaint)
                TableRowSpans.MAKING_NEW_LAYOUTS.invoke(row)
            } catch (_: Exception) {
                // R8 renames or removes private library members in release builds; fall back to the
                // library's own two-pass layout (one frame off during streaming rather than a crash).
                return
            }
        }
    }

    private object TableRowSpans {
        private val width = reflectField("width")
        private val textPaint = reflectField("textPaint")
        private val makingNewLayouts = reflectMethod("makeNewLayouts")

        private fun reflectField(name: String): Field =
            TableRowSpan::class.java.getDeclaredField(name).apply { setAccessible(true) }

        private fun reflectMethod(name: String): Method =
            TableRowSpan::class.java.getDeclaredMethod(name).apply { setAccessible(true) }

        val WIDTH: Field = width
        val TEXT_PAINT: Field = textPaint
        val MAKING_NEW_LAYOUTS: Method = makingNewLayouts
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
