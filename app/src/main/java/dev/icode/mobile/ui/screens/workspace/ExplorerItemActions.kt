package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.designsystem.gitStatusTone
import dev.icode.mobile.designsystem.tint
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import dev.icode.mobile.ui.rememberPendingRequest

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
    val request = rememberPendingRequest(entry.path, onDismiss)
    val folder = entry.kind == FileKind.Folder
    val validationError = if (folder) WorkspaceNameRules.workspaceFolderValidationError(name) else WorkspaceNameRules.fileValidationError(name)
    IcodeConfirmDialog(
        title = if (folder) "Rename folder" else "Rename file",
        confirmLabel = "Rename",
        confirmEnabled = validationError == null && name.trim() != entry.name && !request.waiting,
        dismissEnabled = !request.waiting,
        onDismiss = onDismiss,
        onConfirm = { request.send { requestId -> gateway.dispatch(WorkbenchIntent.RenameWorkspaceItem(entry.path, name.trim(), requestId)) } },
    ) {
        LabeledTextField(if (folder) "Folder name" else "File name", name, { name = it; request.failure = null })
        if (validationError != null && name.isNotEmpty()) {
            Text(validationError, style = IcodeType.Caption, color = IcodeColors.Error)
        }
        request.failure?.let { Text(it, style = IcodeType.Caption, color = IcodeColors.Error) }
    }
}

/** Delete is final — there is no trash — so it is asked, and says what goes. */
@Composable
internal fun DeleteWorkspaceItemDialog(entry: FileEntry, onDismiss: () -> Unit) {
    val gateway = LocalWorkbenchGateway.current
    val root = LocalWorkbenchSnapshot.current.activeWorkspaceLocation
    val folder = entry.kind == FileKind.Folder
    IcodeConfirmDialog(
        title = "Delete ${entry.name}?",
        message = if (folder) {
            "The folder and everything in it are deleted. This cannot be undone."
        } else {
            "The file is deleted. This cannot be undone."
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

@Composable
internal fun ExplorerRow(entry: FileEntry, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val folder = entry.kind == FileKind.Folder
    val type = FileTypes.of(entry.name)
    val icon = when {
        folder -> if (entry.expanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder
        else -> fileTypeIcon(type)
    }
    val iconTint = when {
        folder -> if (entry.expanded) IcodeColors.Accent else IcodeColors.TextSecondary
        else -> fileTypeTint(type)
    }
    IcodeListRow(
        selected = selected,
        selectionBackground = IcodeColors.Surface,
        rowShape = RectangleShape,
        indent = IcodeSpacing.IndentStep * entry.depth,
        onClick = onClick,
        onLongClick = onLongClick,
        leading = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
            ) {
                if (entry.kind == FileKind.Folder) {
                    Icon(
                        if (entry.expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = IcodeColors.TextSecondary,
                        modifier = Modifier.size(IcodeSizes.IconSm),
                    )
                } else {
                    Spacer(Modifier.width(IcodeSizes.IconSm))
                }
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(IcodeSizes.IconMd))
            }
        },
        // As in VS Code: a changed name takes its status colour; a file shows
        // its status letter, a folder a dot for the strongest change beneath it.
        content = {
            Text(
                entry.name,
                modifier = Modifier.weight(1f),
                style = IcodeType.Body,
                color = when {
                    entry.gitStatus != null -> gitStatusTone(entry.gitStatus).tint()
                    !folder && type == FileTypes.Markdown -> IcodeColors.TextSecondary
                    else -> IcodeColors.TextPrimary
                },
            )
        },
        trailing = {
            val status = entry.gitStatus
            when {
                status == null -> Unit
                entry.kind == FileKind.Folder -> StatusDot(gitStatusTone(status).tint())
                else -> MonoLine(status, color = gitStatusTone(status).tint(), style = IcodeType.MonoSmall)
            }
        },
    )
}
