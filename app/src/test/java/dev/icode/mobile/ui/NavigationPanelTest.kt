package dev.icode.mobile.ui

import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPanelTest {
    @Test fun tabsShareOneBackStackEntry() {
        val navigation = NavigationState.create(Route.WorkspaceEditor, captureMode = false)
        navigation.actions.openPanel(ToolPanelTab.Problems)
        assertEquals(Route.TerminalExpanded, navigation.current)
        assertEquals(ToolPanelTab.Problems, navigation.panelTab)
        assertEquals(2, navigation.historyNames.size)

        navigation.actions.openPanel(ToolPanelTab.Output)
        assertEquals(Route.TerminalExpanded, navigation.current)
        assertEquals(ToolPanelTab.Output, navigation.panelTab)
        assertEquals(2, navigation.historyNames.size)

        navigation.actions.back()
        assertEquals(Route.WorkspaceEditor, navigation.current)
    }

    @Test fun oldFrameRoutesAreAliasesForTheSharedPanel() {
        val navigation = NavigationState.create(Route.WorkspaceEditor, captureMode = false)
        navigation.actions.navigate(Route.OutputPanel)
        assertEquals(Route.TerminalExpanded, navigation.current)
        assertEquals(ToolPanelTab.Output, navigation.panelTab)

        val capture = NavigationState.create(Route.ProblemsPanel, captureMode = true)
        assertEquals(Route.ProblemsPanel, capture.current)
        assertEquals(ToolPanelTab.Problems, capture.panelTab)
    }

    @Test fun eachEntryIsKeyedByItsPlaceSoWhatItKeepsIsItsOwn() {
        val navigation = NavigationState.create(Route.SourceControl, captureMode = false)
        navigation.actions.navigate(Route.CommitPush)
        navigation.actions.navigate(Route.BranchSelector)
        assertEquals(listOf("0:SourceControl", "1:CommitPush", "2:BranchSelector"), navigation.entryKeys)

        // Closed, the entry left the history — the host forgets what it kept — so opened again it starts afresh.
        navigation.actions.back()
        navigation.actions.back()
        navigation.actions.navigate(Route.CommitPush)
        assertEquals(listOf("0:SourceControl", "1:CommitPush"), navigation.entryKeys)

        // A replaced entry is a new one.
        navigation.actions.replace(Route.DiffView)
        assertEquals(listOf("0:SourceControl", "1:DiffView"), navigation.entryKeys)
    }

    @Test fun aStepToTheScreenBelowIsAStepForwardAndBackReturns() {
        // The owner, on the phone: the chat's workspace opens its tree, and Back is the chat again.
        val navigation = NavigationState.create(Route.WorkspaceExplorer, captureMode = false)
        navigation.actions.navigate(Route.Assistant)
        navigation.actions.navigate(Route.WorkspaceExplorer)
        assertEquals(listOf("0:WorkspaceExplorer", "1:Assistant", "2:WorkspaceExplorer"), navigation.entryKeys)
        navigation.actions.back()
        assertEquals(Route.Assistant, navigation.current)
    }
}
