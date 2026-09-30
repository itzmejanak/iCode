package dev.icode.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkspaceSummary

/**
 * One workspace in a list — the home screen's recent ones and the Workspaces page alike: its name,
 * `/workspace/` path, branch, [size] where the list measures it, when it was last opened, and its
 * menu. A tap opens it.
 */
@Composable
fun WorkspaceRow(item: WorkspaceSummary, modifier: Modifier = Modifier, size: String? = null) {
    val gateway = LocalWorkbenchGateway.current
    IcodeListRow(
        modifier = modifier,
        card = true,
        cardPadding = PaddingValues(
            horizontal = IcodeSpacing.Space3,
            vertical = (IcodeSizes.Tile - IcodeSizes.TouchTarget) / 2,
        ),
        onClick = { gateway.dispatch(WorkbenchIntent.OpenWorkspace(item.location)) },
        leading = {
            IconBadge(
                icon = Icons.Outlined.Folder,
                tint = IcodeColors.Accent,
                size = IcodeSizes.BadgeMd,
                iconSize = IcodeSizes.IconMd,
            )
        },
        content = {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                ) {
                    MonoLine(item.path, modifier = Modifier.weight(1f), style = IcodeType.MonoSmall)
                    if (size != null) Text(size, style = IcodeType.MonoMicro)
                    if (item.branch != null) {
                        Text(item.branch, style = IcodeType.MonoMicro.copy(color = IcodeColors.Accent))
                    }
                }
            }
        },
        trailing = {
            Box {
                WorkspaceActionsMenu(item)
                Text(
                    RelativeTime.since(item.lastOpenedAt),
                    style = IcodeType.Caption,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = IcodeSpacing.Space1),
                )
            }
        },
    )
}
