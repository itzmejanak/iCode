package dev.icode.mobile.ui.editor

import android.os.SystemClock
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableRowSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownPreviewInteractionTest {
    @Test
    fun previewKeepsNativeTextSelectionAndLinks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = ScalableMarkdownTextView(instrumentation.targetContext)
            view.text = "Selectable Markdown"
            assertTrue(view.isTextSelectable)
            assertTrue(view.movementMethod is LinkMovementMethod)
            assertTrue(view.movementMethod.canSelectArbitrarily())
            Selection.setSelection(view.text as Spannable, 0, 10)
            assertEquals(0, view.selectionStart)
            assertEquals(10, view.selectionEnd)
        }
    }

    @Test
    fun tableRowsAreLaidOutBeforeFirstDraw() {
        // Streaming updates the answer tens of times a second; each chunk must render the table at
        // its real width on the very first layout pass (Markwon's own repair races the next chunk).
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = ScalableMarkdownTextView(instrumentation.targetContext)
            val size = 1000
            view.measure(
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, size, size)
            val markwon = Markwon.builder(instrumentation.targetContext)
                .usePlugin(TablePlugin.create(instrumentation.targetContext))
                .build()
            view.show(markwon, "| a | b |\n|---|---|\n| c | d |", 14f)

            val spanned = view.text as Spanned
            val row = spanned.getSpans(0, spanned.length, TableRowSpan::class.java).first()
            val widthField = TableRowSpan::class.java.getDeclaredField("width").apply { isAccessible = true }
            val layoutsField = TableRowSpan::class.java.getDeclaredField("layouts").apply { isAccessible = true }
            assertEquals(view.width - view.paddingLeft - view.paddingRight, widthField.getInt(row))
            @Suppress("UNCHECKED_CAST")
            val layouts = layoutsField.get(row) as List<Layout>
            assertEquals(2, layouts.size)
            assertTrue("Rows must be laid out before the first draw", layouts.all { it.height > 0 })
        }
    }

    @Test
    fun twoFingerPinchReportsScaleWithoutReplacingSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = ScalableMarkdownTextView(instrumentation.targetContext)
            view.text = "Keep this text"
            view.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 1000, 1000)
            val factors = mutableListOf<Float>()
            view.onScale = factors::add
            val start = SystemClock.uptimeMillis()
            view.sendTouch(start, start, MotionEvent.ACTION_DOWN, 300f to 300f)
            view.sendTouch(
                start,
                start + 16,
                MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                300f to 300f,
                700f to 300f,
            )
            view.sendTouch(start, start + 32, MotionEvent.ACTION_MOVE, 200f to 300f, 800f to 300f)
            view.sendTouch(start, start + 48, MotionEvent.ACTION_MOVE, 100f to 300f, 900f to 300f)
            view.sendTouch(
                start,
                start + 64,
                MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                100f to 300f,
                900f to 300f,
            )
            view.sendTouch(start, start + 80, MotionEvent.ACTION_UP, 100f to 300f)
            assertTrue("A pinch should increase the preview scale; factors=$factors", factors.any { it > 1f })
            assertEquals("Keep this text", view.text.toString())
        }
    }
}

private fun View.sendTouch(downTime: Long, eventTime: Long, action: Int, vararg points: Pair<Float, Float>) {
    val properties = Array(points.size) { index ->
        MotionEvent.PointerProperties().apply {
            id = index
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
    }
    val coordinates = Array(points.size) { index ->
        MotionEvent.PointerCoords().apply {
            x = points[index].first
            y = points[index].second
            pressure = 1f
            size = 1f
        }
    }
    val event = MotionEvent.obtain(
        downTime,
        eventTime,
        action,
        points.size,
        properties,
        coordinates,
        0,
        0,
        1f,
        1f,
        0,
        0,
        InputDevice.SOURCE_TOUCHSCREEN,
        0,
    )
    try {
        onTouchEvent(event)
    } finally {
        event.recycle()
    }
}
