package dev.icode.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeOverflowMenu
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkspaceSummary

/**
 * One set of actions for a whole workspace, wherever one is listed, shown by its ⋮ and by a long
 * press on it alike (docs/workspaces-page-plan.md §2.6). Cut and Copy put it on iCode's one
 * clipboard. [onShowInList] is the way from the home screen's recent rows to the Workspaces page;
 * the page itself has none.
 */
@Composable
fun WorkspaceActionsMenu(
    item: WorkspaceSummary,
    longPressed: Boolean,
    onLongPressDismiss: () -> Unit,
    onShowInList: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val gateway = LocalWorkbenchGateway.current
    val context = LocalContext.current
    var renaming by remember(item.location) { mutableStateOf(false) }
    var deleting by remember(item.location) { mutableStateOf(false) }
    val actions = listOfNotNull(
        IcodeMenuAction("Open", Icons.Outlined.FolderOpen, { gateway.dispatch(WorkbenchIntent.OpenWorkspace(item.location)) }),
        IcodeMenuAction("Open Terminal", Icons.Outlined.Terminal, { gateway.dispatch(WorkbenchIntent.OpenWorkspaceTerminal(item.location)) }),
        IcodeMenuAction("Rename…", Icons.Outlined.DriveFileRenameOutline, { renaming = true }),
        IcodeMenuAction("Cut", Icons.Outlined.ContentCut, { gateway.dispatch(WorkbenchIntent.CutItem(item.location)) }),
        IcodeMenuAction("Copy", Icons.Outlined.ContentCopy, { gateway.dispatch(WorkbenchIntent.CopyItem(item.location)) }),
        onShowInList?.let { IcodeMenuAction("Show in Workspaces", Icons.Outlined.FolderCopy, it) },
        // The runtime path: what a terminal, a script or another workspace's config names it by.
        IcodeMenuAction("Copy path", Icons.Outlined.Link, {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Workspace path", item.path))
            Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
        }),
        IcodeMenuAction("Delete…", Icons.Outlined.DeleteOutline, { deleting = true }, destructive = true),
    )
    Box(modifier) {
        IcodeOverflowMenu(contentDescription = "More options for ${item.name}", actions = actions)
        IcodeActionMenu(expanded = longPressed, onDismiss = onLongPressDismiss, actions = actions)
    }
    if (renaming) RenameWorkspaceDialog(item) { renaming = false }
    if (deleting) {
        IcodeConfirmDialog(
            title = "Delete ${item.name}?",
            message = "The workspace and everything in it are deleted from iCode's storage. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                gateway.dispatch(WorkbenchIntent.DeleteWorkspace(item.location))
                deleting = false
            },
            onDismiss = { deleting = false },
            detail = item.path,
        )
    }
}
