package dev.icode.mobile.designsystem

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * One of the floating stack's pills (owner's choice, 2026-11-04): docked flush on the screen's end
 * edge, round on its open side with a grip there, a corner action tall, its [edgeLine] on the open
 * sides only. Swiped into the edge — past half its width, or flung — it slides away and is put away
 * ([onAway]); short of that it springs back. TalkBack offers the same as an action, [awayLabel].
 */
@Composable
fun IcodeDockedPill(awayLabel: String, onAway: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val away by rememberUpdatedState(onAway)
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val flingPx = with(LocalDensity.current) { FLING.toPx() }
    val line = edgeLine
    // How far it is pushed toward the edge, in pixels; offset follows the layout's direction.
    var pushed by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    fun settle(velocity: Float) {
        scope.launch {
            if (pushed > width / 2f || velocity > flingPx) {
                animate(pushed, width.toFloat(), animationSpec = tween(AWAY_MILLIS)) { value, _ -> pushed = value }
                away()
            } else {
                animate(pushed, 0f, animationSpec = spring()) { value, _ -> pushed = value }
            }
        }
    }
    Row(
        modifier
            .onSizeChanged { width = it.width }
            .offset { IntOffset(pushed.roundToInt(), 0) }
            .draggable(
                rememberDraggableState { delta -> pushed = (pushed + if (rtl) -delta else delta).coerceAtLeast(0f) },
                Orientation.Horizontal,
                onDragStopped = { velocity -> settle(if (rtl) -velocity else velocity) },
            )
            .semantics { customActions = listOf(CustomAccessibilityAction(awayLabel) { away(); true }) }
            .height(IcodeSizes.CornerAction)
            .clip(DOCKED)
            .background(IcodeColors.SurfaceElevated)
            .then(if (line != null) Modifier.drawBehind { openEdge(line, rtl) } else Modifier)
            .padding(start = IcodeSpacing.Space2, end = IcodeSpacing.Space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(end = IcodeSpacing.Space1).width(GRIP_WIDTH).height(IcodeSizes.IconMd).background(IcodeColors.Outline, IcodeShapes.Track))
        content()
    }
}

/**
 * An action on a docked pill: [icon] in [tint] (muted when not [enabled]), a corner action wide —
 * touch finds it a touch target — with [badge], a count, on it where there is one.
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
) {
    Box(
        modifier.size(IcodeSizes.CornerAction).clip(IcodeShapes.Pill).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) tint else IcodeColors.TextMuted, modifier = iconModifier.size(IcodeSizes.IconLg))
        badge?.let { CountBadge(it, Modifier.align(Alignment.Center)) }
    }
}

/** The edge line on the open sides — top, round start, bottom — none along the screen's edge. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.openEdge(line: Color, rtl: Boolean) {
    val stroke = 1.dp.toPx()
    val inset = stroke / 2
    val r = size.height / 2
    val path = Path().apply {
        moveTo(size.width, inset)
        lineTo(r, inset)
        arcTo(Rect(inset, inset, 2 * r - inset, size.height - inset), 270f, -180f, false)
        lineTo(size.width, size.height - inset)
    }
    scale(if (rtl) -1f else 1f, 1f) { drawPath(path, line, style = Stroke(stroke)) }
}

/** Round on the open side only: the other is the screen's edge. */
private val DOCKED = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)

private val GRIP_WIDTH = 3.dp

/** A flick toward the edge faster than this puts the pill away however little it moved (as a sheet's swipe). */
private val FLING = 125.dp
private const val AWAY_MILLIS = 160
