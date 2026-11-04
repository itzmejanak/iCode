package dev.icode.mobile.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A surface in [color], its [line] at its edge and its content clipped to it, with corners [corner]
 * round — and with a [tab] (the owner's design, 2026-11-04: the bubble's bar, a message's files), a
 * tab rising from its top at the end, the size its content measured, the surface's top edge curving
 * up into it. The tab's size is written as it is measured and read as the surface is drawn, so the
 * outline follows it in the same frame, as it grows too.
 */
internal fun Modifier.tabbedSurface(tab: MutableState<IntSize>?, corner: Dp, color: Color, line: Color?): Modifier = drawWithCache {
    val size = tab?.value
    val shape = if (size == null || size == IntSize.Zero) RoundedCornerShape(corner) else TabbedShape(size.width.toFloat(), size.height.toFloat(), corner.toPx())
    val fill = Path().apply { addOutline(shape.createOutline(this@drawWithCache.size, layoutDirection, this@drawWithCache)) }
    val stroke = 1.dp.toPx()
    // The edge line inside the outline, as Modifier.border draws it.
    val edge = line?.let {
        Path().apply { addOutline(shape.createOutline(Size(this@drawWithCache.size.width - stroke, this@drawWithCache.size.height - stroke), layoutDirection, this@drawWithCache)) }
    }
    onDrawWithContent {
        drawPath(fill, color)
        clipPath(fill) { this@onDrawWithContent.drawContent() }
        if (edge != null && line != null) translate(stroke / 2, stroke / 2) { drawPath(edge, line, style = Stroke(stroke)) }
    }
}

/**
 * A surface with a tab [tabWidth] px wide rising [rise] px from its top at the end: the top edge
 * curves up into the tab over as long a run as the tab is tall, and the tab's outer corner is as round
 * as the surface's [corner]s. A tab too wide for the surface starts its curve at the start corner.
 */
private data class TabbedShape(val tabWidth: Float, val rise: Float, val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = corner.coerceAtMost((h - rise) / 2).coerceAtMost(rise)
        val tabStart = (w - tabWidth).coerceAtLeast(r + rise)
        val path = Path().apply {
            moveTo(0f, rise + r)
            arcTo(Rect(0f, rise, 2 * r, rise + 2 * r), 180f, 90f, false)
            lineTo(tabStart - rise, rise)
            cubicTo(tabStart - rise / 2, rise, tabStart - rise / 2, 0f, tabStart, 0f)
            lineTo(w - r, 0f)
            arcTo(Rect(w - 2 * r, 0f, w, 2 * r), 270f, 90f, false)
            lineTo(w, h - r)
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            close()
        }
        // Drawn with the tab at the right; read right to left, it is at the left.
        if (layoutDirection == LayoutDirection.Rtl) path.transform(Matrix().apply { scale(-1f, 1f); translate(-w, 0f) })
        return Outline.Generic(path)
    }
}
