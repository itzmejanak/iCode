package dev.icode.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeMoreButton
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeSpinner
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.rememberShownWhileBusy
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.WorkspaceSummary
import dev.icode.mobile.domain.isBusy

/**
 * One workspace in the home's Recent list, as VS Code's Welcome page lists folders (docs/home-plan.md
 * §1): its folder — open, in the accent, for the workspace open — its name as a link, and under it
 * its branch, drawn as Source Control draws one, and when it was last opened. Its path is
 * `/workspace/<name>`, its name again, so it is not shown. Its actions come from ⋮ or a long press —
 * [onActions] asks the screen to show the workspace's sheet ([WorkspaceSheet]). A tap opens it.
 */
@Composable
fun WorkspaceRow(item: WorkspaceSummary, onActions: () -> Unit, modifier: Modifier = Modifier) {
    val gateway = LocalICodeGateway.current
    val snapshot = LocalICodeSnapshot.current
    // Opening from the tap (docs/loading-plan.md §5): the row says so in place of its ⋮.
    val opening = rememberShownWhileBusy(snapshot.isBusy(BusyAction.OpenWorkspace, item.location))
    val open = item.location == snapshot.activeWorkspaceLocation
    IcodeListRow(
        modifier = modifier,
        onClick = { gateway.dispatch(ICodeIntent.OpenWorkspace(item.location)) },
        onLongClick = onActions,
        leading = {
            Icon(
                if (open) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
                contentDescription = null,
                tint = if (open) IcodeColors.Accent else IcodeColors.TextSecondary,
                modifier = Modifier.size(IcodeSizes.IconMd),
            )
        },
        content = {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = IcodeType.Body.copy(color = IcodeColors.Accent), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                    item.branch?.let { branch ->
                        Icon(Icons.AutoMirrored.Outlined.CallSplit, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
                        Text(branch, style = IcodeType.MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text("·", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
                    }
                    Text(RelativeTime.since(item.lastOpenedAt), style = IcodeType.Caption, maxLines = 1)
                }
            }
        },
        trailing = {
            if (opening) {
                Box(Modifier.size(IcodeSizes.TouchTarget), contentAlignment = Alignment.Center) {
                    IcodeSpinner(Modifier.semantics { contentDescription = "Opening ${item.name}" })
                }
            } else {
                IcodeMoreButton("More options for ${item.name}", onClick = onActions)
            }
        },
    )
}
