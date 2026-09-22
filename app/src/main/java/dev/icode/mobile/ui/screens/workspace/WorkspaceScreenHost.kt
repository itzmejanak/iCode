package dev.icode.mobile.ui.screens.workspace

import androidx.compose.runtime.Composable
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/**
 * Workspace shell frames: explorer, editor, search and the empty editor.
 *
 * `else` stays until every frame in the group has migrated.
 */
@Composable
fun WorkspaceScreenHost(screen: DemoScreen, actions: DemoActions) {
    when (screen) {
        DemoScreen.WorkspaceExplorer -> WorkspaceExplorerScreen(actions)
        DemoScreen.WorkspaceEditor -> WorkspaceEditorScreen(actions)
        DemoScreen.WorkspaceSearch -> WorkspaceSearchScreen(actions)
        DemoScreen.EditorEmpty -> EditorEmptyScreen(actions)
        else -> Unit
    }
}
