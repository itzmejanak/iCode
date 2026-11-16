package dev.icode.mobile.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * What the floating stack — the undo pill above the bell — stands clear of
 * (docs/workspaces-and-files.md): the top edges of the + buttons and foot bars on screen, each reported
 * by the design system's
 * own ([floor]), so no screen wires it; and the sheets open over the screen, which it is hidden under.
 */
@Stable
class IcodeFloors {
    private val tops = mutableStateMapOf<Any, Float>()
    private val sheets = mutableStateMapOf<Any, Unit>()

    /** The highest floor's top edge, in the root's pixels; none, nothing to stand above. */
    val highest: Float? get() = tops.values.minOrNull()

    val sheetOpen: Boolean get() = sheets.isNotEmpty()

    /**
     * The stack is hidden while a sheet is open or it is not [visible] (the Notifications page). Hidden,
     * its pills stay composed, as they were, and come back so ([LocalPillsHidden]).
     */
    fun hidesStack(visible: Boolean): Boolean = !visible || sheetOpen

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

/**
 * A sheet is open over the screen while this is composed: the floating stack is hidden under it. It
 * counts from the composition that first draws the sheet, not once that is applied, so the stack,
 * composed after the screens, is already hidden in the frame the sheet first appears.
 */
@Composable
internal fun SheetOpen() {
    val floors = LocalIcodeFloors.current ?: return
    remember(floors) { SheetMark(floors) }
}

/** One open sheet among [floors]: from its first composition until it leaves the composition. */
internal class SheetMark(private val floors: IcodeFloors) : RememberObserver {
    init {
        floors.sheet(this)
    }

    override fun onRemembered() = Unit

    override fun onForgotten() = floors.leave(this)

    override fun onAbandoned() = floors.leave(this)
}

/**
 * The floating stack: [pills] — the undo pill above the bell, the tool dock below them — at
 * the bottom end, above the highest of [floors] and at least [IcodeSizes.FabClearance] up; hidden while
 * a sheet is open or the stack is not [visible] ([IcodeFloors.hidesStack]). Hidden, the pills stay
 * composed, so each keeps what it holds: at rest or awake, the dock open, where it stands. They are
 * not drawn, take no touch, say nothing to TalkBack, keep nothing from the back gesture, and their
 * Back is off ([LocalPillsHidden]); they go at once, never sliding over a sheet, and come back as they
 * were, with nothing to wake. They slide in from their edges only the first time they are shown. Each pill ([IcodePillRow], [IcodeDockedPill]) is its
 * see-through icons, and says nothing in words (owner's requests, 2026-11-04): icons and counts only,
 * so a long name never stretches it. A pill dragged moves to any edge, anywhere along it ([FloatingPills]).
 *
 * With [onOutside], a pill is out that a touch anywhere else puts away (the dock's row): that touch
 * does only that, as one outside a menu does.
 */
@Composable
fun IcodeFloatingStack(
    floors: IcodeFloors,
    visible: Boolean,
    pills: List<IcodeStackPill>,
    onOutside: (() -> Unit)? = null,
) {
    var bottom by remember { mutableFloatStateOf(0f) }
    val hidden = floors.hidesStack(visible)
    // Shown once, the pills stay composed from then on: hiding them is [LocalPillsHidden]'s.
    var arrived by remember { mutableStateOf(false) }
    if (!hidden) arrived = true
    Box(Modifier.fillMaxSize().onGloballyPositioned { bottom = it.boundsInRoot().bottom }) {
        val above = floors.highest?.let { with(LocalDensity.current) { (bottom - it).toDp() } + IcodeSpacing.Space2 }
        if (onOutside != null && !hidden) {
            val outside by rememberUpdatedState(onOutside)
            // On the touch's first contact: a scroll begun outside it puts it away as a tap does.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); outside() } })
        }
        // Each pill slides in from its own edge, the first time.
        AnimatedVisibility(visible = arrived, modifier = Modifier.fillMaxSize(), enter = EnterTransition.None, exit = ExitTransition.None) {
            CompositionLocalProvider(LocalPillsHidden provides hidden) {
                FloatingPills(pills, floor = maxOf(IcodeSizes.FabClearance, above ?: 0.dp))
            }
        }
    }
}

/**
 * A floating pill — the bubble's bar and its voice bubble: a touch target tall, raised, its [edgeLine]
 * drawn. Its content may make it taller — the bar, typed in at length — and its corners stay as round
 * as at its least.
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

/** A pill's tab grows and shrinks over this long, as ⋮ opens and closes its menu. */
private const val TAB_MILLIS = 200
