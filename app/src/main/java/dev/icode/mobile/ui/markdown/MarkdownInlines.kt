package dev.icode.mobile.ui.markdown

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import dev.icode.mobile.designsystem.PlexMono
import dev.icode.mobile.domain.markdown.MarkdownAlignment
import dev.icode.mobile.domain.markdown.MarkdownDestination
import dev.icode.mobile.domain.markdown.MarkdownImageSource
import dev.icode.mobile.domain.markdown.MarkdownInline
import dev.icode.mobile.domain.markdown.MarkdownLinks
import dev.icode.mobile.domain.markdown.MarkdownStyle
import dev.icode.mobile.domain.markdown.MarkdownTarget
import dev.icode.mobile.domain.markdown.MathOutcome
import kotlin.math.max

/** How lines are aligned in the blocks under an HTML `align` (MarkdownBlock.Aligned) and in a table's column. */
internal val LocalMarkdownAlign = compositionLocalOf { TextAlign.Start }

internal val MarkdownAlignment.textAlign: TextAlign
    get() = when (this) {
        MarkdownAlignment.Start -> TextAlign.Start
        MarkdownAlignment.Center -> TextAlign.Center
        MarkdownAlignment.End -> TextAlign.End
    }

/**
 * A line of Markdown as one text (docs/editor-and-language-services.md): styles as spans, links
 * as link annotations, inline math as inline content on the line's baseline. Selected and copied, a
 * formula is its LaTeX, `$…$`.
 */
@Composable
internal fun MarkdownText(
    inlines: List<MarkdownInline>,
    style: TextStyle,
    context: MarkdownContext,
    type: MarkdownType,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val line = remember(inlines, style, type, context, density) { MarkdownLine(style, type, context, density, measurer).build(inlines) }
    Text(line.first, modifier, style = style, textAlign = LocalMarkdownAlign.current, inlineContent = line.second)
}

