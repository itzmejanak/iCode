package dev.icode.mobile.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One of the floating stack's pills (owner's choices, 2026-11-04): docked flush on the screen's edge
 * the stack puts it on ([LocalPillEdge]) — a side, the top or the bottom — round on its open side, a
 * touch target tall, its [edgeLine] on the open sides only, with a handle on that side. It never goes:
 * [tucked], it rests in the edge with only its handle showing, a strip a thumb finds; swiped into the
 * edge — past half its way, or flung — it tucks, and tapped or pulled out it shows ([onTucked]); short
 * of either it springs back. TalkBack has both as actions on [name], then [actions], then the stack's
 * moves. On the top or the bottom, [content] is the same row, its handle in a strip above or below it.
 *
 * Tucked, its handle is its own icon ([LocalPillIcon]), so each pill is known at a glance; out, a grip.
 * Both take [tone], what needs the user there, if anything. Tucked and left alone, it rests as the
 * assistant's bubble does ([IcodeResting]): it fades, and a touch, a new [tone] or coming out wakes it.
 *
 * With [onSwipeAlong], a swipe along the tucked handle, over half the pill's height, does that: the
 * dock's switch to the tool used before (docs/dock-navigation-plan.md D9).
 */
@Composable
fun IcodeDockedPill(
    tucked: Boolean,
    onTucked: (Boolean) -> Unit,
    name: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    actions: List<CustomAccessibilityAction> = emptyList(),
    onSwipeAlong: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val tuck by rememberUpdatedState(onTucked)
    val swipe by rememberUpdatedState(onSwipeAlong)
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val edge = LocalPillEdge.current
    val across = edge.across
    // Each is drawn as on the right or the bottom, mirrored on the left or the top.
    val mirrored = when (edge) {
        ScreenEdge.Start -> !rtl
        ScreenEdge.End -> rtl
        ScreenEdge.Top -> true
        ScreenEdge.Bottom -> false
    }
    // Into the edge: toward the end, in the layout's direction, for a pill on the end edge; down for one on the bottom.
    val inward = if (edge == ScreenEdge.Start || edge == ScreenEdge.Top) -1f else 1f
    val moves = LocalPillMoves.current(name)
    val hold = LocalPillHold.current
    val icon = LocalPillIcon.current
    // Resting: tucked and left alone a while. Each touch, a new tone, or coming out starts the while again.
    var touches by remember { mutableIntStateOf(0) }
    var resting by remember { mutableStateOf(false) }
    LaunchedEffect(tucked, tone, touches) {
        resting = false
        if (!tucked) return@LaunchedEffect
        delay(IcodeResting.AFTER_MILLIS)
        resting = true
    }
    val fade by animateFloatAsState(if (resting) IcodeResting.ALPHA else 1f, label = "resting")
    val density = LocalDensity.current
    val flingPx = with(density) { FLING.toPx() }
    val peekPx = with(density) { PEEK.toPx() }
    val alongPx = with(density) { SWIPE_ALONG.toPx() }
    // How far a swipe along the grip has gone.
    var along by remember { mutableFloatStateOf(0f) }
    val line = edgeLine
    // How deep it reaches from its edge: its width on a side, its height on the top or the bottom.
    var depth by remember { mutableIntStateOf(0) }
    // How far it is pushed into the edge, in pixels.
    var pushed by remember { mutableFloatStateOf(0f) }
    var placed by remember { mutableStateOf(false) }
    val hidden = (depth - peekPx).coerceAtLeast(0f)
    LaunchedEffect(tucked, hidden) {
        val rest = if (tucked) hidden else 0f
        // Its first place is taken at once: a pill that starts tucked never flashes out first.
        if (!placed) {
            if (depth > 0) placed = true
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
    // A drag's or a fling's part into the edge: the screen's x (in the layout's direction) or y, turned to the edge's.
    fun intoEdge(delta: Float) = (if (!across && rtl) -delta else delta) * inward
    val body = modifier
        .onSizeChanged { depth = if (across) it.height else it.width }
        .offset { (inward * pushed).roundToInt().let { if (across) IntOffset(0, it) else IntOffset(it, 0) } }
        .graphicsLayer { alpha = fade }
        // Any touch wakes it, before what the touch does.
        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); touches++ } }
        // Held there, the floating stack picks it up to move it (LocalPillHold).
        .then(hold)
        .draggable(
            rememberDraggableState { delta -> pushed = (pushed + intoEdge(delta)).coerceIn(0f, hidden) },
            if (across) Orientation.Vertical else Orientation.Horizontal,
            onDragStopped = { velocity -> settle(intoEdge(velocity)) },
        )
        .draggable(
            rememberDraggableState { delta -> along += delta },
            if (across) Orientation.Horizontal else Orientation.Vertical,
            enabled = tucked && onSwipeAlong != null,
            onDragStarted = { along = 0f },
            onDragStopped = { if (abs(along) > alongPx) swipe?.invoke() },
        )
        .semantics {
            customActions = listOf(CustomAccessibilityAction(if (tucked) "Show $name" else "Hide $name") { tuck(!tucked); true }) + actions + moves
        }
        .then(if (across) Modifier else Modifier.height(IcodeSizes.DockedPill))
        .clip(shapeOn(edge))
        .background(IcodeColors.SurfaceElevated)
        .then(if (line != null) Modifier.drawBehind { openEdge(line, across, mirrored) } else Modifier)
        // Tucked, what shows is its grip: a tap there brings it out.
        .clickable(enabled = tucked, onClickLabel = "Show $name") { tuck(false) }
    if (across) {
        Column(body.padding(horizontal = IcodeSpacing.Space1), horizontalAlignment = Alignment.CenterHorizontally) {
            // The handle on the open side, which the peek shows.
            if (edge == ScreenEdge.Bottom) Handle(icon, tucked, tone, lying = true, Modifier.height(PEEK))
            Row(Modifier.height(IcodeSizes.DockedPill), verticalAlignment = Alignment.CenterVertically, content = content)
            if (edge == ScreenEdge.Top) Handle(icon, tucked, tone, lying = true, Modifier.height(PEEK))
        }
    } else {
        val atStart = edge == ScreenEdge.Start
        Row(body.padding(start = if (atStart) IcodeSpacing.Space1 else 0.dp, end = if (atStart) 0.dp else IcodeSpacing.Space1), verticalAlignment = Alignment.CenterVertically) {
            // The handle on the open side, nearest the thumb, which the peek shows.
            if (!atStart) Handle(icon, tucked, tone, lying = false, Modifier.width(PEEK))
            content()
            if (atStart) Handle(icon, tucked, tone, lying = false, Modifier.width(PEEK))
        }
    }
}

