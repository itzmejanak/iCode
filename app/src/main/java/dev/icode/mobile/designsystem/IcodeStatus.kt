package dev.icode.mobile.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A count on an icon's upper right — the rail's pending changes, the bell's unread notices. Placed
 * centred over a [IcodeSizes.TouchTarget] box that holds the icon. Counts above 99 read "99+".
 */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(x = COUNT_BADGE_OFFSET, y = -COUNT_BADGE_OFFSET)
            .defaultMinSize(minWidth = COUNT_BADGE_SIZE, minHeight = COUNT_BADGE_SIZE)
            .clip(CircleShape)
            .background(IcodeColors.Accent)
            .padding(horizontal = IcodeSpacing.Micro),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > COUNT_BADGE_MAX) "$COUNT_BADGE_MAX+" else count.toString(),
            style = IcodeType.Badge.copy(color = IcodeColors.BackgroundDeep),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private val COUNT_BADGE_SIZE = 16.dp
private val COUNT_BADGE_OFFSET = 11.dp
private const val COUNT_BADGE_MAX = 99

/** Small saturated dot marking a status. */
@Composable
fun StatusDot(color: Color, size: Dp = 7.dp) {
    Box(Modifier.size(size).background(color, IcodeShapes.Pill))
}

/** Dot plus label, used in headers, rows and panels. */
@Composable
fun StatusLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro + IcodeSpacing.Space1),
    ) {
        StatusDot(color)
        Text(text, style = IcodeType.Label.copy(color = color, fontSize = 11.sp))
    }
}

/** Horizontal progress track with accent fill by default. */
@Composable
fun ProgressTrack(progress: Float, modifier: Modifier = Modifier, color: Color = IcodeColors.Accent) {
    Box(modifier.height(5.dp).background(IcodeColors.SurfaceTrack, IcodeShapes.Track)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).background(color, IcodeShapes.Track))
    }
}

/**
 * The same track for work whose share is unknown.
 *
 * A network command reports no percentage until it has contacted the remote,
 * and a bar frozen at zero reads as a hang rather than as waiting.
 */
@Composable
fun IndeterminateProgressTrack(modifier: Modifier = Modifier, color: Color = IcodeColors.Accent) {
    val transition = rememberInfiniteTransition(label = "indeterminate")
    val start by transition.animateFloat(
        initialValue = -SWEEP_WIDTH,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SWEEP_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    BoxWithConstraints(modifier.height(5.dp).background(IcodeColors.SurfaceTrack, IcodeShapes.Track)) {
        val width = maxWidth
        Box(
            Modifier
                // Read at placement, so the sweep moves without recomposing every frame.
                .offset { IntOffset((width * start.coerceIn(-SWEEP_WIDTH, 1f)).roundToPx(), 0) }
                .width(width * SWEEP_WIDTH)
                .height(5.dp)
                .background(color, IcodeShapes.Track),
        )
    }
}

private const val SWEEP_WIDTH = 0.3f
private const val SWEEP_MILLIS = 1100

/** Tone for a Git porcelain status code in change lists. */
fun gitStatusTone(status: String): IcodeTone = when (status) {
    "A", "?" -> IcodeTone.Success
    "M" -> IcodeTone.Warning
    else -> IcodeTone.Error
}

/** Diagnostic entry used by the Problems panel and inline decorations. */
@Composable
fun DiagnosticRow(
    tone: IcodeTone,
    icon: ImageVector,
    message: String,
    location: String,
    source: String,
    modifier: Modifier = Modifier,
) {
    IcodeCard(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            Icon(
                icon,
                contentDescription = null,
                tint = tone.tint(),
                modifier = Modifier.size(IcodeSizes.IconMd),
            )
            Column(Modifier.weight(1f)) {
                MonoLine(message)
                Row {
                    MonoLine(location, style = IcodeType.MonoSmall)
                    MonoLine("  $source", color = tone.tint(), style = IcodeType.MonoSmall)
                }
            }
        }
    }
}
