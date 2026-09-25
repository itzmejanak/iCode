package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One activity entry rendered on the workspace rail. */
@Immutable
data class ActivityRailEntry(
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    /** Count shown over the icon, as VS Code marks pending changes. Zero hides it. */
    val badge: Int = 0,
    /** Keep a primary shortcut accented even while another rail item is selected. */
    val accented: Boolean = false,
    val onClick: () -> Unit = {},
)

/** Compact activity rail used by the workspace shell. */
@Composable
fun ActivityRail(
    entries: List<ActivityRailEntry>,
    modifier: Modifier = Modifier,
    bottomEntries: List<ActivityRailEntry> = emptyList(),
) {
    Column(
        modifier = modifier
            .width(IcodeSizes.TouchTarget)
            .fillMaxHeight()
            .background(IcodeColors.Surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEach { entry -> RailItem(entry) }
        if (bottomEntries.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            bottomEntries.forEach { entry -> RailItem(entry) }
        }
    }
}

@Composable
private fun RailItem(entry: ActivityRailEntry) {
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
            tint = if (entry.active || entry.accented) IcodeColors.Accent else IcodeColors.TextSecondary,
            modifier = Modifier.size(IcodeSizes.IconLg),
        )
        if (entry.badge > 0) RailBadge(entry.badge, Modifier.align(Alignment.Center))
    }
}

/**
 * The pending-change count, sitting on the icon's upper right.
 *
 * Counts above 99 are capped so the pill cannot widen past the rail.
 */
@Composable
private fun RailBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(x = BADGE_OFFSET, y = -BADGE_OFFSET)
            .defaultMinSize(minWidth = BADGE_SIZE, minHeight = BADGE_SIZE)
            .clip(CircleShape)
            .background(IcodeColors.Accent)
            .padding(horizontal = IcodeSpacing.Micro),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > BADGE_MAX) "$BADGE_MAX+" else count.toString(),
            style = IcodeType.Badge.copy(color = IcodeColors.BackgroundDeep),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private val BADGE_SIZE = 16.dp
private val BADGE_OFFSET = 11.dp
private const val BADGE_MAX = 99
