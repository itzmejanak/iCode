package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One activity entry rendered on the workspace rail. */
@Immutable
data class ActivityRailEntry(
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val onClick: () -> Unit = {},
)

/** Compact activity rail used by the workspace shell. */
@Composable
fun ActivityRail(entries: List<ActivityRailEntry>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(IcodeSizes.TouchTarget)
            .fillMaxHeight()
            .background(IcodeColors.Surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEach { entry ->
            Box(
                modifier = Modifier.size(IcodeSizes.TouchTarget).clickable(onClick = entry.onClick),
                contentAlignment = Alignment.Center,
            ) {
                if (entry.active) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .height(2.dp)
                            .width(20.dp)
                            .background(IcodeColors.Accent),
                    )
                }
                Icon(
                    entry.icon,
                    contentDescription = entry.label,
                    tint = if (entry.active) IcodeColors.Accent else IcodeColors.TextSecondary,
                    modifier = Modifier.size(IcodeSizes.IconLg),
                )
            }
        }
    }
}
