package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledField
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.ProjectDestination
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * A new workspace's name, as New Folder and Clone ask for it: the name, the path it will
 * have in the runtime as it is typed, and whether it can be used — said here, before anything runs.
 */
@Composable
internal fun WorkspaceNameFields(name: String, onNameChange: (String) -> Unit, problem: String?, destination: ProjectDestination? = null) {
    LabeledTextField(label = "Folder Name", value = name, onValueChange = onNameChange, literal = true)
    IcodeSection {
        LabeledField("Path", WorkspaceRuntimePath.of(name.trim(), destination))
        // Nothing typed yet is not a mistake to point at.
        if (name.isNotBlank()) {
            StatusLabel(problem ?: "Path is valid and available", if (problem == null) IcodeColors.Green else IcodeColors.Error)
        }
    }
}

/** What keeps [name] from being made where it goes — a new workspace, or [destination]; null when nothing does. */
@Composable
internal fun projectProblem(name: String, destination: ProjectDestination?): String? {
    val workspaces = LocalICodeSnapshot.current.workspaces
    return WorkspaceNameRules.projectNameError(name, destination) { taken -> workspaces.any { it.name == taken } }
}

/**
 * Where a clone goes (docs/workspaces-and-files.md): a new workspace, or a folder of one,
 * chosen in the file explorer.
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
