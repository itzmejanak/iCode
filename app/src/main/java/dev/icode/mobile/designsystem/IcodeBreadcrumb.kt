package dev.icode.mobile.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role

/**
 * A path in segments, as a top bar's subtitle (docs/explorer-gaps-plan.md §3.14): "/workspace ›
 * RevPlayer › src". A tap on a segment goes there ([onCrumb], by its index); the last is where it is.
 * A long path scrolls, its end in view.
 */
@Composable
fun IcodeBreadcrumb(labels: List<String>, onCrumb: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(labels, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Row(modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            if (index > 0) Text(" › ", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
            if (index == labels.lastIndex) {
                Text(label, style = IcodeType.Caption)
            } else {
                Text(
                    label,
                    style = IcodeType.Caption.copy(color = IcodeColors.Accent),
                    modifier = Modifier.clip(IcodeShapes.Small).clickable(role = Role.Button) { onCrumb(index) },
                )
            }
        }
    }
}
