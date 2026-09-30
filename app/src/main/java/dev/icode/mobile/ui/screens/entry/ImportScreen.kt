package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledField
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import dev.icode.mobile.ui.rememberImportPicker

/**
 * Import (docs/workspace-storage-plan.md §4.5): the folder picked on phone storage is copied into a
 * new workspace, named after it unless the user names it otherwise. The copy runs as a task; the
 * folder on phone storage is only read.
 */
@Composable
fun ImportScreen(actions: NavigationActions) {
    val gateway = LocalWorkbenchGateway.current
    val source = LocalWorkbenchSnapshot.current.importSource
    val pick = rememberImportPicker()
    // Another folder picked with Change proposes its own name.
    var name by rememberSaveable(source?.uri) { mutableStateOf(source?.name.orEmpty()) }
    NewWorkspacePage(
        title = "Import",
        actions = actions,
        name = name,
        onNameChange = { name = it },
        actionLabel = "Import",
        onSubmit = { gateway.dispatch(WorkbenchIntent.ImportWorkspace(name)) },
        ready = source != null,
        above = {
            LabeledField("From", source?.name ?: "Choose a folder on phone storage") {
                Text(
                    "Change",
                    modifier = Modifier.clickable(onClick = pick),
                    style = IcodeType.Label.copy(color = IcodeColors.Accent),
                )
            }
        },
    )
}
