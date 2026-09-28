package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.RecoverableDraft
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.countOf

/**
 * Edits the last session never saved, kept as drafts. Shown while they exist —
 * it is not an event to delete but work to resolve, in Document Recovery.
 */
@Composable
fun UnsavedWorkNotice(drafts: List<RecoverableDraft>, onReview: () -> Unit, modifier: Modifier = Modifier) {
    IcodeCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = IcodeColors.Amber, modifier = Modifier.size(IcodeSizes.IconMd))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                Text("Unsaved work from the last session", style = IcodeType.Body)
                Text(
                    "${countOf(drafts.size, "file")} kept as drafts · newest ${RelativeTime.since(drafts.maxOf { it.savedAtEpochMillis })}.",
                    style = IcodeType.Caption,
                )
                SecondaryButton("Review", onReview, Modifier.fillMaxWidth(), color = IcodeColors.Accent)
            }
        }
    }
}
