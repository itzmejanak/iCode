package dev.icode.mobile.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import dev.icode.mobile.domain.PillPlace
import dev.icode.mobile.domain.PillPlaces
import dev.icode.mobile.domain.ScreenEdge
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * One pill of the floating stack (docs/dock-navigation-plan.md D11, D13): at [place] — null, at the
 * end edge's foot in the stack's order, as the stack has always stood its pills — and [content], the
 * pill. [icon] is the pill's own: it shows it closed, and it floats under the finger while the pill is
 * moved. Let go, it docks where [onPlaced] says.
 */
@Immutable
class IcodeStackPill(
    val key: Any,
    val icon: ImageVector,
    val place: PillPlace?,
    val onPlaced: (PillPlace) -> Unit,
    val content: @Composable () -> Unit,
)

/** The edge the pill drawn here is docked on: it comes out of that edge and goes back into it. */
val LocalPillEdge = staticCompositionLocalOf { ScreenEdge.End }

/** The pill drawn here's own icon ([IcodeStackPill.icon]). */
val LocalPillIcon = staticCompositionLocalOf<ImageVector> { Icons.Outlined.Apps }

/** The pill drawn here keeps its icon at its row's end, so its row opens toward the start ([PillPlaces.iconAtEnd]). */
val LocalPillIconAtEnd = staticCompositionLocalOf { true }

/** TalkBack's ways to move the pill drawn here, named for it: to each other edge, and along its own. */
val LocalPillMoves = compositionLocalOf<(String) -> List<CustomAccessibilityAction>> { { emptyList() } }

/** Where the stack's layout last stood a pill, for the pill to glide there, and to say where it is. */
private class Footing {
    var at: IntOffset? = null
    /** The footing came from a pill moved to a new place: getting there is a move to show. */
    var glide = false
    var spot = PillPlaces.Spot(0f, 0f)
    var size = 0f
    var areaWidth = 0f
    var areaHeight = 0f
}

/**
 * [pills] on the screen's edges, [EDGE_INSET] in from them, each at its place and none over another
 * ([PillPlaces.spots]); those at no place [floor] up, clear of the bars. Each stands on a closed pill's
 * square, its footing: opened, its row grows from there, its icon kept on the footing, so nothing moves
 * as it opens. A pill is moved as antigem's floating switcher is: a finger moving on it past the touch
 * slop picks it up at once — the phone ticks, and it lifts off its edge as its icon in a floating
 * bubble — and it follows the finger anywhere on the screen. Let go, it docks on the edge nearest the
 * finger, left, right, top or bottom, where the finger was along it, and glides there. A tap is the
 * pill's own.
 */
