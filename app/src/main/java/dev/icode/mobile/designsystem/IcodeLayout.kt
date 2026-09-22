package dev.icode.mobile.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Technical text line (paths, code, logs, metadata).
 *
 * Defaults to three lines with ellipsis to protect dense layouts; pass
 * [maxLines] when a block must grow.
 */
@Composable
fun MonoLine(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = IcodeColors.TextPrimary,
    style: TextStyle = IcodeType.Mono,
    maxLines: Int = 3,
) {
    Text(
        text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Two equal-width action slots separated by the shared gutter. */
@Composable
fun TwoColumnActions(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}
