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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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

/** One of the stack's pills: a touch target tall, raised, its [edgeLine] drawn. */
@Composable
fun IcodeFloatingPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val line = edgeLine
    Row(
        modifier
            .height(IcodeSizes.TouchTarget)
            .clip(IcodeShapes.Pill)
            .background(IcodeColors.SurfaceElevated)
            .then(if (line != null) Modifier.border(1.dp, line, IcodeShapes.Pill) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
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
