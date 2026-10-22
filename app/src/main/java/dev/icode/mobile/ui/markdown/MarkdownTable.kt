package dev.icode.mobile.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.edgeLine
import dev.icode.mobile.domain.markdown.MarkdownBlock

/**
 * A table (docs/markdown-rendering-plan.md §3.5): each column as wide as its widest cell, up to three
 * quarters of the page, its cells wrapping past that; each row as tall as its tallest cell, the others
 * centred in it, as a browser centres them. Wider than the page, it scrolls sideways. The header is
 * bold on a raised ground; cell lines follow Border Lines, and every other row is shaded, as GitHub
 * shades them, so rows read apart without lines too.
 */
@Composable
internal fun MarkdownTable(block: MarkdownBlock.Table, context: MarkdownContext, type: MarkdownType, modifier: Modifier = Modifier) {
    val columns = block.alignments.size
    if (columns == 0) return
    val rows = (if (block.header.isEmpty()) emptyList() else listOf(block.header)) + block.rows
    val line = edgeLine
    val header = IcodeColors.SurfaceElevated
    val shaded = IcodeColors.Surface
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widest = (maxWidth * COLUMN_SHARE)
        Box(
            Modifier.horizontalScroll(rememberScrollState())
                .clip(IcodeShapes.Small)
                .then(if (line != null) Modifier.border(1.dp, line, IcodeShapes.Small) else Modifier),
        ) {
            Layout(
                content = {
                    rows.forEachIndexed { r, cells ->
                        val heading = r == 0 && block.header.isNotEmpty()
                        val ground = when {
                            heading -> header
                            (r - if (block.header.isEmpty()) 0 else 1) % 2 == 1 -> shaded
                            else -> null
                        }
                        cells.forEachIndexed { c, cell ->
                            Box(
                                Modifier
                                    .then(if (ground != null) Modifier.background(ground) else Modifier)
                                    .drawBehind {
                                        if (line == null) return@drawBehind
                                        val stroke = 1.dp.toPx()
                                        if (r < rows.lastIndex) drawLine(line, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                                        if (c < columns - 1) drawLine(line, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
                                    }
                                    .padding(horizontal = IcodeSpacing.Space2, vertical = IcodeSpacing.Space1),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                CompositionLocalProvider(LocalMarkdownAlign provides block.alignments[c].textAlign) {
                                    MarkdownText(cell, if (heading) type.body.copy(fontWeight = FontWeight.Bold) else type.body, context, type, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                },
            ) { measurables, _ ->
                val cap = widest.roundToPx()
                val widths = IntArray(columns) { c -> rows.indices.maxOf { r -> measurables[r * columns + c].maxIntrinsicWidth(Constraints.Infinity) }.coerceAtMost(cap) }
                val heights = IntArray(rows.size) { r -> (0 until columns).maxOf { c -> measurables[r * columns + c].minIntrinsicHeight(widths[c]) } }
                val placeables = measurables.mapIndexed { at, cell -> cell.measure(Constraints.fixed(widths[at % columns], heights[at / columns])) }
                layout(widths.sum(), heights.sum()) {
                    var y = 0
                    rows.indices.forEach { r ->
                        var x = 0
                        (0 until columns).forEach { c ->
                            placeables[r * columns + c].place(x, y)
                            x += widths[c]
                        }
                        y += heights[r]
                    }
                }
            }
        }
    }
}

/** A column wraps its cells past this share of the page. */
private const val COLUMN_SHARE = 0.75f
