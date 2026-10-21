package dev.icode.mobile.ui.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.PlexMono

/**
 * A document's type and colours (docs/markdown-rendering-plan.md §3.5), from its body text: headings
 * on the design system's scale (22, 20 and 16 for a 14 body; H4–H6 the body, bold), code in Plex Mono
 * at the editor's 13, math at KaTeX's 1.21. All in proportion, so a pinch scales them together, and
 * the [gap] between blocks too.
 */
@Immutable
internal data class MarkdownType(
    val body: TextStyle,
    /** H1 to H6. */
    val headings: List<TextStyle>,
    val code: TextStyle,
    val small: TextStyle,
    val gap: Dp,
    val colors: MarkdownColors,
)

@Immutable
internal data class MarkdownColors(
    val text: Color,
    val secondary: Color,
    val muted: Color,
    val link: Color,
    /** A code span's and a code block's ground. */
    val codeGround: Color,
    val line: Color,
    val mark: Color,
    /** LaTeX that cannot be drawn. */
    val refused: Color,
)

@Composable
internal fun markdownType(body: TextStyle): MarkdownType {
    val density = LocalDensity.current
    val colors = MarkdownColors(
        text = IcodeColors.TextPrimary,
        secondary = IcodeColors.TextSecondary,
        muted = IcodeColors.TextMuted,
        link = IcodeColors.Blue,
        codeGround = IcodeColors.SurfaceElevated,
        line = IcodeColors.Outline,
        mark = IcodeColors.Amber.copy(alpha = MARK_ALPHA),
        refused = IcodeColors.Amber,
    )
    return remember(body, density, colors) {
        val text = body.copy(color = colors.text)
        fun scaled(ratio: Float, bold: Boolean) = text.copy(fontSize = text.fontSize * ratio, fontWeight = if (bold) FontWeight.Bold else text.fontWeight)
        MarkdownType(
            body = text,
            headings = HEADING_RATIOS.map { scaled(it, bold = true) },
            code = text.copy(fontFamily = PlexMono, fontSize = text.fontSize * CODE_RATIO),
            small = scaled(SMALL_RATIO, bold = false).copy(color = colors.secondary),
            gap = with(density) { (text.fontSize * GAP_RATIO).toDp() },
            colors = colors,
        )
    }
}

/** IcodeType's Heading, PageTitle and Title over its Body; H4–H6 the body's size. */
private val HEADING_RATIOS = listOf(22f / 14, 20f / 14, 16f / 14, 1f, 1f, 1f)

/** IcodeType's Mono over its Body, as the editor's code is drawn. */
private const val CODE_RATIO = 13f / 14

/** IcodeType's Caption over its Body: footnotes, a code block's language. */
private const val SMALL_RATIO = 11f / 14

/** Between blocks: three quarters of the text's size; GitHub's 1em, a little tighter for a phone. */
private const val GAP_RATIO = 0.75f

/** Math's size over the text's: KaTeX's own `.katex { font-size: 1.21em }`, so its fonts' x-height meets the text's. */
internal const val MATH_SCALE = 1.21f

private const val MARK_ALPHA = 0.3f
