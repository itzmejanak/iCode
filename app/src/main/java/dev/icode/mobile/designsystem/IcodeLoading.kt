package dev.icode.mobile.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay

// iCode's one loading vocabulary (docs/design-and-navigation.md). Every wait is one of three:
// - an action the user started: its own control says it is working — a button's spinner (busy),
//   a row's spinner — from the tap, and refuses a second tap;
// - content not read yet: its place drawn as it will be (IcodeSkeletonRows), never an empty state
//   that reads as "there is none";
// - a wait with words: IcodeLoadingLine, the spinner beside what is happening ("Asking pylsp…").
// Long work with a measure keeps ProgressTrack, ProgressRing and IcodeProgressSheet.

/** A value not read yet, in its row's place — a version, a size — never a default that reads as the answer. */
const val CHECKING = "Checking…"

/**
 * How long a wait lasts before it is drawn. Most of iCode's actions end within it, and a spinner that
 * flashes for a frame reads as a glitch; the control is disabled from the tap all the same.
 */
const val BUSY_SHOW_DELAY_MILLIS = 150L

/** True once [busy] has lasted [BUSY_SHOW_DELAY_MILLIS]; false again the moment it ends. */
@Composable
fun rememberShownWhileBusy(busy: Boolean): Boolean {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (!busy) {
            shown = false
            return@LaunchedEffect
        }
        delay(BUSY_SHOW_DELAY_MILLIS)
        shown = true
    }
    return busy && shown
}

/**
 * The one spinner: icon-sized beside a label or a row by default; [size] null fills what it is in (the
 * phone's bubble). Its meaning is said by what holds it.
 */
@Composable
fun IcodeSpinner(
    modifier: Modifier = Modifier,
    size: Dp? = IcodeSizes.IconSm,
    color: Color = IcodeColors.Accent,
    strokeWidth: Dp = IcodeSpacing.Micro,
) {
    CircularProgressIndicator(
        modifier = if (size == null) modifier else modifier.size(size),
        color = color,
        strokeWidth = strokeWidth,
        trackColor = Color.Transparent,
        strokeCap = StrokeCap.Round,
    )
}

/** What a control is said to be while [busy]: working, not merely unavailable. */
fun Modifier.busySemantics(busy: Boolean): Modifier = if (busy) semantics { stateDescription = WORKING } else this

private const val WORKING = "Working"

/** A row's working state, in its trailing place: the spinner once the wait is worth seeing, else nothing. */
@Composable
fun BusyIndicator(busy: Boolean, modifier: Modifier = Modifier, description: String = WORKING) {
    if (rememberShownWhileBusy(busy)) IcodeSpinner(modifier.semantics { contentDescription = description })
}

/**
 * A wait with words: what is happening, after the spinner, said to screen readers as it changes. The
 * one form for "Loading settings…", "Asking the server…", "Reading the history…".
 */
@Composable
fun IcodeLoadingLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        IcodeSpinner()
        Text(text, style = IcodeType.Caption, color = IcodeColors.TextSecondary)
    }
}

/**
 * Content not read yet, drawn as the [count] rows it will be — a badge and two lines each — pulsing
 * quietly, so the page keeps its shape and nothing jumps when they arrive. Said once as [description].
 */
@Composable
fun IcodeSkeletonRows(count: Int, modifier: Modifier = Modifier, description: String = "Loading") {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = SKELETON_LOW,
        targetValue = SKELETON_HIGH,
        animationSpec = infiniteRepeatable(tween(SKELETON_MILLIS), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap),
    ) {
        repeat(count) { index ->
            Row(
                Modifier.fillMaxWidth().alpha(alpha).background(IcodeColors.Surface, IcodeShapes.Small).padding(IcodeSpacing.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
            ) {
                Box(Modifier.size(IcodeSizes.BadgeSm).background(IcodeColors.SurfaceTrack, IcodeShapes.Small))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                    // Each row a little different, as names are.
                    SkeletonLine(fraction = TITLE_WIDTHS[index % TITLE_WIDTHS.size])
                    SkeletonLine(fraction = DETAIL_WIDTH, height = IcodeSpacing.Space2)
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(fraction: Float, height: Dp = IcodeSpacing.Space3) {
    Box(Modifier.fillMaxWidth(fraction).height(height).background(IcodeColors.SurfaceTrack, IcodeShapes.Track))
}

private const val SKELETON_LOW = 0.45f
private const val SKELETON_HIGH = 0.9f
private const val SKELETON_MILLIS = 900
private val TITLE_WIDTHS = listOf(0.55f, 0.4f, 0.65f)
private const val DETAIL_WIDTH = 0.3f
