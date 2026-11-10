package dev.icode.mobile.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.round
import dev.icode.mobile.domain.PillPlace
import dev.icode.mobile.domain.PillPlaces
import dev.icode.mobile.domain.ScreenEdge
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * One pill of the floating stack (docs/dock-navigation-plan.md D11): at [place] — null, at the end
 * edge's foot in the stack's order, as the stack has always stood its pills — and [content], the
 * pill. Held and dragged, it goes to the edge it is let go nearer, at that height ([onPlaced]).
 */
@Immutable
class IcodeStackPill(
    val key: Any,
    val place: PillPlace?,
    val onPlaced: (PillPlace) -> Unit,
    val content: @Composable () -> Unit,
)

/** The edge the pill drawn here is docked on: [IcodeDockedPill] rounds and grips its other side. */
val LocalPillEdge = staticCompositionLocalOf { ScreenEdge.End }

/** The hold that picks up the pill drawn here, for [IcodeDockedPill] to put on what shows of it. */
val LocalPillHold = staticCompositionLocalOf<Modifier> { Modifier }

/** TalkBack's ways to move the pill drawn here, named for it: to the other edge, up, down. */
val LocalPillMoves = compositionLocalOf<(String) -> List<CustomAccessibilityAction>> { { emptyList() } }

/**
 * [pills] laid out on the edges, the lowest [floor] up and below the top bar: each at its place, none
 * over another ([PillPlaces.bottoms]). A pill held still for a long press is picked up — the phone
 * ticks, it lifts — and follows the finger; let go, it settles on its new edge and height. Any move,
 * this or the keyboard's, is animated.
 */
@Composable
internal fun AnimatedVisibilityScope.FloatingPills(pills: List<IcodeStackPill>, floor: Dp) {
    val density = LocalDensity.current
    val floorPx = with(density) { floor.toPx() }
    val gap = IcodeSpacing.Space2
    val gapPx = with(density) { gap.toPx() }
    // As high as a pill may go: under the top bar, with its subtitle, and a gap.
    val topPx = with(density) { (IcodeSizes.TopBarWithSubtitle + gap).toPx() }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = { pills.forEach { pill -> key(pill.key) { PillSlot(pill, floorPx, topPx) } } },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val height = constraints.maxHeight
        val room = height - floorPx - topPx
        val tops = IntArray(placeables.size)
        ScreenEdge.entries.forEach { edge ->
            val here = pills.indices.filter { (pills[it].place?.edge ?: ScreenEdge.End) == edge }
            val bottoms = PillPlaces.bottoms(here.map { PillPlaces.Laid(placeables[it].height.toFloat(), pills[it].place?.height) }, room, gapPx)
            here.forEachIndexed { at, i -> tops[i] = (height - floorPx - bottoms[at]).roundToInt() - placeables[i].height }
        }
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { i, placeable ->
                val end = (pills[i].place?.edge ?: ScreenEdge.End) == ScreenEdge.End
                placeable.placeRelative(if (end) constraints.maxWidth - placeable.width else 0, tops[i])
            }
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.PillSlot(pill: IcodeStackPill, floorPx: Float, topPx: Float) {
    val edge = pill.place?.edge ?: ScreenEdge.End
    val onRight = pillOnRight(edge)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Read as they are when a pill is let go, the keyboard up or down since it was first drawn.
    val placed by rememberUpdatedState(pill.onPlaced)
    val floor by rememberUpdatedState(floorPx)
    val top by rememberUpdatedState(topPx)
    val rtl by rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)
    // Where the stack put it last; how far from there it is drawn on its way to a new place; the finger's pull.
    var laidAt by remember { mutableStateOf<IntOffset?>(null) }
    val motion = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var held by remember { mutableStateOf(false) }
    // The slot as drawn, and what shows of the pill in it, which the finger holds.
    val drawn = remember { arrayOfNulls<LayoutCoordinates>(2) }

    /**
     * The place it is drawn at now, as the user would keep it: on [edgeAt], or the edge nearer
     * [fingerX] or its own middle; [raise] higher. Null before it is drawn.
     */
    fun placeNow(edgeAt: ScreenEdge? = null, raise: Float = 0f, fingerX: Float? = null): PillPlace? {
        val here = drawn[0]?.takeIf { it.isAttached } ?: return null
        val parent = here.parentLayoutCoordinates ?: return null
        val at = here.positionInParent()
        val room = parent.size.height - floor - top
        val bottom = parent.size.height - floor - (at.y + here.size.height)
        val x = fingerX ?: (at.x + here.size.width / 2f)
        return PillPlace(
            edgeAt ?: PillPlaces.edgeAt(x, parent.size.width.toFloat(), rtl),
            (PillPlaces.heightAt(bottom, here.size.height.toFloat(), room) + raise).coerceIn(0f, 1f),
        )
    }
    val moves: (String) -> List<CustomAccessibilityAction> = remember(edge, onRight) {
        { name ->
            val other = if (edge == ScreenEdge.End) ScreenEdge.Start else ScreenEdge.End
            listOf(
                CustomAccessibilityAction("Move $name to the ${if (onRight) "left" else "right"} edge") { placeNow(other)?.let(placed) != null },
                CustomAccessibilityAction("Move $name up") { placeNow(edge, MOVE_STEP)?.let(placed) != null },
                CustomAccessibilityAction("Move $name down") { placeNow(edge, -MOVE_STEP)?.let(placed) != null },
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
                held = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                // Held: every touch now moves it, and nothing in it is tapped or swiped.
                var finger = down.position
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    event.changes.forEach { it.consume() }
                    if (change == null) break
                    finger = change.position
                    if (!change.pressed) break
                    drag += change.positionChange()
                }
                val shown = drawn[1]?.takeIf { it.isAttached }
                val fingerX = shown?.let { drawn[0]?.parentLayoutCoordinates?.localPositionOf(it, finger)?.x }
                val place = placeNow(fingerX = fingerX)
                held = false
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
            .graphicsLayer {
                val lift = if (held) HELD_SCALE else 1f
                scaleX = lift
                scaleY = lift
            }
            .animateEnterExit(enter = pillEnter(edge), exit = pillExit(edge)),
    ) {
        CompositionLocalProvider(LocalPillEdge provides edge, LocalPillMoves provides moves, LocalPillHold provides hold) { pill.content() }
    }
}

/** A pill coming out from its edge ([LocalPillEdge]): what one that shows only at times shows with. */
@Composable
fun pillEnter(edge: ScreenEdge = LocalPillEdge.current): EnterTransition {
    val toRight = pillOnRight(edge)
    return slideInHorizontally { if (toRight) it else -it } + fadeIn()
}

/** A pill going into its edge. */
@Composable
fun pillExit(edge: ScreenEdge = LocalPillEdge.current): ExitTransition {
    val toRight = pillOnRight(edge)
    return slideOutHorizontally { if (toRight) it else -it } + fadeOut()
}

@Composable
private fun pillOnRight(edge: ScreenEdge): Boolean = (edge == ScreenEdge.End) != (LocalLayoutDirection.current == LayoutDirection.Rtl)

/** How far TalkBack's Move up or Move down takes a pill: a fifth of the room. */
private const val MOVE_STEP = 0.2f

/** A pill held to be moved stands a little bigger, as picked up. */
private const val HELD_SCALE = 1.06f
