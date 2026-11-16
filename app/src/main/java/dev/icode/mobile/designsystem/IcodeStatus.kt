package dev.icode.mobile.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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

/**
 * What an icon in a [IcodeSizes.TouchTarget] box carries — a rail entry, a docked action: [count]
 * on its upper end ([CountBadge]), none at zero; a [dot] on its upper start while something runs.
 */
@Composable
internal fun BoxScope.IconMarks(count: Int, dot: Color?) {
    if (count > 0) CountBadge(count, Modifier.align(Alignment.Center))
    if (dot != null) {
        Box(Modifier.align(Alignment.Center).offset(x = -COUNT_BADGE_OFFSET, y = -COUNT_BADGE_OFFSET)) { StatusDot(dot) }
    }
}

/** The line under an icon that marks it, [lift] above the box's foot: the rail's active entry, the dock's tool on screen. */
@Composable
internal fun BoxScope.UnderLine(color: Color, lift: Dp = 0.dp) {
    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = lift).height(2.dp).width(20.dp).background(color, IcodeShapes.Track))
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

/**
 * A name a thing carries — a branch, a tag — in [color], on one line, tinted; a long one is cut short.
 * [filled] is for the accent's own tag, the one that marks where you are: the accent behind
 * [IcodeColors.OnAccent].
 */
@Composable
fun IcodeTag(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Text(
        text,
        modifier = modifier
            .widthIn(max = TAG_MAX_WIDTH)
            .background(if (filled) color else color.copy(alpha = TAG_TINT), IcodeShapes.Chip)
            .padding(horizontal = IcodeSpacing.Space1, vertical = IcodeSpacing.Micro),
        style = IcodeType.Badge.copy(color = if (filled) IcodeColors.OnAccent else color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val TAG_TINT = 0.16f
/** A long name is cut short, leaving room for what the tag stands beside. */
private val TAG_MAX_WIDTH = 96.dp

/**
 * Horizontal progress track with accent fill by default: [progress] 0..1, or null while the share is
 * unknown, when it sweeps ([IndeterminateProgressTrack]). The one track for every long operation.
 */
@Composable
fun ProgressTrack(progress: Float?, modifier: Modifier = Modifier, color: Color = IcodeColors.Accent) {
    if (progress == null) return IndeterminateProgressTrack(modifier, color)
    Box(modifier.height(5.dp).background(IcodeColors.SurfaceTrack, IcodeShapes.Track)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).background(color, IcodeShapes.Track))
    }
}

/**
 * A track filled by [shares] of it side by side, each a fraction in its colour, the rest left empty:
 * what a storage holds, kind by kind.
 */
@Composable
fun ShareTrack(shares: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    Row(modifier.height(IcodeSpacing.Space2).clip(IcodeShapes.Track).background(IcodeColors.SurfaceTrack)) {
        shares.filter { (share, _) -> share > 0f }.forEach { (share, color) -> Box(Modifier.weight(share).fillMaxHeight().background(color)) }
        val rest = 1f - shares.sumOf { (share, _) -> share.coerceAtLeast(0f).toDouble() }.toFloat()
        if (rest > 0f) Spacer(Modifier.weight(rest))
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

/**
 * The ring a long setup step fills (docs/design-and-navigation.md), the tracks' colours on a
 * circle: [progress] 0..1 as the step measures it, or null while its share is unknown, the arc then
 * sweeping. [content] sits at its centre. A new step should start a new ring (`key`), so the arc
 * never runs back.
 */
@Composable
fun ProgressRing(progress: Float?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.size(IcodeSizes.ProgressRing), contentAlignment = Alignment.Center) {
        if (progress == null) {
            CircularProgressIndicator(
                Modifier.fillMaxSize(),
                color = IcodeColors.Accent,
                strokeWidth = IcodeSizes.RingStroke,
                trackColor = IcodeColors.SurfaceTrack,
                strokeCap = StrokeCap.Round,
            )
        } else {
            val shown by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ring")
            CircularProgressIndicator(
                progress = { shown },
                modifier = Modifier.fillMaxSize(),
                color = IcodeColors.Accent,
                strokeWidth = IcodeSizes.RingStroke,
                trackColor = IcodeColors.SurfaceTrack,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
        }
        content()
    }
}

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
