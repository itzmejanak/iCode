package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One entry of an [IcodeBottomBar]: a destination, a mode, a scope, a filter or a toggle. */
@Immutable
data class BottomBarItem(
    val label: String,
    val onClick: (() -> Unit)? = null,
    val active: Boolean = false,
    /** A count in a pill after the label; none when null. */
    val count: Int? = null,
    /** The pill's colour while [count] is above zero. */
    val countTone: IcodeTone = IcodeTone.Error,
    /** While [count] is above zero the label takes the pill's colour and weight, as "Problems" does. */
    val alert: Boolean = false,
    /** Spoken instead of [label] when the label is a symbol ("Aa", ".*"). */
    val description: String? = null,
    /** False: shown muted and not tappable, as a step not reached yet or a Prev at the first change. */
    val enabled: Boolean = true,
)

/**
 * The editor's bottom section, and every second- and third-level bar in the
 * app (owner's design, 2026-09-25): a flat surface band at the bottom of the
 * screen, plain labels — the active one in the accent, nothing else added —
 * each with an optional count. With the keyboard open it sits just above it.
 */
@Composable
fun IcodeBottomBar(
    items: List<BottomBarItem>,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IcodeSizes.BottomBar)
            .background(IcodeColors.Surface)
            .padding(horizontal = IcodeSpacing.Space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space5),
    ) {
        // The items scroll sideways when large text makes them wider than the
        // screen; the trailing part stays put.
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space5),
        ) {
            items.forEach { BottomBarEntry(it) }
        }
        trailing?.invoke(this)
    }
}

/** One entry drawn the bar's way; also for a bar's trailing toggles. */
@Composable
fun BottomBarEntry(item: BottomBarItem, modifier: Modifier = Modifier) {
    val count = item.count
    val alerting = item.alert && count != null && count > 0
    val color = when {
        !item.enabled -> IcodeColors.TextMuted
        item.active -> IcodeColors.Accent
        alerting -> item.countTone.tint()
        else -> IcodeColors.TextSecondary
    }
    val click = item.onClick?.takeIf { item.enabled }
    Row(
        modifier = modifier
            .fillMaxHeight()
            .then(if (click != null) Modifier.clickable(role = Role.Tab, onClick = click) else Modifier)
            .semantics {
                selected = item.active
                item.description?.let { contentDescription = it }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.label, style = IcodeType.Body.copy(color = color, fontWeight = if (alerting) FontWeight.SemiBold else null))
        if (count != null) {
            val lit = count > 0
            Box(
                modifier = Modifier
                    .padding(start = IcodeSpacing.Space1 + IcodeSpacing.Micro)
                    .defaultMinSize(minWidth = 20.dp)
                    .height(20.dp)
                    .background(if (lit) item.countTone.tint() else IcodeColors.SurfaceElevated, IcodeShapes.Pill)
                    .padding(horizontal = IcodeSpacing.Space1 + IcodeSpacing.Micro),
                contentAlignment = Alignment.Center,
            ) {
                Text(count.toString(), style = IcodeType.Label.copy(color = if (lit) IcodeColors.OnAccent else IcodeColors.TextMuted))
            }
        }
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
    IcodeBottomBar(
        items = listOf(
            BottomBarItem("Terminal", onClick = onTerminal),
            BottomBarItem("Problems", onClick = onProblems, count = problemCount.takeIf { it > 0 }, alert = true),
        ),
        modifier = modifier,
        trailing = trailing,
    )
}