/** One line's text and its inline content, built from its inlines. */
private class MarkdownLine(
    private val style: TextStyle,
    private val type: MarkdownType,
    private val context: MarkdownContext,
    private val density: Density,
    private val measurer: TextMeasurer,
) {
    private val text = AnnotatedString.Builder()
    private val content = mutableMapOf<String, InlineTextContent>()

    /** How far above the baseline the text's centre is, in pixels: where Compose centres a `TextCenter` placeholder. */
    private val textCenter: Float by lazy {
        measurer.measure(AnnotatedString("x"), style).let { it.firstBaseline - it.size.height / 2f }
    }

    fun build(inlines: List<MarkdownInline>): Pair<AnnotatedString, Map<String, InlineTextContent>> {
        add(inlines)
        return text.toAnnotatedString() to content.toMap()
    }

    private fun add(inlines: List<MarkdownInline>) = inlines.forEach(::add)

    private fun add(inline: MarkdownInline) {
        when (inline) {
            is MarkdownInline.Text -> text.append(inline.text)
            is MarkdownInline.Styled -> text.withStyle(span(inline.style)) { add(inline.children) }
            is MarkdownInline.Code -> text.withStyle(
                SpanStyle(fontFamily = PlexMono, fontSize = CODE_EM.em, background = type.colors.codeGround, fontWeight = if (inline.key) FontWeight.Medium else null),
            ) { append(inline.text) }
            is MarkdownInline.Link -> when (val destination = inline.destination) {
                is MarkdownDestination.Url -> linked(MarkdownLinks.target(destination.href, context.place)) { add(inline.children) }
                is MarkdownDestination.Reference -> text.append(destination.source)
            }
            is MarkdownInline.Image -> image(inline)
            is MarkdownInline.Math -> math(inline.latex)
            MarkdownInline.LineBreak -> text.append('\n')
            is MarkdownInline.FootnoteReference -> inline.number?.let { number ->
                text.withLink(LinkAnnotation.Clickable(FOOTNOTE_TAG, TextLinkStyles(SpanStyle(color = type.colors.link))) { context.openFootnotes() }) {
                    withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = SCRIPT_EM.em)) { append(number.toString()) }
                }
            } ?: text.append(inline.source)
            is MarkdownInline.Source -> text.withStyle(SpanStyle(fontFamily = PlexMono, fontSize = CODE_EM.em, color = type.colors.muted)) { append(inline.text) }
        }
    }

    /** [body] as a link to [target]; as plain text when it leads nowhere iCode follows. */
    private fun linked(target: MarkdownTarget?, body: () -> Unit) {
        if (target == null) return body()
        text.withLink(LinkAnnotation.Clickable(LINK_TAG, TextLinkStyles(SpanStyle(color = type.colors.link))) { context.open(target) }) { body() }
    }

    /** An image in a line of text: its alt text, a link to the image (the viewer, or the browser for `https`). */
    private fun image(image: MarkdownInline.Image) {
        val label = image.alt.ifBlank { image.title ?: IMAGE_LABEL }
        val href = (image.destination as? MarkdownDestination.Url)?.href
            ?: return text.append((image.destination as MarkdownDestination.Reference).source)
        val target = when (val source = MarkdownLinks.image(href, context.place)) {
            is MarkdownImageSource.Local -> MarkdownTarget.File(source.path, null)
            is MarkdownImageSource.Remote -> MarkdownTarget.Web(source.url)
            is MarkdownImageSource.Inline, null -> MarkdownLinks.target(href, context.place)
        }
        linked(target) { text.withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = type.colors.secondary)) { append(label) } }
    }

    /**
     * Inline math, laid out by RaTeX at [MATH_SCALE] of the text. Compose aligns a placeholder only by
     * its edges or centre, so it is sized around the text's centre ([textCenter]), reaching the
     * formula's height above its baseline and its depth below: the formula's baseline is then the
     * line's, as KaTeX puts it. LaTeX RaTeX refuses is its source, amber.
     */
    private fun math(latex: String) {
        val layout = (mathLayouts.layout(latex, display = false) as? MathOutcome.Drawn)?.layout
        if (layout == null) {
            text.withStyle(SpanStyle(fontFamily = PlexMono, fontSize = CODE_EM.em, color = type.colors.refused)) { append("\$$latex\$") }
            return
        }
        val size = with(density) { style.fontSize.toPx() } * MATH_SCALE
        val above = (layout.height * size).toFloat()
        val below = (layout.depth * size).toFloat()
        val half = max(above - textCenter, below + textCenter)
        val id = "math${content.size}"
        val ink = style.color
        content[id] = InlineTextContent(
            with(density) { Placeholder((layout.width * size).toFloat().toSp(), (2 * half).toSp(), PlaceholderVerticalAlign.TextCenter) },
        ) {
            MathCanvas(layout, size, ink, top = half + textCenter - above, description = latex)
        }
        text.appendInlineContent(id, formulaText(latex, display = false))
    }

    private fun span(style: MarkdownStyle): SpanStyle = when (style) {
        MarkdownStyle.Emphasis -> SpanStyle(fontStyle = FontStyle.Italic)
        MarkdownStyle.Strong -> SpanStyle(fontWeight = FontWeight.Bold)
        MarkdownStyle.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        MarkdownStyle.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
        MarkdownStyle.Subscript -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = SCRIPT_EM.em)
        MarkdownStyle.Superscript -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = SCRIPT_EM.em)
        MarkdownStyle.Mark -> SpanStyle(background = type.colors.mark)
    }
}

/** A code span's size in its line: the editor's 13 to the text's 14. */
private const val CODE_EM = 13f / 14

/** Sub- and superscripts, footnote numbers among them. */
private const val SCRIPT_EM = 0.75f

private const val LINK_TAG = "link"
private const val FOOTNOTE_TAG = "footnote"
private const val IMAGE_LABEL = "Image"
