package dev.icode.mobile.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * What the floating stack — the undo pill above the bell — stands clear of (docs/explorer-gaps-plan.md
 * §3.8): the top edges of the + buttons and foot bars on screen, each reported by the design system's
 * own ([floor]), so no screen wires it; and the sheets open over the screen, while it steps aside.
 */
@Stable
class IcodeFloors {
    private val tops = mutableStateMapOf<Any, Float>()
    private val sheets = mutableStateMapOf<Any, Unit>()

    /** The highest floor's top edge, in the root's pixels; none, nothing to stand above. */
    val highest: Float? get() = tops.values.minOrNull()

    val sheetOpen: Boolean get() = sheets.isNotEmpty()

    internal fun report(key: Any, top: Float) {
        tops[key] = top
    }

    internal fun sheet(key: Any) {
        sheets[key] = Unit
    }

    internal fun leave(key: Any) {
        tops.remove(key)
        sheets.remove(key)
    }
}

val LocalIcodeFloors = staticCompositionLocalOf<IcodeFloors?> { null }

/** This + or foot bar is a floor the floating stack stands above, while it is on screen. */
@Composable
internal fun Modifier.floor(): Modifier {
    val floors = LocalIcodeFloors.current ?: return this
    val key = remember { Any() }
    DisposableEffect(floors, key) { onDispose { floors.leave(key) } }
    return onGloballyPositioned { floors.report(key, it.boundsInRoot().top) }
}

/** A sheet is open over the screen while this is composed: the floating stack steps aside. */
@Composable
internal fun SheetOpen() {
    val floors = LocalIcodeFloors.current ?: return
    DisposableEffect(floors) {
        val key = Any()
        floors.sheet(key)
        onDispose { floors.leave(key) }
    }
}

/**
 * The floating stack (§3.8): [content] — the undo pill above the bell — in one column at the bottom
 * end, above the highest of [floors] and at least [IcodeSizes.FabClearance] up; gone while a sheet is
 * open or the stack is not [visible]. Its pills say nothing in words (owner's request, 2026-11-04):
 * icons and counts only, so a long name never stretches them.
 */
@Composable
fun IcodeFloatingStack(floors: IcodeFloors, visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    var bottom by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { bottom = it.boundsInRoot().bottom }) {
        val above = floors.highest?.let { with(LocalDensity.current) { (bottom - it).toDp() } + IcodeSpacing.Space2 }
        AnimatedVisibility(
            visible = visible && !floors.sheetOpen,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = IcodeSpacing.Gutter, bottom = maxOf(IcodeSizes.FabClearance, above ?: 0.dp)),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2), content = content)
        }
    }
}

/**
 * One of the stack's pills: a touch target tall, raised, its [edgeLine] drawn. Its content may make it
 * taller — the bubble's bar, typed in at length — and its corners stay as round as at its least.
 *
 * With a [tab] or a [menu] (the owner's design, 2026-11-04): a tab rises from its top at the end, of
 * the pill's own surface, its top edge curving up into it. On it, [tab]'s actions as icons, each in a
 * touch target's column so they stand over what ends the pill, then ⋮. ⋮ opens [menu] in the tab
 * itself, not in a dropdown: the actions named, in [tab]'s place, the tab growing toward the start
 * with its curve; one chosen, or ⋮ again, and it closes.
 */
@Composable
fun IcodeFloatingPill(
    modifier: Modifier = Modifier,
    tab: List<IcodeMenuAction> = emptyList(),
    menu: List<IcodeMenuAction> = emptyList(),
    content: @Composable RowScope.() -> Unit,
) {
    val tabbed = tab.isNotEmpty() || menu.isNotEmpty()
    var open by remember { mutableStateOf(false) }
    val tabSize = remember { mutableStateOf(IntSize.Zero) }
    Column(
        modifier.tabbedSurface(tabSize.takeIf { tabbed }, IcodeSizes.TouchTarget / 2, IcodeColors.SurfaceElevated, edgeLine),
        horizontalAlignment = Alignment.End,
    ) {
        if (tabbed) {
            Row(
                Modifier
                    .height(IcodeSizes.BarChoice)
                    .animateContentSize(tween(TAB_MILLIS), alignment = Alignment.CenterEnd)
                    .onSizeChanged { tabSize.value = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (open) {
                    menu.forEach { action ->
                        NamedTabAction(action) {
                            open = false
                            action.onClick()
                        }
                    }
                } else {
                    tab.forEach { TabAction(it.icon, it.label, it.enabled, IcodeColors.TextSecondary, it.onClick) }
                }
                if (menu.isNotEmpty()) TabAction(Icons.Outlined.MoreVert, "More", true, if (open) IcodeColors.Accent else IcodeColors.TextSecondary) { open = !open }
            }
        }
        // As tall as its content, at least a touch target: content filling its height (the bell's
        // tap area) fills that, never the screen.
        Row(
            Modifier.heightIn(min = IcodeSizes.TouchTarget).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** A tab's icon action: a compact action's size, in a touch target's column; touch finds it a touch target. */
@Composable
private fun TabAction(icon: ImageVector, label: String, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    Box(Modifier.width(IcodeSizes.TouchTarget).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(IcodeSizes.BarChoice)
                .clip(IcodeShapes.Pill)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (enabled) tint else IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
        }
    }
}

/** A menu's action opened in the tab: its icon and its name. */
@Composable
private fun NamedTabAction(action: IcodeMenuAction, onClick: () -> Unit) {
    val color = if (action.enabled) IcodeColors.TextPrimary else IcodeColors.TextMuted
    Row(
        Modifier
            .fillMaxHeight()
            .clip(IcodeShapes.Pill)
            .clickable(enabled = action.enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = IcodeSpacing.Space2),
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(action.icon, contentDescription = null, tint = if (action.enabled) IcodeColors.TextSecondary else IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
        Text(action.label, style = IcodeType.Body.copy(color = color), maxLines = 1)
    }
}

/**
 * A floating [pill] with its ×, apart from it on nothing just off its top end (owner's design,
 * 2026-11-04): × puts the pill away; touch finds it a touch target.
 */
@Composable
fun IcodeFloatingAway(label: String, onAway: () -> Unit, pill: @Composable () -> Unit) {
    Box {
        pill()
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = IcodeSpacing.Space1, y = -IcodeSpacing.Space1)
                .size(IcodeSizes.IconXl)
                .clip(IcodeShapes.Pill)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onAway),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = label, tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
        }
    }
}

/** A pill's tab grows and shrinks over this long, as ⋮ opens and closes its menu. */
private const val TAB_MILLIS = 200
