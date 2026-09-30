package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledField
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/**
 * A new workspace's name, as New Folder, Import and Clone ask for it: the name, the path it will
 * have in the runtime as it is typed, and whether it can be used — said here, before anything runs.
 */
@Composable
internal fun WorkspaceNameFields(name: String, onNameChange: (String) -> Unit, problem: String?) {
    LabeledTextField(label = "Folder Name", value = name, onValueChange = onNameChange, literal = true)
    LabeledField("Path", WorkspaceRuntimePath.of(name.trim()))
    // Nothing typed yet is not a mistake to point at.
    if (name.isNotBlank()) {
        StatusLabel(problem ?: "Path is valid and available", if (problem == null) IcodeColors.Green else IcodeColors.Error)
    }
}

/** What keeps [name] from being a new workspace now; null when nothing does. */
@Composable
internal fun newWorkspaceProblem(name: String): String? {
    val workspaces = LocalWorkbenchSnapshot.current.workspaces
    return WorkspaceNameRules.newWorkspaceError(name) { taken -> workspaces.any { it.name == taken } }
}

/**
 * New Folder's page, and Import's: [above] the name, then the space the phone has — every
 * workspace lives on it — and [actionLabel] at the foot.
 */
@Composable
internal fun NewWorkspacePage(
    title: String,
    actions: NavigationActions,
    name: String,
    onNameChange: (String) -> Unit,
    actionLabel: String,
    onSubmit: () -> Unit,
    ready: Boolean = true,
    above: @Composable () -> Unit = {},
) {
    val storage = LocalWorkbenchSnapshot.current.storage
    val problem = newWorkspaceProblem(name)
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(title, onBack = actions.back)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(IcodeSpacing.Space5),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space5),
        ) {
            above()
            WorkspaceNameFields(name, onNameChange, problem)
            IcodeCard(Modifier.fillMaxWidth(), elevated = true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
                ) {
                    Icon(Icons.Outlined.Storage, contentDescription = null, tint = IcodeColors.Blue)
                    Column {
                        Text("Device Available Storage", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                        MonoLine(
                            storage?.let { "${ByteSize.format(it.availableBytes)} free of ${ByteSize.format(it.totalBytes)}" }
                                ?: "Storage information unavailable",
                            style = IcodeType.MonoSmall,
                        )
                    }
                }
            }
        }
        PrimaryButton(
            actionLabel,
            onSubmit,
            Modifier.fillMaxWidth().padding(IcodeSpacing.Space5),
            enabled = ready && problem == null,
        )
    }
}
