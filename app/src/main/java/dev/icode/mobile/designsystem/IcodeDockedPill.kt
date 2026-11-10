package dev.icode.mobile.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.icode.mobile.domain.ScreenEdge
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * One of the floating stack's pills (owner's choices, 2026-11-04): docked flush on the screen's edge
 * the stack puts it on ([LocalPillEdge]), round on its open side with a grip there in [gripTint], a
 * touch target tall, its [edgeLine] on the open sides only. It never goes: [tucked], it rests in the
 * edge with only its grip showing, wide enough to find with a thumb; swiped into the edge — past half
 * its way, or flung — it tucks, and tapped or pulled out it shows ([onTucked]); short of either it
 * springs back. TalkBack has both as actions on [name], then [actions], then the stack's moves.
 *
 * With [onSwipeAlong], a swipe up or down along the tucked grip, over half its height, does that:
 * the dock's switch to the tool used before (docs/dock-navigation-plan.md D9).
 */
@Composable
fun IcodeDockedPill(
    tucked: Boolean,
    onTucked: (Boolean) -> Unit,
    name: String,
    modifier: Modifier = Modifier,
    gripTint: Color = IcodeColors.Outline,
    actions: List<CustomAccessibilityAction> = emptyList(),
    onSwipeAlong: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val tuck by rememberUpdatedState(onTucked)
    val swipe by rememberUpdatedState(onSwipeAlong)
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val atStart = LocalPillEdge.current == ScreenEdge.Start
    // Docked on the screen's left, it is drawn as on the right, mirrored.
    val onLeft = atStart != rtl
    // Into the edge, in the layout's direction: toward the end for a pill on the end edge.
    val inward = if (atStart) -1f else 1f
    val moves = LocalPillMoves.current(name)
    val hold = LocalPillHold.current
    val density = LocalDensity.current
    val flingPx = with(density) { FLING.toPx() }
    val peekPx = with(density) { PEEK.toPx() }
    val alongPx = with(density) { SWIPE_ALONG.toPx() }
    // How far a swipe along the grip has gone.
    var along by remember { mutableFloatStateOf(0f) }
    val line = edgeLine
    var width by remember { mutableIntStateOf(0) }
    // How far it is pushed into the edge, in pixels.
    var pushed by remember { mutableFloatStateOf(0f) }
    var placed by remember { mutableStateOf(false) }
    val hidden = (width - peekPx).coerceAtLeast(0f)
    LaunchedEffect(tucked, hidden) {
        val rest = if (tucked) hidden else 0f
        // Its first place is taken at once: a pill that starts tucked never flashes out first.
        if (!placed) {
            if (width > 0) placed = true
            pushed = rest
        } else {
            animate(pushed, rest, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> pushed = value }
        }
    }
    fun settle(velocity: Float) {
        val tuckNow = when {
            velocity > flingPx -> true
            velocity < -flingPx -> false
            else -> pushed > hidden / 2
        }
        if (tuckNow != tucked) {
            tuck(tuckNow)
        } else {
            scope.launch { animate(pushed, if (tucked) hidden else 0f, animationSpec = spring()) { value, _ -> pushed = value } }
        }
    }
    // A drag's or a fling's part into the edge: the screen's x, turned to the layout's, turned to the edge's.
    fun intoEdge(x: Float) = (if (rtl) -x else x) * inward
    Row(
        modifier
            .onSizeChanged { width = it.width }
            .offset { IntOffset((inward * pushed).roundToInt(), 0) }
            // Held there, the floating stack picks it up to move it (LocalPillHold).
            .then(hold)
            .draggable(
                rememberDraggableState { delta -> pushed = (pushed + intoEdge(delta)).coerceIn(0f, hidden) },
                Orientation.Horizontal,
                onDragStopped = { velocity -> settle(intoEdge(velocity)) },
            )
            .draggable(
                rememberDraggableState { delta -> along += delta },
                Orientation.Vertical,
                enabled = tucked && onSwipeAlong != null,
                onDragStarted = { along = 0f },
                onDragStopped = { if (abs(along) > alongPx) swipe?.invoke() },
            )
            .semantics {
                customActions = listOf(CustomAccessibilityAction(if (tucked) "Show $name" else "Hide $name") { tuck(!tucked); true }) + actions + moves
            }
            .height(IcodeSizes.DockedPill)
            .clip(if (atStart) DOCKED_AT_START else DOCKED_AT_END)
            .background(IcodeColors.SurfaceElevated)
            .then(if (line != null) Modifier.drawBehind { openEdge(line, onLeft) } else Modifier)
            // Tucked, what shows is its grip: a tap there brings it out.
            .clickable(enabled = tucked, onClickLabel = "Show $name") { tuck(false) }
            .padding(start = if (atStart) IcodeSpacing.Space1 else GRIP_INSET, end = if (atStart) GRIP_INSET else IcodeSpacing.Space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The grip on the open side, nearest the thumb, which the peek shows.
        if (!atStart) Grip(gripTint, Modifier.padding(end = GRIP_GAP))
        content()
        if (atStart) Grip(gripTint, Modifier.padding(start = GRIP_GAP))
    }
}

@Composable
private fun Grip(tint: Color, modifier: Modifier) {
    Box(modifier.width(GRIP_WIDTH).height(GRIP_HEIGHT).background(tint, IcodeShapes.Track))
}

/**
 * An action on a docked pill: [icon] in [tint] (muted when not [enabled]), a [IcodeSizes.DockedAction]
 * wide and the pill tall, with [badge], a count, on it where there is one; a [dot] while something
 * runs there; and a [line] under it, as the rail marks its active entry. Its press shows in a round
 * of its width; what it carries is drawn over that, whole.
 */
@Composable
fun IcodeDockedAction(
    icon: ImageVector,
    description: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier,
    badge: Int? = null,
    dot: Color? = null,
    line: Color? = null,
) {
    val pressed = remember { MutableInteractionSource() }
    Box(
        modifier
            .width(IcodeSizes.DockedAction)
            .height(IcodeSizes.DockedPill)
            .clickable(pressed, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(IcodeSizes.DockedAction).clip(IcodeShapes.Pill).indication(pressed, LocalIndication.current))
        line?.let { UnderLine(it, lift = LINE_LIFT) }
        Icon(icon, contentDescription = description, tint = if (enabled) tint else IcodeColors.TextMuted, modifier = iconModifier.size(IcodeSizes.IconXl))
        IconMarks(badge ?: 0, dot)
    }
}

/** The edge line on the open sides — top, round start, bottom — none along the screen's edge. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.openEdge(line: Color, mirrored: Boolean) {
    val stroke = 1.dp.toPx()
    val inset = stroke / 2
    val r = size.height / 2
    val path = Path().apply {
        moveTo(size.width, inset)
        lineTo(r, inset)
        arcTo(Rect(inset, inset, 2 * r - inset, size.height - inset), 270f, -180f, false)
        lineTo(size.width, size.height - inset)
    }
    scale(if (mirrored) -1f else 1f, 1f) { drawPath(path, line, style = Stroke(stroke)) }
}

/** Round on the open side only: the other is the screen's edge. */
private val DOCKED_AT_END = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
private val DOCKED_AT_START = RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)

/** The grip: a bar the thumb finds in the peek, centred in it. */
private val GRIP_WIDTH = 4.dp
private val GRIP_HEIGHT = 24.dp
private val GRIP_GAP = 6.dp

/** How much of a tucked pill shows: its grip, in a strip a thumb opens without hunting for it. */
private val PEEK = 24.dp
private val GRIP_INSET = (PEEK - GRIP_WIDTH) / 2

/** A docked action's line stands clear of the pill's edge, under its icon. */
private val LINE_LIFT = 5.dp

/** A flick faster than this tucks the pill, or brings it out, however little it moved (as a sheet's swipe). */
private val FLING = 125.dp

/** How far a swipe along the tucked grip goes to count: half the pill's height. */
private val SWIPE_ALONG = 24.dp
