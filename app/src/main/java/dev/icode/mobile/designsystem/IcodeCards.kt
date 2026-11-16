package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Appearance › Border Lines: whether a surface draws the line at its edge (docs/design-and-navigation.md). */
internal val LocalIcodeBorderLines = staticCompositionLocalOf { true }

/** Whether cards stand on a sheet, and so one step above it, as a page's cards are above the page. */
internal val LocalIcodeCardRaised = staticCompositionLocalOf { false }

/** The line where a surface ends — a card, a menu, a bar — while Border Lines is on; null while it is off. */
internal val edgeLine: Color? @Composable get() = IcodeColors.Outline.takeIf { LocalIcodeBorderLines.current }

/**
 * Card surface used by every list, form and panel block, a step above what it stands on. Without a
 * [borderColor] it draws its [edgeLine]; a [borderColor] is a line that says something — a
 * control's edge, a selection, a tone — and is drawn either way. With a [tab] (the owner's design,
 * 2026-11-04: a message's files), the tab rises from its top at the end, of the card's own surface,
 * its top edge curving up into it ([tabbedSurface]), as the bubble's bar holds its actions.
 */
@Composable
fun IcodeCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = LocalIcodeCardRaised.current,
    borderColor: Color? = null,
    padding: PaddingValues = PaddingValues(IcodeSpacing.CardPadding),
    onClick: (() -> Unit)? = null,
    /** What a long press does, said as [onLongClickLabel]: a message's Copy and Select Text. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    tab: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickableModifier = when {
        onLongClick != null -> Modifier.combinedClickable(
            role = onClick?.let { Role.Button },
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick ?: {},
        )
        onClick != null -> Modifier.clickable(role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    val line = borderColor ?: edgeLine
    val color = if (elevated) IcodeColors.SurfaceElevated else IcodeColors.Surface
    if (tab != null) {
        val tabSize = remember { mutableStateOf(IntSize.Zero) }
        Column(modifier.tabbedSurface(tabSize, IcodeShapes.SmallCorner, color, line).then(clickableModifier), horizontalAlignment = Alignment.End) {
            Row(
                Modifier.onSizeChanged { tabSize.value = it }.padding(IcodeSpacing.Space1),
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
                verticalAlignment = Alignment.CenterVertically,
                content = tab,
            )
            Column(Modifier.fillMaxWidth().padding(padding), content = content)
        }
        return
    }
    Column(
        modifier = modifier
            .clip(IcodeShapes.Small)
            .background(if (elevated) IcodeColors.SurfaceElevated else IcodeColors.Surface)
            .then(if (line != null) Modifier.border(1.dp, line, IcodeShapes.Small) else Modifier)
            .then(clickableModifier)
            .padding(padding),
        content = content,
    )
}

/** A group of a page: its label, if it has one, then its items, [IcodeSpacing.ItemGap] apart. */
@Composable
fun IcodeSection(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap)) {
        title?.let { SectionLabel(it) }
        content()
    }
}

/** Uppercase group label above cards and sections. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = IcodeType.Label,
        color = IcodeColors.TextSecondary,
    )
}

/** Section label with an optional trailing text action (e.g. "STAGE ALL"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** What the action started runs: the spinner before its label, and no second tap (docs/design-and-navigation.md). */
    busy: Boolean = false,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title)
        if (actionLabel != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                BusyIndicator(busy)
                Text(
                    actionLabel,
                    style = IcodeType.Label.copy(color = IcodeColors.Accent),
                    modifier = if (onAction == null) Modifier else Modifier.clickable(enabled = !busy, onClick = onAction).busySemantics(busy),
                )
            }
        }
    }
}

/**
 * Section label with an icon action at its end: the top corner of the card it heads (Last Commit's
 * history). The icon takes no more of the row than its own size, its ripple spreading into the card's
 * padding, so the header is no taller than its label; touch finds it a full touch target wide.
 */
@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title, Modifier.weight(1f))
        Box(
            Modifier
                .size(IcodeSizes.IconMd)
                .wrapContentSize(unbounded = true)
                .size(IcodeSizes.CornerAction)
                .clip(IcodeShapes.Pill)
                .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onAction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = actionLabel, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
        }
    }
}

/** Single-pixel separator between dense rows; drawn whatever Border Lines says. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = IcodeColors.Outline, thickness = 1.dp)
}

/** The line where a bar begins — under the top bar, over a bar at the foot — while Border Lines is on. */
@Composable
fun EdgeLine() {
    edgeLine?.let { HorizontalDivider(color = it, thickness = 1.dp) }
}

/** Deep surface for code, terminal, diff and log content.
 *
 * Use [verticalArrangement] to space code lines; the surface owns the
 * background and padding so screens never repeat them.
 */
@Composable
fun CodeSurface(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(IcodeSpacing.Space3),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.background(IcodeColors.BackgroundDeep).padding(padding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
