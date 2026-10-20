package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
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
import androidx.compose.ui.platform.LocalContext
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
import dev.icode.mobile.domain.ItemClipboard
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.ui.FileItemActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.rememberPendingRequest

/**
 * One Explorer row's actions, every explorer's ([FileItemActions]), from its ⋮ and a long press
 * alike. Open is a file into the editor, a folder opened or closed in the tree; Paste goes into a
 * folder row itself, or beside a file row into the folder it sits in ([creationParentLocation]).
 */
@Composable
internal fun explorerItemActions(
    entry: FileEntry,
    root: String?,
    rootPath: String,
    files: List<FileEntry>,
    clipboard: ItemClipboard?,
    onRename: () -> Unit,
    onDelete: () -> Unit,
): List<IcodeMenuAction> {
    val gateway = LocalICodeGateway.current
    val folder = entry.kind == FileKind.Folder
    return FileItemActions.of(
        context = LocalContext.current,
        dispatch = gateway::dispatch,
        location = entry.path,
        runtimePath = root?.let { WorkspaceRuntimePath.within(it, rootPath, entry.path) } ?: entry.path,
        clipboard = clipboard,
        open = if (folder) {
            IcodeMenuAction(
                if (entry.expanded) "Collapse" else "Expand",
                if (entry.expanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                { gateway.dispatch(ICodeIntent.ToggleFolder(entry.path)) },
            )
        } else {
            FileItemActions.openFile { gateway.dispatch(ICodeIntent.OpenFile(entry.path)) }
        },
        onRename = onRename,
        onPaste = { creationParentLocation(root, entry.path, files)?.let { gateway.dispatch(ICodeIntent.PasteInto(it)) } },
        onDelete = onDelete,
        file = !folder,
    )
}

/**
 * The create form, for a new name: the same rules, the same answer by request id — for an item of
 * the Explorer's tree or of the workspaces' file explorer alike.
 */
@Composable
internal fun RenameWorkspaceItemDialog(location: String, currentName: String, folder: Boolean, onDismiss: () -> Unit) {
    val gateway = LocalICodeGateway.current
    var name by rememberSaveable(location) { mutableStateOf(currentName) }
    val request = rememberPendingRequest(location, onDismiss)
    val validationError = if (folder) WorkspaceNameRules.workspaceFolderValidationError(name) else WorkspaceNameRules.fileValidationError(name)
    IcodeConfirmDialog(
        title = if (folder) "Rename folder" else "Rename file",
        confirmLabel = "Rename",
        confirmEnabled = validationError == null && name.trim() != currentName && !request.waiting,
        dismissEnabled = !request.waiting,
        onDismiss = onDismiss,
        onConfirm = { request.send { requestId -> gateway.dispatch(ICodeIntent.RenameWorkspaceItem(location, name.trim(), requestId)) } },
    ) {
        LabeledTextField(if (folder) "Folder name" else "File name", name, { name = it; request.failure = null })
        if (validationError != null && name.isNotEmpty()) {
            Text(validationError, style = IcodeType.Caption, color = IcodeColors.Error)
        }
        request.failure?.let { Text(it, style = IcodeType.Caption, color = IcodeColors.Error) }
    }
}

/** Delete is final — there is no trash — so it is asked, and says what goes: [detail], its path as shown. */
@Composable
internal fun DeleteWorkspaceItemDialog(location: String, name: String, folder: Boolean, detail: String, onDismiss: () -> Unit) {
    val gateway = LocalICodeGateway.current
    IcodeConfirmDialog(
        title = "Delete $name?",
        message = if (folder) {
            "The folder and everything in it are deleted. This cannot be undone."
        } else {
            "The file is deleted. This cannot be undone."
        },
        confirmLabel = "Delete",
        onConfirm = {
            gateway.dispatch(ICodeIntent.DeleteWorkspaceItem(location))
            onDismiss()
        },
        onDismiss = onDismiss,
        detail = detail,
    )
}

/** One row of the Explorer's tree; its actions open from a long press ([menuOpen]), at its end. */
@Composable
internal fun ExplorerRow(
    entry: FileEntry,
    selected: Boolean,
    actions: List<IcodeMenuAction>,
    menuOpen: Boolean,
    onMenuDismiss: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
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
            // A long press opens the menu here, as in every explorer (docs/storage-explorer-plan.md §3.2).
            Box { IcodeActionMenu(expanded = menuOpen, onDismiss = onMenuDismiss, actions = actions) }
        },
    )
}
