package dev.icode.mobile.ui

import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPanelTest {
    @Test fun tabsShareOneBackStackEntry() {
        val navigation = NavigationState.create(Route.WorkspaceEditor, staticDemo = false)
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
        val navigation = NavigationState.create(Route.WorkspaceEditor, staticDemo = false)
        navigation.actions.navigate(Route.OutputPanel)
        assertEquals(Route.TerminalExpanded, navigation.current)
        assertEquals(ToolPanelTab.Output, navigation.panelTab)

        val capture = NavigationState.create(Route.ProblemsPanel, staticDemo = true)
        assertEquals(Route.ProblemsPanel, capture.current)
        assertEquals(ToolPanelTab.Problems, capture.panelTab)
    }
}
