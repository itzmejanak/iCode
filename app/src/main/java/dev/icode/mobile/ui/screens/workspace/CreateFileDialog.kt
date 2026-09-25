package dev.icode.mobile.ui.screens.workspace

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import java.util.UUID
import kotlinx.coroutines.flow.collect

internal enum class WorkspaceItemType { File, Folder }

/** Existing editor entry points use the same creation form in file mode. */
@Composable
internal fun CreateFileDialog(parentLocation: String?, onDismiss: () -> Unit) {
    CreateWorkspaceItemDialog(WorkspaceItemType.File, parentLocation, onDismiss)
}

/** One compact form for files and subfolders inside the active workspace. */
@Composable
internal fun CreateWorkspaceItemDialog(type: WorkspaceItemType, parentLocation: String?, onDismiss: () -> Unit) {
    val gateway = LocalWorkbenchGateway.current
    val state = LocalWorkbenchSnapshot.current
    val workspace = remember(type, parentLocation) { state.activeWorkspaceLocation }
    val parent = parentLocation ?: workspace
    var name by rememberSaveable(type, parent) { mutableStateOf("") }
    val pendingRequestId = remember { mutableStateOf<String?>(null) }
    var creationError by remember(type, parent) { mutableStateOf<String?>(null) }
    val validationError = when (type) {
        WorkspaceItemType.File -> WorkspaceNameRules.fileValidationError(name)
        WorkspaceItemType.Folder -> WorkspaceNameRules.workspaceFolderValidationError(name)
    }
    LaunchedEffect(gateway) {
        gateway.effects.collect { effect ->
            val requestId = pendingRequestId.value
            when {
                effect is WorkbenchEffect.WorkspaceItemCreated && effect.requestId == requestId -> onDismiss()
                effect is WorkbenchEffect.WorkspaceItemCreationFailed && effect.requestId == requestId -> {
                    creationError = effect.message
                    pendingRequestId.value = null
                }
            }
        }
    }
    IcodeConfirmDialog(
        title = if (type == WorkspaceItemType.File) "New file" else "New folder",
        confirmLabel = "Create",
        confirmEnabled = validationError == null && parent != null && workspace != null &&
            state.activeWorkspaceLocation == workspace && pendingRequestId.value == null,
        dismissEnabled = pendingRequestId.value == null,
        onDismiss = onDismiss,
        onConfirm = {
            val targetParent = parent ?: return@IcodeConfirmDialog
            val targetWorkspace = workspace ?: return@IcodeConfirmDialog
            val requestId = UUID.randomUUID().toString()
            pendingRequestId.value = requestId
            creationError = null
            when (type) {
                WorkspaceItemType.File -> gateway.dispatch(WorkbenchIntent.CreateFile(name.trim(), targetParent, targetWorkspace, requestId))
                WorkspaceItemType.Folder -> gateway.dispatch(WorkbenchIntent.CreateWorkspaceFolder(name.trim(), targetParent, targetWorkspace, requestId))
            }
        },
    ) {
        Text(
            "In ${if (parent == workspace) state.activeWorkspace else parent?.removePrefix("$workspace/") ?: "No workspace"}",
            style = IcodeType.Body,
            color = IcodeColors.TextSecondary,
        )
        LabeledTextField(
            if (type == WorkspaceItemType.File) "File name" else "Folder name",
            name,
            { name = it; creationError = null },
            placeholder = if (type == WorkspaceItemType.File) "example.ts" else "components",
        )
        if (validationError != null && name.isNotEmpty()) {
            Text(validationError, style = IcodeType.Caption, color = IcodeColors.Error)
        }
        if (workspace != state.activeWorkspaceLocation) {
            Text("The active workspace changed. Close and reopen this form.", style = IcodeType.Caption, color = IcodeColors.Error)
        }
        if (creationError != null) Text(creationError.orEmpty(), style = IcodeType.Caption, color = IcodeColors.Error)
    }
}
