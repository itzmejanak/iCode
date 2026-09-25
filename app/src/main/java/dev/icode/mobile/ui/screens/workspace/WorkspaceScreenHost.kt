package dev.icode.mobile.ui.screens.workspace

import androidx.compose.runtime.Composable
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/**
 * Workspace shell frames: explorer, editor, search and the empty editor.
 *
 * `else` stays until every frame in the group has migrated.
 */
@Composable
fun WorkspaceScreenHost(screen: Route, actions: NavigationActions) {
    when (screen) {
        Route.WorkspaceExplorer -> WorkspaceExplorerScreen(actions)
        // The editor route shows the empty editor until a document is open:
        // a real condition, not an entry point of its own.
        Route.WorkspaceEditor ->
            if (LocalWorkbenchSnapshot.current.activeDocument == null) EditorEmptyScreen(actions)
            else WorkspaceEditorScreen(actions)
        Route.WorkspaceSearch -> WorkspaceFindScreen(actions, FindMode.Text)
        Route.EditorEmpty -> EditorEmptyScreen(actions)
        else -> Unit
    }
}
