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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.positionInParent
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * One pill of the floating stack (docs/dock-navigation-plan.md D11): at [place] — null, at the end
 * edge's foot in the stack's order, as the stack has always stood its pills — and [content], the
 * pill. [icon] is the pill's own: its tucked handle shows it, and it floats under the finger while
 * the pill is moved. Let go, it docks where [onPlaced] says.
 */
@Immutable
class IcodeStackPill(
    val key: Any,
    val icon: ImageVector,
    val place: PillPlace?,
    val onPlaced: (PillPlace) -> Unit,
    val content: @Composable () -> Unit,
)

/** The edge the pill drawn here is docked on: [IcodeDockedPill] rounds and grips its other side. */
val LocalPillEdge = staticCompositionLocalOf { ScreenEdge.End }

/** The pill drawn here's own icon ([IcodeStackPill.icon]): what its tucked handle shows. */
val LocalPillIcon = staticCompositionLocalOf<ImageVector?> { null }

/** The hold that picks up the pill drawn here, for [IcodeDockedPill] to put on what shows of it. */
val LocalPillHold = staticCompositionLocalOf<Modifier> { Modifier }

/** TalkBack's ways to move the pill drawn here, named for it: to each other edge, and along its own. */
val LocalPillMoves = compositionLocalOf<(String) -> List<CustomAccessibilityAction>> { { emptyList() } }

/**
 * [pills] on the screen's edges, each at its place, none over another ([PillPlaces.spots]); those at
 * no place [floor] up, clear of the bars. A pill held still for a long press is picked up: the phone
 * ticks and it lifts off its edge as its icon in a floating bubble, which follows the finger anywhere
 * on the screen. Let go, it docks on the edge nearest the finger — left, right, top or bottom — where
 * the finger was along it. Any move, this or the keyboard's, is animated.
 */
@Composable
internal fun AnimatedVisibilityScope.FloatingPills(pills: List<IcodeStackPill>, floor: Dp) {
    val density = LocalDensity.current
    val floorPx = with(density) { floor.toPx() }
    val gapPx = with(density) { IcodeSpacing.Space2.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = { pills.forEach { pill -> key(pill.key) { PillSlot(pill) } } },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val laid = pills.indices.map { PillPlaces.Laid(placeables[it].width.toFloat(), placeables[it].height.toFloat(), pills[it].place) }
        val spots = PillPlaces.spots(laid, width.toFloat(), height.toFloat(), floorPx, gapPx, rtl)
        layout(width, height) {
            placeables.forEachIndexed { i, placeable -> placeable.place(spots[i].x.roundToInt(), spots[i].y.roundToInt()) }
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.PillSlot(pill: IcodeStackPill) {
    val edge = pill.place?.edge ?: ScreenEdge.End
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val placed by rememberUpdatedState(pill.onPlaced)
    val rtl by rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)
    // Where the stack put it last; how far from there it is drawn on its way to a new place; the finger's pull.
    var laidAt by remember { mutableStateOf<IntOffset?>(null) }
    val motion = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    // Held: where in the slot the finger holds it, which the bubble floats under.
    var grab by remember { mutableStateOf<Offset?>(null) }
    val lift by animateFloatAsState(if (grab != null) 1f else 0f, spring(stiffness = Spring.StiffnessMedium), label = "lift")
    // The slot as drawn, and what shows of the pill in it, which the finger holds.
    val drawn = remember { arrayOfNulls<LayoutCoordinates>(2) }

    /** The screen's size, and this pill's spot and size on it, as drawn; null before it is drawn. */
    fun screen(): Pair<LayoutCoordinates, LayoutCoordinates>? {
        val here = drawn[0]?.takeIf { it.isAttached } ?: return null
        return (here.parentLayoutCoordinates ?: return null) to here
    }

    /** Where it is now, along [onto] — its own edge, or another it is moved to — [step] further along. */
    fun movedTo(onto: ScreenEdge, step: Float = 0f): PillPlace? {
        val (parent, here) = screen() ?: return null
        val at = here.positionInParent()
        val along = PillPlaces.alongOf(
            edge, PillPlaces.Spot(at.x, at.y), here.size.width.toFloat(), here.size.height.toFloat(),
            parent.size.width.toFloat(), parent.size.height.toFloat(), rtl,
        )
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
    // On what shows of the pill only: the slot is as wide as the pill out, and the empty part of a
    // tucked pill's slot must not take the screen's touches.
    val hold = remember(pill.key) {
        Modifier.onGloballyPositioned { drawn[1] = it }.pointerInput(pill.key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // Until a long press's time, a lift or a move past the slop is the pill's own tap or swipe.
                val gone = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    while (change != null && change.pressed && (change.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                        change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    }
                    true
                }
                if (gone != null) return@awaitEachGesture
                val shown = drawn[1]?.takeIf { it.isAttached } ?: return@awaitEachGesture
                val slot = drawn[0]?.takeIf { it.isAttached } ?: return@awaitEachGesture
                grab = slot.localPositionOf(shown, down.position)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                // Held: every touch now moves it, and nothing in it is tapped or swiped.
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    event.changes.forEach { it.consume() }
                    if (change == null || !change.pressed) break
                    drag += change.positionChange()
                }
                // Where the finger let go, on the screen: the slot has followed it, so it is where it was grabbed.
                val place = screen()?.let { (parent, here) ->
                    val finger = parent.localPositionOf(here, grab ?: Offset.Zero)
                    PillPlaces.placeAt(
                        finger.x, finger.y, parent.size.width.toFloat(), parent.size.height.toFloat(),
                        here.size.width.toFloat(), here.size.height.toFloat(), rtl,
                    )
                }
                grab = null
                // Drawn where it was let go, from where the stack has it; then on to its new place.
                scope.launch {
                    motion.snapTo(motion.value + drag.round())
                    drag = Offset.Zero
                    place?.let(placed)
                }
            }
        }
    }
    Box(
        Modifier
            // Where the stack lays it out: a change of place is drawn as a move there.
            .onPlaced { coordinates ->
                val now = coordinates.positionInParent().round()
                val before = laidAt
                laidAt = now
                if (before != null && before != now) {
                    scope.launch {
                        motion.snapTo(motion.value + (before - now))
                        motion.animateTo(IntOffset.Zero, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                }
            }
            .absoluteOffset { motion.value + drag.round() }
            .onGloballyPositioned { drawn[0] = it }
            .animateEnterExit(enter = pillEnter(edge), exit = pillExit(edge)),
    ) {
        // Lifted, the pill gives way to its bubble; let go, it comes back as the bubble goes.
        Box(Modifier.graphicsLayer { alpha = 1f - lift }) {
            CompositionLocalProvider(
                LocalPillEdge provides edge,
                LocalPillIcon provides pill.icon,
                LocalPillMoves provides moves,
                LocalPillHold provides hold,
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

/** How far TalkBack's moves along an edge take a pill: a fifth of the edge. */
private const val MOVE_STEP = 0.2f

/** The bubble rises from smaller as it is picked up, and sinks back so as it is let go. */
private const val BUBBLE_FROM = 0.6f
private val BUBBLE_ELEVATION = 8.dp
