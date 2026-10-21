package dev.icode.mobile.ui.markdown

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import dev.icode.mobile.domain.markdown.MarkdownAssembly
import dev.icode.mobile.domain.markdown.MarkdownDocument
import dev.icode.mobile.domain.markdown.MathItem
import dev.icode.mobile.domain.markdown.MathLayout
import dev.icode.mobile.domain.markdown.MathLayouts
import dev.icode.mobile.domain.markdown.MathOutcome
import dev.icode.mobile.domain.markdown.MathPath
import io.ratex.RaTeXEngine
import kotlin.math.max

/** Every formula iCode draws, laid out once by RaTeX (third_party/ratex) and kept (docs/markdown-rendering-plan.md §3.2). */
internal val mathLayouts = MathLayouts(RaTeXEngine::layout)

/** [document]'s formulas laid out ahead, off the main thread, so drawing it lays out none. */
internal fun layOutMath(document: MarkdownDocument) =
    MarkdownAssembly.formulas(document).forEach { (latex, display) -> mathLayouts.layout(latex, display) }

/**
 * Display math: centred, scrolling sideways when wider than the screen. It is a line of text holding
 * the formula, so it is selected with the text around it and copied as its LaTeX, `$$…$$`. LaTeX RaTeX
 * refuses is shown as written, amber, and TalkBack reads why.
 */
@Composable
internal fun MarkdownMathBlock(latex: String, type: MarkdownType, modifier: Modifier = Modifier) {
    when (val outcome = remember(latex) { mathLayouts.layout(latex, display = true) }) {
        is MathOutcome.Drawn -> {
            val layout = outcome.layout
            val density = LocalDensity.current
            val size = with(density) { type.body.fontSize.toPx() } * MATH_SCALE
            val ink = type.body.color
            val line = remember(latex, layout, size, ink) {
                val text = buildAnnotatedString { appendInlineContent(FORMULA, "\$\$\n$latex\n\$\$") }
                val placeholder = with(density) {
                    Placeholder((layout.width * size).toFloat().toSp(), ((layout.height + layout.depth) * size).toFloat().toSp(), PlaceholderVerticalAlign.Top)
                }
                text to mapOf(FORMULA to InlineTextContent(placeholder) { MathCanvas(layout, size, ink, top = 0f, description = latex) })
            }
            BoxWithConstraints(modifier.fillMaxWidth()) {
                Box(Modifier.horizontalScroll(rememberScrollState()).widthIn(min = maxWidth), contentAlignment = Alignment.Center) {
                    Text(line.first, style = type.body, inlineContent = line.second, softWrap = false)
                }
            }
        }
        is MathOutcome.Refused -> Column(modifier.semantics { contentDescription = "Formula not drawn, ${outcome.reason}: $latex" }) {
            Text("\$\$$latex\$\$", style = type.code.copy(color = type.colors.refused))
            Text(outcome.reason, style = type.small)
        }
    }
}

/**
 * [layout] drawn at [size] pixels to the em, its top [top] pixels down: what RaTeX laid out in its ink
 * in [ink], the rest (`\color{…}`) in its own colour. TalkBack reads its LaTeX, [description].
 */
@Composable
internal fun MathCanvas(layout: MathLayout, size: Float, ink: Color, top: Float, description: String, modifier: Modifier = Modifier) {
    val assets = LocalContext.current.assets
    Canvas(modifier.fillMaxSize().semantics { contentDescription = description }) {
        translate(top = top) { drawMath(layout, size, ink) { font -> MathFonts.typeface(assets, font) } }
    }
}

/** Adapted from RaTeX-CMP 0.1.15's RaTeXCanvas.kt (MIT; app/src/main/resources/licenses/RaTeX.txt). */
private fun DrawScope.drawMath(layout: MathLayout, size: Float, ink: Color, typeface: (String) -> Typeface) {
    fun color(argb: Int) = if (argb == MathLayout.INK) ink else Color(argb)
    fun px(em: Double) = (em * size).toFloat()
    layout.items.forEach { item ->
        when (item) {
            is MathItem.Glyph -> drawGlyph(String(Character.toChars(item.codePoint)), px(item.x), px(item.y), (size * item.scale).toFloat(), typeface(item.font), color(item.color))
            is MathItem.Line -> {
                val thickness = max(MIN_RULE_PX, px(item.thickness))
                if (item.dashed) {
                    drawLine(
                        color(item.color), Offset(px(item.x), px(item.y)), Offset(px(item.x + item.width), px(item.y)), thickness,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(thickness * DASH, thickness * DASH)),
                    )
                } else {
                    drawRect(color(item.color), Offset(px(item.x), px(item.y) - thickness / 2), Size(px(item.width), thickness))
                }
            }
            is MathItem.Rect -> drawRect(color(item.color), Offset(px(item.x), px(item.y)), Size(px(item.width), px(item.height)))
            is MathItem.Path -> {
                val path = Path()
                item.commands.forEach { command ->
                    when (command) {
                        is MathPath.MoveTo -> path.moveTo(px(item.x + command.x), px(item.y + command.y))
                        is MathPath.LineTo -> path.lineTo(px(item.x + command.x), px(item.y + command.y))
                        is MathPath.QuadTo -> path.quadraticTo(px(item.x + command.x1), px(item.y + command.y1), px(item.x + command.x), px(item.y + command.y))
                        is MathPath.CubicTo -> path.cubicTo(
                            px(item.x + command.x1), px(item.y + command.y1), px(item.x + command.x2), px(item.y + command.y2), px(item.x + command.x), px(item.y + command.y),
                        )
                        MathPath.Close -> path.close()
                    }
                }
                drawPath(path, color(item.color), style = if (item.fill) Fill else Stroke(width = STROKE_PX))
            }
        }
    }
}

private const val FORMULA = "formula"

/** RaTeX-CMP's: a rule never thinner than half a pixel, a stroked path one pixel, a dash three rules long. */
private const val MIN_RULE_PX = 0.5f
private const val STROKE_PX = 1f
private const val DASH = 3f
