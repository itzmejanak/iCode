package dev.icode.mobile.ui.screens.workspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import java.util.UUID

/** An item cut or copied in the Explorer, waiting for Paste (as VS Code: Move is Cut, then Paste). */
internal data class ExplorerClipboard(val path: String, val cut: Boolean)

/** The long-press menu of one Explorer row. */
@Composable
internal fun ExplorerItemMenu(
    entry: FileEntry,
    expanded: Boolean,
    clipboard: ExplorerClipboard?,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onClipboard: (ExplorerClipboard) -> Unit,
    onPaste: () -> Unit,
    onDelete: () -> Unit,
) {
    IcodeActionMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        actions = listOf(
            IcodeMenuAction("Rename", Icons.Outlined.DriveFileRenameOutline, onRename),
            IcodeMenuAction("Cut", Icons.Outlined.ContentCut, { onClipboard(ExplorerClipboard(entry.path, cut = true)) }),
            IcodeMenuAction("Copy", Icons.Outlined.ContentCopy, { onClipboard(ExplorerClipboard(entry.path, cut = false)) }),
            IcodeMenuAction("Paste", Icons.Outlined.ContentPaste, onPaste, enabled = clipboard != null),
            IcodeMenuAction("Delete", Icons.Outlined.DeleteOutline, onDelete, destructive = true),
        ),
    )
}

/** Paste goes into a folder row itself, or into the folder a file row sits in. */
internal fun pasteTarget(root: String?, entry: FileEntry, files: List<FileEntry>): String? =
    creationParentLocation(root, entry.path, files)

/** Sends the Paste the clipboard asks for; a cut item moves once, a copied one can be pasted again. */
internal fun paste(clipboard: ExplorerClipboard, target: String, send: (WorkbenchIntent) -> Unit): ExplorerClipboard? {
    return if (clipboard.cut) {
        send(WorkbenchIntent.MoveWorkspaceItem(clipboard.path, target))
        null
    } else {
        send(WorkbenchIntent.CopyWorkspaceItem(clipboard.path, target))
        clipboard
    }
}

/** The create form, for a new name: the same rules, the same answer by request id. */
@Composable
internal fun RenameWorkspaceItemDialog(entry: FileEntry, onDismiss: () -> Unit) {
    val gateway = LocalWorkbenchGateway.current
    var name by rememberSaveable(entry.path) { mutableStateOf(entry.name) }
    val pendingRequestId = remember { mutableStateOf<String?>(null) }
    var failure by remember(entry.path) { mutableStateOf<String?>(null) }
    val folder = entry.kind == FileKind.Folder
    val validationError = if (folder) WorkspaceNameRules.workspaceFolderValidationError(name) else WorkspaceNameRules.fileValidationError(name)
    LaunchedEffect(gateway) {
        gateway.effects.collect { effect ->
            val requestId = pendingRequestId.value
            when {
                effect is WorkbenchEffect.WorkspaceItemChanged && effect.requestId == requestId -> onDismiss()
                effect is WorkbenchEffect.WorkspaceItemFailed && effect.requestId == requestId -> {
                    failure = effect.message
                    pendingRequestId.value = null
                }
            }
        }
    }
    IcodeConfirmDialog(
        title = if (folder) "Rename folder" else "Rename file",
        confirmLabel = "Rename",
        confirmEnabled = validationError == null && name.trim() != entry.name && pendingRequestId.value == null,
        dismissEnabled = pendingRequestId.value == null,
        onDismiss = onDismiss,
        onConfirm = {
            val requestId = UUID.randomUUID().toString()
            pendingRequestId.value = requestId
            failure = null
            gateway.dispatch(WorkbenchIntent.RenameWorkspaceItem(entry.path, name.trim(), requestId))
        },
    ) {
        LabeledTextField(if (folder) "Folder name" else "File name", name, { name = it; failure = null })
        if (validationError != null && name.isNotEmpty()) {
            Text(validationError, style = IcodeType.Caption, color = IcodeColors.Error)
        }
        failure?.let { Text(it, style = IcodeType.Caption, color = IcodeColors.Error) }
    }
}

/** Delete is final on phone storage, so it is asked, and says what goes. */
@Composable
internal fun DeleteWorkspaceItemDialog(entry: FileEntry, onDismiss: () -> Unit) {
    val gateway = LocalWorkbenchGateway.current
    val root = LocalWorkbenchSnapshot.current.activeWorkspaceLocation
    val folder = entry.kind == FileKind.Folder
    IcodeConfirmDialog(
        title = "Delete ${entry.name}?",
        message = if (folder) {
            "The folder and everything in it are deleted from the phone's storage. This cannot be undone."
        } else {
            "The file is deleted from the phone's storage. This cannot be undone."
        },
        confirmLabel = "Delete",
        onConfirm = {
            gateway.dispatch(WorkbenchIntent.DeleteWorkspaceItem(entry.path))
            onDismiss()
        },
        onDismiss = onDismiss,
        detail = root?.let { entry.path.removePrefix("$it/") } ?: entry.path,
    )
}
