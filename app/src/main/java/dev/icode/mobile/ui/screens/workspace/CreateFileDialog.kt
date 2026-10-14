package dev.icode.mobile.ui.screens.workspace

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.rememberPendingRequest

internal enum class WorkspaceItemType { File, Folder }

/** Existing editor entry points use the same creation form in file mode. */
@Composable
internal fun CreateFileDialog(parentLocation: String?, onDismiss: () -> Unit) {
    CreateWorkspaceItemDialog(WorkspaceItemType.File, parentLocation, onDismiss)
}

/**
 * One compact form for files and subfolders — in the open workspace (the Explorer, the editor), or
 * in [workspace], the one the file explorer is in, or phone storage's top. Following the open
 * workspace, it stops when that changes under it. [shownAs] names the folder it makes in, as the
 * file explorer shows it.
 */
@Composable
internal fun CreateWorkspaceItemDialog(
    type: WorkspaceItemType,
    parentLocation: String?,
    onDismiss: () -> Unit,
    workspace: String? = null,
    shownAs: String? = null,
) {
    val gateway = LocalICodeGateway.current
    val state = LocalICodeSnapshot.current
    val followsOpen = workspace == null
    val intended = workspace ?: remember(type, parentLocation) { state.activeWorkspaceLocation }
    val parent = parentLocation ?: intended
    var name by rememberSaveable(type, parent) { mutableStateOf("") }
    val request = rememberPendingRequest(type to parent, onDismiss)
    val validationError = when (type) {
        WorkspaceItemType.File -> WorkspaceNameRules.fileValidationError(name)
        WorkspaceItemType.Folder -> WorkspaceNameRules.workspaceFolderValidationError(name)
    }
    IcodeConfirmDialog(
        title = if (type == WorkspaceItemType.File) "New file" else "New folder",
        confirmLabel = "Create",
        confirmEnabled = validationError == null && parent != null && intended != null &&
            (!followsOpen || state.activeWorkspaceLocation == intended) && !request.waiting,
        dismissEnabled = !request.waiting,
        onDismiss = onDismiss,
        onConfirm = {
            val targetParent = parent ?: return@IcodeConfirmDialog
            val targetWorkspace = intended ?: return@IcodeConfirmDialog
            request.send { requestId ->
                when (type) {
                    WorkspaceItemType.File -> gateway.dispatch(ICodeIntent.CreateFile(name.trim(), targetParent, targetWorkspace, requestId))
                    WorkspaceItemType.Folder -> gateway.dispatch(ICodeIntent.CreateWorkspaceFolder(name.trim(), targetParent, targetWorkspace, requestId))
                }
            }
        },
    ) {
        Text(
            "In ${shownAs ?: if (parent == intended) intended?.substringAfterLast('/') ?: "No workspace" else parent?.removePrefix("$intended/")}",
            style = IcodeType.Body,
            color = IcodeColors.TextSecondary,
        )
        LabeledTextField(
            if (type == WorkspaceItemType.File) "File name" else "Folder name",
            name,
            { name = it; request.failure = null },
            placeholder = if (type == WorkspaceItemType.File) "example.ts" else "components",
        )
        if (validationError != null && name.isNotEmpty()) {
            Text(validationError, style = IcodeType.Caption, color = IcodeColors.Error)
        }
        if (followsOpen && intended != state.activeWorkspaceLocation) {
            Text("The active workspace changed. Close and reopen this form.", style = IcodeType.Caption, color = IcodeColors.Error)
        }
        request.failure?.let { Text(it, style = IcodeType.Caption, color = IcodeColors.Error) }
    }
}
