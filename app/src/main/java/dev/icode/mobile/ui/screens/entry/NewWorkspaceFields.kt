package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.clickable
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
import dev.icode.mobile.domain.ProjectDestination
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * A new workspace's name, as New Folder, Import and Clone ask for it: the name, the path it will
 * have in the runtime as it is typed, and whether it can be used — said here, before anything runs.
 */
@Composable
internal fun WorkspaceNameFields(name: String, onNameChange: (String) -> Unit, problem: String?, destination: ProjectDestination? = null) {
    LabeledTextField(label = "Folder Name", value = name, onValueChange = onNameChange, literal = true)
    LabeledField("Path", WorkspaceRuntimePath.of(name.trim(), destination))
    // Nothing typed yet is not a mistake to point at.
    if (name.isNotBlank()) {
        StatusLabel(problem ?: "Path is valid and available", if (problem == null) IcodeColors.Green else IcodeColors.Error)
    }
}

/** What keeps [name] from being made where it goes — a new workspace, or [destination]; null when nothing does. */
@Composable
internal fun projectProblem(name: String, destination: ProjectDestination?): String? {
    val workspaces = LocalICodeSnapshot.current.workspaces
    return WorkspaceNameRules.projectNameError(name, destination) { taken -> workspaces.any { it.name == taken } }
}

/**
 * Where a clone or an import goes (docs/workspace-destinations-plan.md): a new workspace, or a
 * folder of one, chosen in the workspaces' file explorer.
 */
@Composable
internal fun DestinationField(destination: ProjectDestination?, onChange: () -> Unit) {
    LabeledField("Where", destination?.displayPath ?: "New workspace") {
        Text(
            "Change",
            modifier = Modifier.clickable(onClick = onChange),
            style = IcodeType.Label.copy(color = IcodeColors.Accent),
        )
    }
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
    val storage = LocalICodeSnapshot.current.storage
    val destination = LocalICodeSnapshot.current.projectDestination
    val problem = projectProblem(name, destination)
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(title, onBack = actions.back)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(IcodeSpacing.Space5),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space5),
        ) {
            above()
            WorkspaceNameFields(name, onNameChange, problem, destination)
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
