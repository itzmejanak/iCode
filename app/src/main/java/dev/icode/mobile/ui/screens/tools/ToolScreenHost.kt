package dev.icode.mobile.ui.screens.tools

import androidx.compose.runtime.Composable
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.screens.workspace.FindMode
import dev.icode.mobile.ui.screens.workspace.WorkspaceFindScreen

/**
 * Source control, editor tooling and panel frames.
 *
 * `else` stays until every frame in the group has migrated.
 */
@Composable
fun ToolScreenHost(screen: Route, actions: NavigationActions) {
    when (screen) {
        Route.SourceControl -> SourceControlScreen(actions)
        Route.DiffView -> DiffViewScreen(actions)
        Route.QuickOpen -> WorkspaceFindScreen(actions, FindMode.Files)
        Route.BranchSelector -> BranchSelectorScreen(actions)
        Route.RepositorySelector -> RepositorySelectorScreen(actions)
        Route.EditorFind -> EditorFindScreen(actions)
        Route.TerminalExpanded, Route.ProblemsPanel, Route.OutputPanel -> TerminalScreen(actions)
        Route.ConflictResolution -> ConflictResolutionScreen(actions)
        Route.CommandPalette -> CommandPaletteScreen(actions)
        Route.CommitPush -> CommitPushScreen(actions)
        Route.PullFetchProgress -> PullProgressScreen(actions)
        Route.GitAuth -> GitAuthScreen(actions)
        else -> Unit
    }
}
