package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeOverflowMenu
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.domain.BrowsedItem
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.copyPathAction
import dev.icode.mobile.ui.screens.workspace.fileTypeIcon
import dev.icode.mobile.ui.screens.workspace.fileTypeTint

/**
 * One folder or file inside a workspace, in the file explorer: its icon, its name, its size or
 * count and when it changed. A tap goes into a folder or opens a file in the editor; a long press
 * or ⋮ gives its actions — one list: Open, Rename…, Cut, Copy, Copy path, Delete….
 */
@Composable
internal fun BrowsedItemRow(
    item: BrowsedItem,
    runtimePath: String,
    onBrowse: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val gateway = LocalICodeGateway.current
    val context = LocalContext.current
    var longPressed by remember(item.location) { mutableStateOf(false) }
    val type = FileTypes.of(item.name)
    val open = { if (item.folder) onBrowse(item.location) else gateway.dispatch(ICodeIntent.OpenFile(item.location)) }
    val actions = listOf(
        IcodeMenuAction("Open", if (item.folder) Icons.Outlined.FolderOpen else Icons.Outlined.Description, open),
        IcodeMenuAction("Rename…", Icons.Outlined.DriveFileRenameOutline, onRename),
        IcodeMenuAction("Cut", Icons.Outlined.ContentCut, { gateway.dispatch(ICodeIntent.CutItem(item.location)) }),
        IcodeMenuAction("Copy", Icons.Outlined.ContentCopy, { gateway.dispatch(ICodeIntent.CopyItem(item.location)) }),
        copyPathAction(context, runtimePath),
        IcodeMenuAction("Delete…", Icons.Outlined.DeleteOutline, onDelete, destructive = true),
    )
    IcodeListRow(
        card = true,
        cardPadding = PaddingValues(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space2),
        onClick = open,
        onLongClick = { longPressed = true },
        leading = {
            IconBadge(
                icon = if (item.folder) Icons.Outlined.Folder else fileTypeIcon(type),
                tint = if (item.folder) IcodeColors.Accent else fileTypeTint(type),
                size = IcodeSizes.BadgeMd,
                iconSize = IcodeSizes.IconMd,
            )
        },
        content = {
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MonoLine(detailOf(item), style = IcodeType.MonoSmall, maxLines = 1)
            }
        },
        trailing = {
            Box {
                IcodeOverflowMenu(contentDescription = "More options for ${item.name}", actions = actions)
                IcodeActionMenu(expanded = longPressed, onDismiss = { longPressed = false }, actions = actions)
            }
        },
    )
}

/** "4 items · 5m ago", "12.4 KB · 2h ago". */
private fun detailOf(item: BrowsedItem): String {
    val what = if (item.folder) countOf(item.entries ?: 0, "item") else ByteSize.format(item.bytes ?: 0)
    return "$what · ${RelativeTime.since(item.changedAt)}"
}
