package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    message: String,
    location: String,
    source: String,
    modifier: Modifier = Modifier,
) {
    IcodeCard(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            Icon(
                Icons.Outlined.WarningAmber,
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
