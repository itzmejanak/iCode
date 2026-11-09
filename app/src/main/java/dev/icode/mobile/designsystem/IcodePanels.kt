package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    /**
     * What is chosen — a branch, a repository — drawn as the button that changes it: [icon], the
     * name and ▾, as a button, so it reads as both the state and the way to change it.
     */
    val choice: Boolean = false,
    /** Drawn before the label: a choice's in the accent, any other's in the label's colour. */
    val icon: ImageVector? = null,
    /** A [choice] being changed now (docs/loading-plan.md §4.2): the spinner in [icon]'s place, and no second tap. */
    val busy: Boolean = false,
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
            .floor()
            .fillMaxWidth()
            .height(IcodeSizes.BottomBar)
            .background(IcodeColors.Surface)
            .padding(horizontal = IcodeSpacing.Space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space5),
    ) {
        // The items scroll sideways when large text makes them wider than the
        // screen; the trailing part stays put. Choices never scroll away: they
        // share the width, each cutting its name short with "…" to fit.
        val choices = items.any { it.choice }
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().then(if (choices) Modifier else Modifier.horizontalScroll(rememberScrollState())),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (choices) IcodeSpacing.Space2 else IcodeSpacing.Space5),
        ) {
            items.forEach { item -> BottomBarEntry(item, if (item.choice) Modifier.weight(1f, fill = false) else Modifier) }
        }
        trailing?.invoke(this)
    }
}

/**
 * A page's actions at its foot, the page scrolling above them: a surface band with its [EdgeLine]
 * on top, its content [IcodeSpacing.Gutter] from the edges and [IcodeSpacing.ItemGap] apart.
 */
@Composable
fun IcodeFootBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.floor().fillMaxWidth().background(IcodeColors.Surface)) {
        EdgeLine()
        Column(
            Modifier.fillMaxWidth().padding(IcodeSpacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap),
            content = content,
        )
    }
}

/**
 * A page's actions at its foot, one row, as the explorer's paste bar drew them first (owner,
 * 2026-10-24: every page's foot alike): a [IcodeColors.SurfaceElevated] band under its [EdgeLine],
 * [IcodeSpacing.Gutter] from the edges and a touch target tall — [leading], then what the actions are
 * about ([title], and [caption] under it), and the [actions] on the right. [iconLast]: the last action
 * is an icon button, its icon then at the gutter, as the top bar's are.
 */
@Composable
fun IcodeActionBar(
    title: String?,
    modifier: Modifier = Modifier,
    caption: String? = null,
    titleStyle: TextStyle = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
    captionColor: Color = IcodeColors.TextSecondary,
    leading: (@Composable () -> Unit)? = null,
    iconLast: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.floor().fillMaxWidth().background(IcodeColors.SurfaceElevated)) {
        EdgeLine()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = IcodeSpacing.Gutter,
                    end = if (iconLast) IcodeSpacing.IconButtonGutter else IcodeSpacing.Gutter,
                    top = IcodeSpacing.Space2,
                    bottom = IcodeSpacing.Space2,
                )
                .defaultMinSize(minHeight = IcodeSizes.TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                title?.let { Text(it, style = titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                caption?.let { Text(it, style = IcodeType.Caption.copy(color = captionColor), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            actions()
        }
    }
}

/** One entry drawn the bar's way; also for a bar's trailing toggles. */
@Composable
fun BottomBarEntry(item: BottomBarItem, modifier: Modifier = Modifier) {
    if (item.choice) return ChoiceEntry(item, item.onClick?.takeIf { item.enabled && !item.busy }, modifier)
    val count = item.count
    BottomBarEntry(item, modifier) { color ->
        item.icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(IcodeSizes.IconSm)) }
        Text(item.label, style = IcodeType.Body.copy(color = color, fontWeight = if (item.alerting) FontWeight.SemiBold else null))
        if (count != null) {
            val lit = count > 0
            Box(
                modifier = Modifier
                    .padding(start = IcodeSpacing.Micro)
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

/**
 * An entry drawn by [content] — more than a label, as the sync counts' arrows are — in the colour
 * [item]'s state gives it, with [item]'s tap and spoken description.
 */
@Composable
fun BottomBarEntry(item: BottomBarItem, modifier: Modifier = Modifier, content: @Composable RowScope.(color: Color) -> Unit) {
    val color = when {
        !item.enabled -> IcodeColors.TextMuted
        item.active -> IcodeColors.Accent
        item.alerting -> item.countTone.tint()
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
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        content(color)
    }
}

/** While its count is above zero, an [BottomBarItem.alert] item takes the pill's colour and weight. */
private val BottomBarItem.alerting: Boolean get() = alert && count != null && count > 0

/**
 * A [BottomBarItem.choice]: its icon in the accent, what is chosen and ▾, on the raised surface
 * with the app's button corner — the state and the button that changes it, at a glance. A long
 * name is cut short with "…" so the bar keeps every choice in view.
 */
@Composable
private fun ChoiceEntry(item: BottomBarItem, click: (() -> Unit)?, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(IcodeSizes.BarChoice)
            .clip(IcodeShapes.Small)
            .background(IcodeColors.SurfaceElevated)
            .then(if (click != null) Modifier.clickable(role = Role.Button, onClick = click) else Modifier)
            .semantics { item.description?.let { contentDescription = it } }
            .busySemantics(item.busy)
            .padding(horizontal = IcodeSpacing.Space2 + IcodeSpacing.Micro),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro),
    ) {
        if (rememberShownWhileBusy(item.busy)) IcodeSpinner()
        else item.icon?.let { Icon(it, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm)) }
        Text(
            item.label,
            modifier = Modifier.weight(1f, fill = false),
            style = IcodeType.Mono.copy(color = if (item.enabled) IcodeColors.TextPrimary else IcodeColors.TextMuted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
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
