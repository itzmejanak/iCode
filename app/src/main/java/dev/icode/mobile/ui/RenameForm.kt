package dev.icode.mobile.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
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

/**
 * Renaming one item, wherever it is asked: an item sheet's Rename… page, or a dialog where one is
 * left (docs/explorer-gaps-plan.md §3.1). It holds the new name, and why a name cannot be, as it is
 * typed: a workspace's rules, a folder's or a file's ([WorkspaceNameRules]). The request is answered
 * by its id; a refusal is said under the field.
 */
@Stable
internal class RenameForm(
    private val location: String,
    private val current: String,
    val folder: Boolean,
    val workspace: Boolean,
    private val taken: (String) -> Boolean,
    named: MutableState<String>,
    val request: PendingRequest,
) {
    var name by named

    val problem: String?
        get() = when {
            workspace -> WorkspaceNameRules.newWorkspaceError(name) { it != current && taken(it) }
            folder -> WorkspaceNameRules.workspaceFolderValidationError(name)
            else -> WorkspaceNameRules.fileValidationError(name)
        }

    /** A new name that can be, and nothing sent yet. */
    val ready: Boolean get() = problem == null && name.trim() != current && !request.waiting

    fun send(dispatch: (ICodeIntent) -> Unit) = request.send { id -> dispatch(ICodeIntent.RenameWorkspaceItem(location, name.trim(), id)) }
}

/** The form renaming [location], now [current]; [onDone] once the gateway says it is renamed. */
@Composable
internal fun rememberRenameForm(location: String, current: String, folder: Boolean, workspace: Boolean, onDone: () -> Unit): RenameForm {
    val workspaces = LocalICodeSnapshot.current.workspaces
    val named = rememberSaveable(location) { mutableStateOf(current) }
    val request = rememberPendingRequest(location, onDone)
    return RenameForm(location, current, folder, workspace, { name -> workspaces.any { it.name == name } }, named, request)
}

/** The new name's field — a workspace's `/workspace/<name>` under it — and why it cannot be, or why it was refused. */
@Composable
internal fun RenameFields(form: RenameForm) = NameFields(
    label = if (form.folder) "Folder name" else "File name",
    name = form.name,
    onName = { form.name = it; form.request.failure = null },
    workspace = form.workspace,
    problem = form.problem ?: form.request.failure,
)

/**
 * A name as it is typed, for an item made or renamed: [label]'s field — literal for a [workspace],
 * whose `/workspace/<name>` shows under it — and the [problem] with it, once something is typed.
 */
@Composable
internal fun NameFields(label: String, name: String, onName: (String) -> Unit, workspace: Boolean, problem: String?) {
    LabeledTextField(label, name, onName, literal = workspace)
    if (workspace) MonoLine(WorkspaceRuntimePath.of(name.trim()), style = IcodeType.MonoSmall)
    problem?.takeIf { name.isNotEmpty() }?.let { Text(it, style = IcodeType.Caption, color = IcodeColors.Error) }
}

/** Renaming in a dialog, where an item's actions are a dropdown still: the IDE tree's rows, the home screen's workspaces. */
@Composable
internal fun RenameDialog(location: String, current: String, folder: Boolean, workspace: Boolean, onDismiss: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val form = rememberRenameForm(location, current, folder, workspace, onDismiss)
    IcodeConfirmDialog(
        title = when {
            workspace -> "Rename workspace"
            folder -> "Rename folder"
            else -> "Rename file"
        },
        confirmLabel = "Rename",
        confirmEnabled = form.ready,
        dismissEnabled = !form.request.waiting,
        onDismiss = onDismiss,
        onConfirm = { form.send(gateway::dispatch) },
    ) {
        RenameFields(form)
    }
}
