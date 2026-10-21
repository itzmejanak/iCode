package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Appearance › Border Lines: whether a surface draws the line at its edge (docs/border-lines-plan.md). */
internal val LocalIcodeBorderLines = staticCompositionLocalOf { true }

/** Whether cards stand on a sheet, and so one step above it, as a page's cards are above the page. */
internal val LocalIcodeCardRaised = staticCompositionLocalOf { false }

/** The line where a surface ends — a card, a menu, a bar — while Border Lines is on; null while it is off. */
internal val edgeLine: Color? @Composable get() = IcodeColors.Outline.takeIf { LocalIcodeBorderLines.current }

/**
 * Card surface used by every list, form and panel block, a step above what it stands on. Without a
 * [borderColor] it draws its [edgeLine]; a [borderColor] is a line that says something — a
 * control's edge, a selection, a tone — and is drawn either way.
 */
@Composable
fun IcodeCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = LocalIcodeCardRaised.current,
    borderColor: Color? = null,
    padding: PaddingValues = PaddingValues(IcodeSpacing.CardPadding),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickableModifier = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    val line = borderColor ?: edgeLine
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
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SectionLabel(title)
        if (actionLabel != null) {
            Text(
                actionLabel,
                style = IcodeType.Label.copy(color = IcodeColors.Accent),
                modifier = if (onAction == null) Modifier else Modifier.clickable(onClick = onAction),
            )
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
