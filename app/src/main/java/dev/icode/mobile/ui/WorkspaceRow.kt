package dev.icode.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeSpinner
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.busySemantics
import dev.icode.mobile.designsystem.rememberShownWhileBusy
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.WorkspaceSummary
import dev.icode.mobile.domain.isBusy

/**
 * One workspace in the home's Recent list, drawn as the Workspaces page draws it
 * (docs/design-and-navigation.md, owner's choice A, 2026-11-15): its folder's icon from the file icon
 * theme, open for the
 * workspace open, whose name is in the accent; under the name its branch, drawn as Source Control
 * draws one; when it was last opened at the end. A tap opens it; a long press asks the screen for its
 * sheet ([onActions], [WorkspaceSheet]), as on the Workspaces page.
 */
@Composable
fun WorkspaceRow(item: WorkspaceSummary, onActions: () -> Unit, modifier: Modifier = Modifier) {
    val gateway = LocalICodeGateway.current
    val snapshot = LocalICodeSnapshot.current
    val opening = snapshot.isBusy(BusyAction.OpenWorkspace, item.location)
    val open = item.location == snapshot.activeWorkspaceLocation
    IcodeListRow(
        modifier = modifier.busySemantics(opening),
        onClick = { gateway.dispatch(ICodeIntent.OpenWorkspace(item.location)) },
        onLongClick = onActions,
        leading = {
            // Opening from the tap (docs/design-and-navigation.md): the spinner in place of the icon, as on the Workspaces page.
            if (rememberShownWhileBusy(opening)) {
                Box(Modifier.size(IcodeSizes.IconXl), contentAlignment = Alignment.Center) { IcodeSpinner() }
            } else {
                FileTypeIcon(item.name, folder = true, size = IcodeSizes.IconXl, open = open, parent = parentName(item.location))
            }
        },
        content = {
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = IcodeType.Body.copy(color = if (open) IcodeColors.Accent else IcodeColors.TextPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.branch?.let { branch ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                        Icon(Icons.AutoMirrored.Outlined.CallSplit, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
                        Text(branch, style = IcodeType.MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        trailing = { Text(RelativeTime.since(item.lastOpenedAt), style = IcodeType.Caption, maxLines = 1) },
    )
}
