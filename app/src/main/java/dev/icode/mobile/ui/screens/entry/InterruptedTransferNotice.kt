package dev.icode.mobile.ui.screens.entry

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.GitTransferLabel

/** A clone or sync that iCode's previous process never finished, instead of silence. */
@Composable
fun InterruptedTransferNotice(
    notice: AppNotice.GitTransferInterrupted,
    new: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val flags = DateUtils.FORMAT_SHOW_TIME or if (DateUtils.isToday(notice.at)) 0 else DateUtils.FORMAT_SHOW_DATE
    IcodeCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
            Icon(Icons.Outlined.SyncProblem, contentDescription = null, tint = IcodeColors.Amber, modifier = Modifier.size(IcodeSizes.IconMd))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    if (new) StatusDot(IcodeColors.Accent)
                    Text("${transferName(notice.label)} did not finish", style = IcodeType.Body)
                }
                Text(
                    if (notice.label == GitTransferLabel.CLONING) {
                        "iCode's process ended while Git was cloning. Nothing of it was kept: clone again."
                    } else {
                        "iCode's process ended while Git was working. Run it again; Git carries on from what it has."
                    },
                    style = IcodeType.Caption,
                )
                Text(notice.target, style = IcodeType.MonoSmall)
                Text("Started " + DateUtils.formatDateTime(context, notice.at, flags), style = IcodeType.Micro)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(IcodeSizes.TouchTarget)) {
                Icon(Icons.Outlined.Close, contentDescription = "Delete notification", tint = IcodeColors.TextSecondary)
            }
        }
    }
}

private fun transferName(label: String): String = when (label) {
    GitTransferLabel.CLONING -> "A clone"
    GitTransferLabel.FETCHING -> "A fetch"
    GitTransferLabel.PULLING -> "A pull"
    GitTransferLabel.PUSHING -> "A push"
    else -> label
}
