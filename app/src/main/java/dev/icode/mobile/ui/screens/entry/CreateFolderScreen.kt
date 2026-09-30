package dev.icode.mobile.ui.screens.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalWorkbenchGateway

/** New Folder: an empty workspace at `/workspace/<name>`, opened once it is made. */
@Composable
fun CreateFolderScreen(actions: NavigationActions) {
    var name by rememberSaveable { mutableStateOf("auth-v2_") }
    val gateway = LocalWorkbenchGateway.current
    NewWorkspacePage(
        title = "Create Folder",
        actions = actions,
        name = name,
        onNameChange = { name = it },
        actionLabel = "Create Folder",
        onSubmit = { gateway.dispatch(WorkbenchIntent.CreateWorkspace(name)) },
    )
}
