package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One tab in a bottom panel (terminal, output, problems). */
@Immutable
data class PanelTab(
    val label: String,
    val tone: IcodeTone = IcodeTone.Neutral,
    val active: Boolean = false,
    val onClick: () -> Unit = {},
)

/**
 * Bottom-panel tab strip.
 *
 * Defaults render terminal-style tabs (mono, tone-tinted); pass [style] and
 * [activeStyle] for panels that use body typography (Output).
 */
@Composable
fun PanelTabStrip(
    tabs: List<PanelTab>,
    modifier: Modifier = Modifier,
    style: TextStyle = IcodeType.MonoSmall,
    activeStyle: TextStyle = style,
    activeBackground: Color = IcodeColors.Background,
    verticalPadding: Dp = 10.dp,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().background(IcodeColors.Surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            Box(
                modifier = Modifier
                    .background(if (tab.active) activeBackground else IcodeColors.Surface)
                    .clickable(onClick = tab.onClick)
                    .padding(horizontal = IcodeSpacing.Space3, vertical = verticalPadding),
            ) {
                Text(
                    tab.label,
                    style = (if (tab.active) activeStyle else style).copy(color = tab.tone.tint()),
                    maxLines = 1,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** Workspace bottom bar switching to the terminal and problems panels. */
@Composable
fun BottomPanelBar(
    problemCount: Int,
    onTerminal: () -> Unit,
    onProblems: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val hasProblems = problemCount > 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IcodeSizes.BottomBar)
            .background(IcodeColors.Surface)
            .padding(horizontal = IcodeSpacing.Space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space5),
    ) {
        Text(
            "Terminal",
            style = IcodeType.Body.copy(color = IcodeColors.TextSecondary),
            modifier = Modifier.clickable(onClick = onTerminal),
        )
        Row(Modifier.clickable(onClick = onProblems), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Problems",
                style = IcodeType.Body.copy(
                    color = if (hasProblems) IcodeColors.Error else IcodeColors.TextSecondary,
                    fontWeight = if (hasProblems) FontWeight.SemiBold else null,
                ),
            )
            if (hasProblems) {
                Box(
                    modifier = Modifier
                        .padding(start = IcodeSpacing.Space1 + IcodeSpacing.Micro)
                        .size(20.dp)
                        .background(IcodeColors.Error, IcodeShapes.Pill),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(problemCount.toString(), style = IcodeType.Label.copy(color = IcodeColors.OnAccent))
                }
            }
        }
        if (trailing != null) {
            Box(Modifier.weight(1f))
            trailing()
        }
    }
}

/** Editor document tab; the caller owns the scrollable strip. */
@Composable
fun EditorTab(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(92.dp)
            .fillMaxHeight()
            .background(if (active) IcodeColors.Surface else IcodeColors.Background)
            .clickable(onClick = onClick)
            .padding(horizontal = IcodeSpacing.Space3),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = IcodeType.Caption.copy(color = if (active) IcodeColors.Accent else IcodeColors.TextMuted))
    }
}
