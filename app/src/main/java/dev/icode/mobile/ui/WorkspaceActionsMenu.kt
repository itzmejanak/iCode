package dev.icode.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeOverflowMenu
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkspaceSummary

/** One workspace action set for every surface that displays a recent path. */
@Composable
fun WorkspaceActionsMenu(item: WorkspaceSummary, modifier: Modifier = Modifier) {
    val gateway = LocalWorkbenchGateway.current
    val context = LocalContext.current
    var confirmRemoval by remember(item.location) { mutableStateOf(false) }
    IcodeOverflowMenu(
        contentDescription = "More options for ${item.name}",
        modifier = modifier,
        actions = listOf(
            IcodeMenuAction("Open", Icons.Outlined.FolderOpen, { gateway.dispatch(WorkbenchIntent.OpenWorkspace(item.location)) }),
            IcodeMenuAction("Open Terminal", Icons.Outlined.Terminal, { gateway.dispatch(WorkbenchIntent.OpenWorkspaceTerminal(item.location)) }, enabled = item.available),
            IcodeMenuAction("Locate folder", Icons.Outlined.Folder, { gateway.dispatch(WorkbenchIntent.ShowWorkspaceLocation(item.location)) }, enabled = item.available),
            IcodeMenuAction("Copy path", Icons.Outlined.ContentCopy, {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Workspace path", item.location))
                Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
            }),
            IcodeMenuAction("Remove from recents", Icons.Outlined.RemoveCircleOutline, { confirmRemoval = true }, destructive = true),
        ),
    )
    if (confirmRemoval) {
        IcodeConfirmDialog(
            title = "Remove recent workspace?",
            confirmLabel = "Remove",
            destructive = true,
            onDismiss = { confirmRemoval = false },
            onConfirm = {
                gateway.dispatch(WorkbenchIntent.RemoveRecentWorkspace(item.location))
                confirmRemoval = false
            },
        ) {
            Text(item.name, style = IcodeType.Body)
            MonoLine(item.path, style = IcodeType.MonoSmall)
            Text("Only this recent shortcut is removed. Files in the folder stay untouched.", style = IcodeType.Body)
        }
    }
}
