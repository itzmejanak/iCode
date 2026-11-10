package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

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
    /** What it started runs (docs/loading-plan.md §4.2): the spinner in its icon's place, and no second tap. */
    val busy: Boolean = false,
    /** Its icon's colour where its state gives one (the assistant waiting for you), over the accent's. */
    val tint: Color? = null,
    /** A dot on it while something runs there ([IconMarks]). */
    val dot: Color? = null,
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
        modifier = Modifier.size(IcodeSizes.TouchTarget).clickable(enabled = !entry.busy, onClick = entry.onClick)
            .busySemantics(entry.busy),
        contentAlignment = Alignment.Center,
    ) {
        if (entry.active) UnderLine(IcodeColors.Accent)
        val tint = entry.tint ?: if (entry.active || entry.accented) IcodeColors.Accent else IcodeColors.TextSecondary
        if (rememberShownWhileBusy(entry.busy)) {
            IcodeSpinner(Modifier.semantics { contentDescription = entry.label }, size = IcodeSizes.IconLg, color = tint)
        } else {
            Icon(entry.icon, contentDescription = entry.label, tint = tint, modifier = Modifier.size(IcodeSizes.IconLg))
        }
        IconMarks(entry.badge, entry.dot)
    }
}
