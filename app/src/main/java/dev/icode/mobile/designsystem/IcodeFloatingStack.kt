package dev.icode.mobile.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
 * open or the stack is not [visible]. A line in it takes at most [LINE_SHARE] of the screen's width,
 * given to [content].
 */
@Composable
fun IcodeFloatingStack(floors: IcodeFloors, visible: Boolean, content: @Composable ColumnScope.(lineWidth: Dp) -> Unit) {
    var bottom by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { bottom = it.boundsInRoot().bottom }) {
        val above = floors.highest?.let { with(LocalDensity.current) { (bottom - it).toDp() } + IcodeSpacing.Space2 }
        val lineWidth = maxWidth * LINE_SHARE
        AnimatedVisibility(
            visible = visible && !floors.sheetOpen,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = IcodeSpacing.Gutter, bottom = maxOf(IcodeSizes.FabClearance, above ?: 0.dp)),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                content(lineWidth)
            }
        }
    }
}

/**
 * One of the stack's pills: a touch target tall, raised, its [edgeLine] drawn. Its content may make it
 * taller — the bubble's bar, typed in at length — and its corners stay as round as at its least.
 *
 * With a [tab] (the owner's design, 2026-11-04): a tab rises from its top at the end, of the pill's
 * own surface, its top edge curving up into it, holding those actions small — each in a touch
 * target's column, so they stand over what ends the pill.
 */
@Composable
fun IcodeFloatingPill(modifier: Modifier = Modifier, tab: List<IcodeMenuAction> = emptyList(), content: @Composable RowScope.() -> Unit) {
    val line = edgeLine
    val shape = if (tab.isEmpty()) IcodeShapes.FloatingPill else TabbedPill(IcodeSizes.TouchTarget * tab.size)
    Column(
        modifier
            .clip(shape)
            .background(IcodeColors.SurfaceElevated)
            .then(if (line != null) Modifier.border(1.dp, line, shape) else Modifier),
        horizontalAlignment = Alignment.End,
    ) {
        if (tab.isNotEmpty()) {
            Row(Modifier.height(IcodeSizes.CornerAction)) { tab.forEach { TabAction(it) } }
        }
        Row(Modifier.heightIn(min = IcodeSizes.TouchTarget), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** One of a pill's tab actions: an icon a corner action wide, in a touch target's column; touch finds it a touch target. */
@Composable
private fun TabAction(action: IcodeMenuAction) {
    Box(Modifier.width(IcodeSizes.TouchTarget).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(IcodeSizes.CornerAction)
                .clip(IcodeShapes.Pill)
                .clickable(enabled = action.enabled, role = Role.Button, onClickLabel = action.label, onClick = action.onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                action.icon,
                contentDescription = action.label,
                tint = if (action.enabled) IcodeColors.TextSecondary else IcodeColors.TextMuted,
                modifier = Modifier.size(IcodeSizes.IconMd),
            )
        }
    }
}

/**
 * A floating pill with a tab [tabWidth] wide rising [IcodeSizes.CornerAction] from its top at the end:
 * the pill's top edge curves up into the tab over as long a run as the tab is tall, and the tab's
 * outer corner is as round as the pill's.
 */
private data class TabbedPill(val tabWidth: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val rise = IcodeSizes.CornerAction.toPx()
        val tab = tabWidth.toPx()
        val r = (IcodeSizes.TouchTarget / 2).toPx().coerceAtMost((size.height - rise) / 2)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, rise + r)
            arcTo(Rect(0f, rise, 2 * r, rise + 2 * r), 180f, 90f, false)
            lineTo(w - tab - rise, rise)
            cubicTo(w - tab - rise / 2, rise, w - tab - rise / 2, 0f, w - tab, 0f)
            lineTo(w - r, 0f)
            arcTo(Rect(w - 2 * r, 0f, w, 2 * r), 270f, 90f, false)
            lineTo(w, h - r)
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            close()
        }
        // Drawn with the tab at the right; read right to left, it is at the left.
        if (layoutDirection == LayoutDirection.Rtl) path.transform(Matrix().apply { scale(-1f, 1f); translate(-w, 0f) })
        Outline.Generic(path)
    }
}

/**
 * A pill's line, sliding out from beside its icon and back, at most [maxWidth], with its [action]
 * after it; TalkBack reads it as it read a toast.
 */
@Composable
fun IcodeFloatingLine(visible: Boolean, text: String, maxWidth: Dp, action: (@Composable RowScope.() -> Unit)? = null) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(tween(FLOATING_SLIDE_MILLIS), expandFrom = Alignment.End) + fadeIn(tween(FLOATING_SLIDE_MILLIS)),
        exit = shrinkHorizontally(tween(FLOATING_SLIDE_MILLIS), shrinkTowards = Alignment.End) + fadeOut(tween(FLOATING_SLIDE_MILLIS)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = IcodeType.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .padding(end = if (action == null) IcodeSpacing.Space4 else IcodeSpacing.Space1)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            action?.invoke(this)
        }
    }
}

/** ×, putting a pill away: it comes once the line has gone, so the pill does not jostle while it slides. */
@Composable
fun IcodeFloatingAway(visible: Boolean, label: String, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(tween(FLOATING_SLIDE_MILLIS, delayMillis = FLOATING_SLIDE_MILLIS), expandFrom = Alignment.Start) +
            fadeIn(tween(FLOATING_SLIDE_MILLIS, delayMillis = FLOATING_SLIDE_MILLIS)),
        exit = fadeOut(tween(FLOATING_SLIDE_MILLIS)),
    ) {
        IconButton(onClick = onClick) {
            Icon(Icons.Outlined.Close, contentDescription = label, tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
        }
    }
}

/** A pill's line slides in and out over this long. */
const val FLOATING_SLIDE_MILLIS = 350

/** The line takes at most this share of the screen's width. */
private const val LINE_SHARE = 0.75f
