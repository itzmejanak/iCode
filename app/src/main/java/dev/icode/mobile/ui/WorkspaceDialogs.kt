package dev.icode.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.WorkspaceNameRules

/**
 * New Folder on the Workspaces page, as a file manager asks for one: the new workspace appears in the
 * list. Its name is checked as it is typed; the backend's answer comes by request id, a refusal said
 * here.
 */
@Composable
internal fun NewWorkspaceDialog(onDismiss: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val workspaces = LocalICodeSnapshot.current.workspaces
    var name by rememberSaveable { mutableStateOf("") }
    val request = rememberPendingRequest(NEW, onDismiss)
    val problem = WorkspaceNameRules.newWorkspaceError(name) { taken -> workspaces.any { it.name == taken } }
    IcodeConfirmDialog(
        title = "New folder",
        confirmLabel = "Create",
        confirmEnabled = problem == null && !request.waiting,
        dismissEnabled = !request.waiting,
        onDismiss = onDismiss,
        onConfirm = { request.send { requestId -> gateway.dispatch(ICodeIntent.CreateWorkspace(name.trim(), requestId)) } },
    ) {
        NameFields("Folder name", name, { name = it; request.failure = null }, workspace = true, problem = problem ?: request.failure)
    }
}

private const val NEW = "new"
