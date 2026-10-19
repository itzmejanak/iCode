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

/** Appearance › Card Borders: whether a card draws its own outline (docs/card-borders-plan.md). */
internal val LocalIcodeCardBorders = staticCompositionLocalOf { true }

/**
 * Card surface used by every list, form and panel block. Without a [borderColor] it draws its own
 * outline while Card Borders is on; a [borderColor] is a line that says something — a control's
 * edge, a selection, a tone — and is drawn either way.
 */
@Composable
fun IcodeCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    borderColor: Color? = null,
    padding: PaddingValues = PaddingValues(IcodeSpacing.CardPadding),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickableModifier = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    val line = borderColor ?: IcodeColors.Outline.takeIf { LocalIcodeCardBorders.current }
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

/** Single-pixel separator between dense rows. */
@Composable
fun Divider() {
    HorizontalDivider(color = IcodeColors.Outline, thickness = 1.dp)
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
