package dev.icode.mobile.ui.screens.tools

import androidx.compose.runtime.Composable
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/**
 * Source control, editor tooling and panel frames.
 *
 * `else` stays until every frame in the group has migrated.
 */
@Composable
fun ToolScreenHost(screen: DemoScreen, actions: DemoActions) {
    when (screen) {
        DemoScreen.SourceControl -> SourceControlScreen(actions)
        DemoScreen.DiffView -> DiffViewScreen(actions)
        DemoScreen.QuickOpen -> QuickOpenScreen(actions)
        DemoScreen.BranchSelector -> BranchSelectorScreen(actions)
        DemoScreen.EditorFind -> EditorFindScreen(actions)
        DemoScreen.TerminalExpanded -> TerminalScreen(actions)
        DemoScreen.ProblemsPanel -> ProblemsScreen(actions)
        DemoScreen.ConflictResolution -> ConflictResolutionScreen(actions)
        DemoScreen.CommandPalette -> CommandPaletteScreen(actions)
        DemoScreen.OutputPanel -> OutputPanelScreen(actions)
        DemoScreen.CommitPush -> CommitPushScreen(actions)
        DemoScreen.PullFetchProgress -> PullProgressScreen(actions)
        DemoScreen.GitAuth -> GitAuthScreen(actions)
        else -> Unit
    }
}
