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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

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
    textAlign: TextAlign? = null,
) {
    Text(
        text,
        modifier = modifier,
        style = style.scaledBy(LocalCodeTextScale.current).copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

/** Three equal-width action slots, [IcodeSpacing.ItemGap] apart, for a choice between mutually exclusive options. */
@Composable
fun ThreeColumnActions(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    third: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap)) {
        Box(Modifier.weight(1f)) { first() }
        Box(Modifier.weight(1f)) { second() }
        Box(Modifier.weight(1f)) { third() }
    }
}

/** Two equal-width action slots, [IcodeSpacing.ItemGap] apart as cards are. */
@Composable
fun TwoColumnActions(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap)) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}
