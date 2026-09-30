package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.domain.AppNotice

/**
 * Once, after the update that moved workspaces into iCode's storage (docs/workspace-storage-plan.md
 * §5): the folders opened from phone storage before, each with its own Import — the Import browser,
 * opened at that folder. Their folders were never changed.
 */
@Composable
fun WorkspacesMovedInNotice(
    notice: AppNotice.WorkspacesMovedIn,
    new: Boolean,
    onImport: (path: String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IcodeCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    if (new) StatusDot(IcodeColors.Accent)
                    Text("Workspaces now live in iCode", style = IcodeType.Body)
                }
                Text(
                    "iCode keeps every workspace in its own storage now. Bring these in with Import; " +
                        "their folders on phone storage were not changed.",
                    style = IcodeType.Caption,
                )
                notice.earlier.forEach { workspace ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(workspace.name, style = IcodeType.Body)
                            Text(workspace.path, style = IcodeType.MonoSmall)
                        }
                        Text(
                            "Import",
                            modifier = Modifier
                                .heightIn(min = IcodeSizes.TouchTarget)
                                .clickable(onClickLabel = "Import ${workspace.name}") { onImport(workspace.path) }
                                .padding(horizontal = IcodeSpacing.Space2)
                                .wrapContentHeight(),
                            style = IcodeType.Label.copy(color = IcodeColors.Accent),
                        )
                    }
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(IcodeSizes.TouchTarget)) {
                Icon(Icons.Outlined.Close, contentDescription = "Delete notification", tint = IcodeColors.TextSecondary)
            }
        }
    }
}
