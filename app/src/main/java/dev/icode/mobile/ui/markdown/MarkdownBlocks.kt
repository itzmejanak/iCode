package dev.icode.mobile.ui.markdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.tint
import dev.icode.mobile.domain.markdown.MarkdownAlertKind
import dev.icode.mobile.domain.markdown.MarkdownBlock
import dev.icode.mobile.domain.markdown.MarkdownFootnote
import dev.icode.mobile.domain.markdown.MarkdownInline
import dev.icode.mobile.domain.markdown.MarkdownListItem

/** How deep in lists a block is: bullets change with depth, as GitHub's do. */
private val LocalListDepth = compositionLocalOf { 0 }

/** [blocks] one above the other, a [MarkdownType.gap] apart. */
@Composable
internal fun MarkdownBlocks(blocks: List<MarkdownBlock>, context: MarkdownContext, type: MarkdownType, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(type.gap)) {
        blocks.forEach { MarkdownBlockView(it, context, type) }
    }
}

/** One block as GitHub draws it, in iCode's type and colours (docs/editor-and-language-services.md). */
@Composable
internal fun MarkdownBlockView(block: MarkdownBlock, context: MarkdownContext, type: MarkdownType, modifier: Modifier = Modifier) {
    when (block) {
        is MarkdownBlock.Heading -> Column(modifier.fillMaxWidth()) {
            MarkdownText(block.content, type.headings[block.level - 1], context, type, Modifier.fillMaxWidth().semantics { heading() })
            // A rule under H1 and H2, as GitHub draws them.
            if (block.level <= 2) Divider(Modifier.padding(top = IcodeSpacing.Space1))
        }
        is MarkdownBlock.Paragraph -> {
            val pictures = pictures(block.content)
            if (pictures != null) MarkdownPictures(pictures, context, type, modifier.fillMaxWidth()) else MarkdownText(block.content, type.body, context, type, modifier.fillMaxWidth())
        }
        is MarkdownBlock.ListBlock -> MarkdownList(block, context, type, modifier)
        // The rule is drawn behind, not measured beside: a quote may hold a table, which has no intrinsic height.
        is MarkdownBlock.Quote -> MarkdownBlocks(
            block.blocks,
            context,
            type.copy(body = type.body.copy(color = type.colors.secondary)),
            modifier.drawBehind { drawRect(type.colors.line, size = Size(QUOTE_RULE.toPx(), size.height)) }.padding(start = QUOTE_RULE + IcodeSpacing.Space3),
        )
        is MarkdownBlock.Alert -> {
            val (tone, icon) = ALERTS.getValue(block.kind)
            IcodeCard(modifier.fillMaxWidth(), borderColor = tone.tint()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    Icon(icon, contentDescription = null, tint = tone.tint(), modifier = Modifier.size(IcodeSizes.IconMd))
                    Text(block.kind.name, style = type.body.copy(color = tone.tint(), fontWeight = FontWeight.Bold))
                }
                MarkdownBlocks(block.blocks, context, type, Modifier.padding(top = IcodeSpacing.Space2))
            }
        }
        is MarkdownBlock.Code -> MarkdownCodeBlock(block, type, modifier)
        is MarkdownBlock.Math -> MarkdownMathBlock(block.latex, type, modifier)
        is MarkdownBlock.Table -> MarkdownTable(block, context, type, modifier)
        MarkdownBlock.Rule -> Divider(modifier.padding(vertical = IcodeSpacing.Space1))
        is MarkdownBlock.Aligned -> CompositionLocalProvider(LocalMarkdownAlign provides block.alignment.textAlign) {
            MarkdownBlocks(block.blocks, context, type, modifier)
        }
        is MarkdownBlock.Details -> Disclosure(block.open, block.summary, context, type, modifier) { MarkdownBlocks(block.blocks, context, type) }
        is MarkdownBlock.FrontMatter -> Disclosure(false, listOf(MarkdownInline.Text("Front matter")), context, type, modifier) {
            MarkdownCodeBlock(MarkdownBlock.Code("yaml", block.text), type)
        }
        is MarkdownBlock.Source -> Text(block.text, modifier, style = type.code.copy(color = type.colors.muted))
    }
}

