package dev.icode.mobile.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.domain.WorkspaceSummary

/** New Folder on the Workspaces page, as a file manager asks for one: the new workspace appears in the list. */
@Composable
internal fun NewWorkspaceDialog(onDismiss: () -> Unit) = WorkspaceNameDialog(
    title = "New folder",
    confirmLabel = "Create",
    key = "new",
    initial = "",
    current = null,
    onDismiss = onDismiss,
) { name, requestId -> ICodeIntent.CreateWorkspace(name, requestId) }

/** A whole workspace's new name; its settings, history and open tabs follow it. */
@Composable
internal fun RenameWorkspaceDialog(item: WorkspaceSummary, onDismiss: () -> Unit) = WorkspaceNameDialog(
    title = "Rename workspace",
    confirmLabel = "Rename",
    key = item.location,
    initial = item.name,
    current = item.name,
    onDismiss = onDismiss,
) { name, requestId -> ICodeIntent.RenameWorkspaceItem(item.location, name, requestId) }

/**
 * A workspace's name, as New Folder and Rename ask for it: the name, the `/workspace/<name>` it will
 * have, and whether it can be used, as it is typed; the backend's answer comes by request id and a
 * refusal is said here. [current] is the name it has now, which is not "taken" by itself.
 */
@Composable
private fun WorkspaceNameDialog(
    title: String,
    confirmLabel: String,
    key: Any,
    initial: String,
    current: String?,
    onDismiss: () -> Unit,
    intent: (name: String, requestId: String) -> ICodeIntent,
) {
    val gateway = LocalICodeGateway.current
    val workspaces = LocalICodeSnapshot.current.workspaces
    var name by rememberSaveable(key) { mutableStateOf(initial) }
    val request = rememberPendingRequest(key, onDismiss)
    val problem = WorkspaceNameRules.newWorkspaceError(name) { taken -> taken != current && workspaces.any { it.name == taken } }
    IcodeConfirmDialog(
        title = title,
        confirmLabel = confirmLabel,
        confirmEnabled = problem == null && name.trim() != current && !request.waiting,
        dismissEnabled = !request.waiting,
        onDismiss = onDismiss,
        onConfirm = { request.send { requestId -> gateway.dispatch(intent(name.trim(), requestId)) } },
    ) {
        LabeledTextField("Folder name", name, { name = it; request.failure = null }, literal = true)
        MonoLine(WorkspaceRuntimePath.of(name.trim()), style = IcodeType.MonoSmall)
        (problem?.takeIf { name.isNotEmpty() } ?: request.failure)?.let {
            Text(it, style = IcodeType.Caption, color = IcodeColors.Error)
        }
    }
}