@Composable
internal fun AnimatedVisibilityScope.FloatingPills(pills: List<IcodeStackPill>, floor: Dp) {
    val density = LocalDensity.current
    val floorPx = with(density) { floor.toPx() }
    val gapPx = with(density) { IcodeSpacing.Space2.toPx() }
    val insetPx = with(density) { EDGE_INSET.toPx() }
    val footPx = with(density) { IcodeSizes.DockedPill.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val footings = remember { HashMap<Any, Footing>() }
    val laidPlaces = remember { arrayOfNulls<List<PillPlace?>>(1) }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = { pills.forEach { pill -> key(pill.key) { PillSlot(pill, footings.getOrPut(pill.key) { Footing() }) } } },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val areaWidth = width - 2 * insetPx
        val areaHeight = height - 2 * insetPx
        // A pill showing nothing takes no room: the bell stays put while the undo pill comes and goes.
        val laid = pills.indices.map { i ->
            if (placeables[i].height > 0) PillPlaces.Laid(footPx, footPx, pills[i].place) else PillPlaces.Laid(0f, 0f, pills[i].place)
        }
        val spots = PillPlaces.spots(laid, areaWidth, areaHeight, (floorPx - insetPx).coerceAtLeast(0f), gapPx, rtl)
        val places = pills.map { it.place }
        val moved = laidPlaces[0] != null && laidPlaces[0] != places
        laidPlaces[0] = places
        layout(width, height) {
            placeables.forEachIndexed { i, placeable ->
                val footX = insetPx + spots[i].x
                val footY = insetPx + spots[i].y
                footings[pills[i].key]?.apply {
                    at = IntOffset(footX.roundToInt(), footY.roundToInt())
                    glide = moved
                    spot = spots[i]
                    size = footPx
                    this.areaWidth = areaWidth
                    this.areaHeight = areaHeight
                }
                // Opened, its row grows from the footing, its icon's end kept there, and stays on the screen.
                val iconOnRight = PillPlaces.iconAtEnd(pills[i].place) != rtl
                val x = (if (iconOnRight) footX + footPx - placeable.width else footX)
                    .coerceIn(insetPx, max(insetPx, width - insetPx - placeable.width))
                val y = (footY + (footPx - placeable.height) / 2).coerceIn(insetPx, max(insetPx, height - insetPx - placeable.height))
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.PillSlot(pill: IcodeStackPill, footing: Footing) {
    val edge = pill.place?.edge ?: ScreenEdge.End
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val placed by rememberUpdatedState(pill.onPlaced)
    val rtl by rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)
    // How far from its footing it is drawn on its way to a new one, and the finger's pull while it is moved.
    val motion = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    // Picked up: where in it the finger holds it, which the bubble floats under.
    var grab by remember { mutableStateOf<Offset?>(null) }
    val lift by animateFloatAsState(if (grab != null) 1f else 0f, spring(stiffness = Spring.StiffnessMedium), label = "lift")
    // Plain bookkeeping, which the layout and the gesture read and write without drawing again.
    val drawn = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val lastAt = remember { arrayOfNulls<IntOffset>(1) }
    // Let go, waiting for its new footing to glide to.
    val settling = remember { BooleanArray(1) }

    /** Where it stands now, along [onto] — its own edge, or another it is moved to — [step] further along. */
    fun movedTo(onto: ScreenEdge, step: Float = 0f): PillPlace? {
        if (footing.at == null) return null
        val along = PillPlaces.alongOf(edge, footing.spot, footing.size, footing.size, footing.areaWidth, footing.areaHeight, rtl)
        return PillPlace(onto, (along + step).coerceIn(0f, 1f))
    }
    val moves: (String) -> List<CustomAccessibilityAction> = remember(edge) {
        { name ->
            val other = ScreenEdge.entries.filter { it != edge }.map { onto ->
                CustomAccessibilityAction("Move $name to the ${edgeName(onto, rtl)} edge") { movedTo(onto)?.let(placed) != null }
            }
            // Along a side, up and down; along the top or the bottom, toward its start and its end.
            val (back, forth) = if (edge.across) {
                if (rtl) "right" to "left" else "left" to "right"
            } else {
                "down" to "up"
            }
            other + listOf(
                CustomAccessibilityAction("Move $name $forth") { movedTo(edge, MOVE_STEP)?.let(placed) != null },
                CustomAccessibilityAction("Move $name $back") { movedTo(edge, -MOVE_STEP)?.let(placed) != null },
            )
        }
    }
    Box(
        Modifier
            // A new footing from a move is drawn as a glide there; one from the keyboard or the bars follows them as they go.
            .onPlaced {
                val now = footing.at ?: return@onPlaced
                val before = lastAt[0]
                lastAt[0] = now
                if (before != null && before != now && (footing.glide || settling[0])) {
                    settling[0] = false
                    scope.launch {
                        motion.snapTo(motion.value + (before - now))
                        motion.animateTo(IntOffset.Zero, GLIDE)
                    }
                }
            }
            .absoluteOffset { motion.value + drag.round() }
            .onGloballyPositioned { drawn[0] = it }
            .pointerInput(pill.key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // The pill's buttons take the down first, as their own; a move past the slop from here
                    // is the pill's, which cancels their tap.
                    awaitPointerEvent()
                    var over = Offset.Zero
                    val start = awaitTouchSlopOrCancellation(down.id) { change, slop ->
                        change.consume()
                        over = slop
                    } ?: return@awaitEachGesture
                    grab = start.position - over
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    drag += over
                    drag(start.id) { change ->
                        drag += change.positionChange()
                        change.consume()
                    }
                    // Where the finger let go, in the area the pills dock in: the bubble has followed it.
                    val place = drawn[0]?.takeIf { it.isAttached }?.let { here ->
                        val parent = here.parentLayoutCoordinates ?: return@let null
                        val finger = parent.localPositionOf(here, grab ?: Offset.Zero)
                        val inset = EDGE_INSET.toPx()
                        PillPlaces.placeAt(
                            finger.x - inset, finger.y - inset, footing.areaWidth, footing.areaHeight,
                            footing.size, footing.size, rtl,
                        )
                    }
                    grab = null
                    // Drawn where it was let go, from its footing; then on to its new one.
                    scope.launch {
                        motion.snapTo(motion.value + drag.round())
                        drag = Offset.Zero
                        settling[0] = true
                        place?.let(placed)
                        // Let go where it already stood, no new footing comes: it glides back from here.
                        withFrameNanos { }
                        withFrameNanos { }
                        if (settling[0]) {
                            settling[0] = false
                            motion.animateTo(IntOffset.Zero, GLIDE)
                        }
                    }
                }
            }
            .animateEnterExit(enter = pillEnter(edge), exit = pillExit(edge)),
    ) {
        // Lifted, the pill gives way to its bubble; let go, it comes back as the bubble goes.
        Box(Modifier.graphicsLayer { alpha = 1f - lift }) {
            CompositionLocalProvider(
                LocalPillEdge provides edge,
                LocalPillIcon provides pill.icon,
                LocalPillIconAtEnd provides PillPlaces.iconAtEnd(pill.place),
                LocalPillMoves provides moves,
            ) { pill.content() }
        }
        val at = grab
        if (at != null || lift > 0f) FloatingBubble(pill.icon, at, lift)
    }
}

/** The pill being moved: its [icon] in a raised bubble centred [at] the finger, [lift] of the way up. */
@Composable
private fun FloatingBubble(icon: ImageVector, at: Offset?, lift: Float) {
    var last by remember { mutableStateOf(Offset.Zero) }
    if (at != null) last = at
    val line = edgeLine
    val bubble = with(LocalDensity.current) { IcodeSizes.Bubble.toPx() }
    Box(
        Modifier
            .absoluteOffset { (last - Offset(bubble / 2, bubble / 2)).round() }
            .graphicsLayer {
                val scale = BUBBLE_FROM + (1f - BUBBLE_FROM) * lift
                scaleX = scale
                scaleY = scale
                alpha = lift
            }
            .size(IcodeSizes.Bubble)
            .shadow(BUBBLE_ELEVATION, CircleShape)
            .background(IcodeColors.SurfaceElevated, CircleShape)
            .then(if (line != null) Modifier.border(1.dp, line, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconXl))
    }
}

/** A pill coming out from its edge ([LocalPillEdge]): what one that shows only at times shows with. */
@Composable
fun pillEnter(edge: ScreenEdge = LocalPillEdge.current): EnterTransition {
    val way = outward(edge)
    return if (edge.across) slideInVertically { way * it } + fadeIn() else slideInHorizontally { way * it } + fadeIn()
}

/** A pill going into its edge. */
@Composable
fun pillExit(edge: ScreenEdge = LocalPillEdge.current): ExitTransition {
    val way = outward(edge)
    return if (edge.across) slideOutVertically { way * it } + fadeOut() else slideOutHorizontally { way * it } + fadeOut()
}

/** Toward [edge] on the screen: 1 for the right or the bottom, -1 for the left or the top. */
@Composable
private fun outward(edge: ScreenEdge): Int {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return when (edge) {
        ScreenEdge.End -> if (rtl) -1 else 1
        ScreenEdge.Start -> if (rtl) 1 else -1
        ScreenEdge.Bottom -> 1
        ScreenEdge.Top -> -1
    }
}

/** The screen's own name for [edge], as the user sees it. */
private fun edgeName(edge: ScreenEdge, rtl: Boolean): String = when (edge) {
    ScreenEdge.Start -> if (rtl) "right" else "left"
    ScreenEdge.End -> if (rtl) "left" else "right"
    ScreenEdge.Top -> "top"
    ScreenEdge.Bottom -> "bottom"
}

/** How far in from the screen's edges a pill stands: off the edge itself, where Android's gestures start. */
private val EDGE_INSET = 4.dp

/** How a pill glides to its new place: as antigem's voice orb settles on its edge. */
private val GLIDE = spring<IntOffset>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

/** How far TalkBack's moves along an edge take a pill: a fifth of the edge. */
private const val MOVE_STEP = 0.2f

/** The bubble rises from smaller as it is picked up, and sinks back so as it is let go. */
private const val BUBBLE_FROM = 0.6f
private val BUBBLE_ELEVATION = 8.dp