/**
 * [content]'s pictures when it holds pictures alone — each perhaps a link, `[![shot](a.png)](b.md)`, as
 * READMEs write them — with line breaks and spaces between; null when it holds anything else.
 */
private fun pictures(content: List<MarkdownInline>): List<MarkdownPicture>? {
    val pictures = mutableListOf<MarkdownPicture>()
    content.forEach { inline ->
        when {
            inline is MarkdownInline.Image -> pictures += MarkdownPicture(inline, null)
            inline is MarkdownInline.Link && inline.children.size == 1 && inline.children[0] is MarkdownInline.Image ->
                pictures += MarkdownPicture(inline.children[0] as MarkdownInline.Image, inline.destination)
            inline == MarkdownInline.LineBreak || (inline is MarkdownInline.Text && inline.text.isBlank()) -> Unit
            else -> return null
        }
    }
    return pictures.ifEmpty { null }
}

/** Bullets or numbers, task items with their boxes, nested lists indented; a loose list's items further apart. */
@Composable
private fun MarkdownList(block: MarkdownBlock.ListBlock, context: MarkdownContext, type: MarkdownType, modifier: Modifier) {
    val depth = LocalListDepth.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (block.loose) type.gap else IcodeSpacing.Space1)) {
        block.items.forEachIndexed { at, item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                ListMarker(item, block.start?.let { (it + at).toString() + "." } ?: BULLETS[depth % BULLETS.size], type)
                CompositionLocalProvider(LocalListDepth provides depth + 1) {
                    MarkdownBlocks(item.blocks, context, type, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ListMarker(item: MarkdownListItem, marker: String, type: MarkdownType) {
    val checked = item.checked
    if (checked == null) {
        Text(marker, style = type.body.copy(color = type.colors.secondary), textAlign = TextAlign.End, modifier = Modifier.widthIn(min = IcodeSizes.IconSm))
    } else {
        Icon(
            if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (checked) IcodeColors.Accent else type.colors.muted,
            modifier = Modifier.size(IcodeSizes.IconSm).semantics { stateDescription = if (checked) "Done" else "Not done" },
        )
    }
}

/** `<details>` and front matter: [summary] shown, a tap shows or hides what is behind it. */
@Composable
private fun Disclosure(
    initiallyOpen: Boolean,
    summary: List<MarkdownInline>,
    context: MarkdownContext,
    type: MarkdownType,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Hide" else "Show") { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
        ) {
            Icon(if (open) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, contentDescription = null, tint = type.colors.muted, modifier = Modifier.size(IcodeSizes.IconSm))
            MarkdownText(summary, type.body.copy(fontWeight = FontWeight.Bold), context, type)
        }
        if (open) Box(Modifier.padding(start = IcodeSpacing.Space4)) { content() }
    }
}

/** A document's footnotes, after its last block, numbered; each one's ↩ goes back to where it is first referred to. */
@Composable
internal fun MarkdownFootnotes(footnotes: List<MarkdownFootnote>, context: MarkdownContext, type: MarkdownType, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        Divider()
        footnotes.forEach { footnote ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                Text("${footnote.number}.", style = type.small)
                MarkdownText(footnote.content, type.small, context, type, Modifier.weight(1f))
                Text(
                    BACK,
                    style = type.small.copy(color = type.colors.link),
                    modifier = Modifier.clickable(onClickLabel = "Back to the text") { context.revealBlock(footnote.referredFrom) },
                )
            }
        }
    }
}

private val ALERTS: Map<MarkdownAlertKind, Pair<IcodeTone, ImageVector>> = mapOf(
    MarkdownAlertKind.Note to (IcodeTone.Accent to Icons.Outlined.Info),
    MarkdownAlertKind.Tip to (IcodeTone.Success to Icons.Outlined.Lightbulb),
    MarkdownAlertKind.Important to (IcodeTone.Info to Icons.Outlined.Campaign),
    MarkdownAlertKind.Warning to (IcodeTone.Warning to Icons.Outlined.ReportProblem),
    MarkdownAlertKind.Caution to (IcodeTone.Error to Icons.Outlined.ErrorOutline),
)


/** GitHub's: a disc, a circle, a square, by depth. */
private val BULLETS = listOf("•", "◦", "▪")

private const val BACK = "↩"
private val QUOTE_RULE = 2.dp