/**
 * What shows of a tucked pill, and leads it out: [icon], the pill's own, while [tucked] — so the
 * pills tell apart at a glance — else a grip, [lying] along a pill on the top or the bottom, standing
 * up on a side. Both in [tone], what needs the user, if anything.
 */
@Composable
private fun Handle(icon: ImageVector?, tucked: Boolean, tone: Color?, lying: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Crossfade(tucked && icon != null, label = "handle") { showIcon ->
            if (showIcon && icon != null) {
                Icon(icon, contentDescription = null, tint = tone ?: IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconMd))
            } else {
                val bar = if (lying) Modifier.width(GRIP_LENGTH).height(GRIP_WIDTH) else Modifier.width(GRIP_WIDTH).height(GRIP_LENGTH)
                Box(bar.background(tone ?: IcodeColors.Outline, IcodeShapes.Track))
            }
        }
    }
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

/** Round on the open side only: the other is the screen's edge. */
private fun shapeOn(edge: ScreenEdge) = when (edge) {
    ScreenEdge.End -> RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    ScreenEdge.Start -> RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    ScreenEdge.Bottom -> RoundedCornerShape(topStart = ACROSS_CORNER, topEnd = ACROSS_CORNER)
    ScreenEdge.Top -> RoundedCornerShape(bottomStart = ACROSS_CORNER, bottomEnd = ACROSS_CORNER)
}

/**
 * The edge line on the open sides, none along the screen's edge: drawn for a pill on the right — top,
 * round left, bottom — or on the bottom — left, round top, right — and [mirrored] for the other two.
 */
private fun DrawScope.openEdge(line: Color, across: Boolean, mirrored: Boolean) {
    val stroke = 1.dp.toPx()
    val path = if (across) onBottom(size, ACROSS_CORNER.toPx(), stroke / 2) else onRight(size, stroke / 2)
    scale(if (!across && mirrored) -1f else 1f, if (across && mirrored) -1f else 1f) { drawPath(path, line, style = Stroke(stroke)) }
}

private fun onRight(size: Size, inset: Float) = Path().apply {
    val r = size.height / 2
    moveTo(size.width, inset)
    lineTo(r, inset)
    arcTo(Rect(inset, inset, 2 * r - inset, size.height - inset), 270f, -180f, false)
    lineTo(size.width, size.height - inset)
}

private fun onBottom(size: Size, corner: Float, inset: Float) = Path().apply {
    val r = min(corner, min(size.width / 2, size.height))
    moveTo(inset, size.height)
    lineTo(inset, r)
    arcTo(Rect(inset, inset, 2 * r - inset, 2 * r - inset), 180f, 90f, false)
    lineTo(size.width - r, inset)
    arcTo(Rect(size.width - 2 * r + inset, inset, size.width - inset, 2 * r - inset), 270f, 90f, false)
    lineTo(size.width - inset, size.height)
}

/** The grip of a pill that is out: a bar centred in its handle. */
private val GRIP_WIDTH = 4.dp
private val GRIP_LENGTH = 24.dp

/** How much of a tucked pill shows: its handle, its icon clear in it, a strip a thumb opens without hunting for it. */
private val PEEK = 30.dp

/** A pill on the top or the bottom: its open corners, as round as a side pill's end. */
private val ACROSS_CORNER = 24.dp

/** A docked action's line stands clear of the pill's edge, under its icon. */
private val LINE_LIFT = 5.dp

/** A flick faster than this tucks the pill, or brings it out, however little it moved (as a sheet's swipe). */
private val FLING = 125.dp

/** How far a swipe along the tucked grip goes to count: half the pill's height. */
private val SWIPE_ALONG = 24.dp
