package dev.icode.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeOverflowMenu
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.domain.WorkspaceSummary

/** One set of actions for a whole workspace, wherever one is listed: the launcher and Storage settings. */
@Composable
fun WorkspaceActionsMenu(item: WorkspaceSummary, modifier: Modifier = Modifier) {
    val gateway = LocalWorkbenchGateway.current
    val context = LocalContext.current
    var renaming by remember(item.location) { mutableStateOf(false) }
    var deleting by remember(item.location) { mutableStateOf(false) }
    IcodeOverflowMenu(
        contentDescription = "More options for ${item.name}",
        modifier = modifier,
        actions = listOf(
            IcodeMenuAction("Open", Icons.Outlined.FolderOpen, { gateway.dispatch(WorkbenchIntent.OpenWorkspace(item.location)) }),
            IcodeMenuAction("Open Terminal", Icons.Outlined.Terminal, { gateway.dispatch(WorkbenchIntent.OpenWorkspaceTerminal(item.location)) }),
            IcodeMenuAction("Rename…", Icons.Outlined.DriveFileRenameOutline, { renaming = true }),
            IcodeMenuAction("Show in Files", Icons.Outlined.Folder, { gateway.dispatch(WorkbenchIntent.ShowWorkspaceInFiles(item.location)) }),
            // The runtime path: what a terminal, a script or another workspace's config names it by.
            IcodeMenuAction("Copy path", Icons.Outlined.ContentCopy, {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Workspace path", item.path))
                Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
            }),
            IcodeMenuAction("Delete…", Icons.Outlined.DeleteOutline, { deleting = true }, destructive = true),
        ),
    )
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

/** A whole workspace's new name; its settings, history and open tabs follow it. */
@Composable
private fun RenameWorkspaceDialog(item: WorkspaceSummary, onDismiss: () -> Unit) {
    val gateway = LocalWorkbenchGateway.current
    val workspaces = LocalWorkbenchSnapshot.current.workspaces
    var name by rememberSaveable(item.location) { mutableStateOf(item.name) }
    val request = rememberPendingRequest(item.location, onDismiss)
    val problem = WorkspaceNameRules.newWorkspaceError(name) { taken -> taken != item.name && workspaces.any { it.name == taken } }
    IcodeConfirmDialog(
        title = "Rename workspace",
        confirmLabel = "Rename",
        confirmEnabled = problem == null && name.trim() != item.name && !request.waiting,
        dismissEnabled = !request.waiting,
        onDismiss = onDismiss,
        onConfirm = { request.send { requestId -> gateway.dispatch(WorkbenchIntent.RenameWorkspace(item.location, name.trim(), requestId)) } },
    ) {
        LabeledTextField("Folder name", name, { name = it; request.failure = null }, literal = true)
        MonoLine(WorkspaceRuntimePath.of(name.trim()), style = IcodeType.MonoSmall)
        (problem?.takeIf { name.isNotEmpty() } ?: request.failure)?.let {
            Text(it, style = IcodeType.Caption, color = IcodeColors.Error)
        }
    }
}
