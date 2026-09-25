package dev.icode.mobile.ui.screens.tools

import dev.icode.mobile.navigation.ToolPanelTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalPanelLifecycleTest {
    @Test fun openingProblemsOrOutputDoesNotStartAShell() {
        assertFalse(shouldAutoOpenTerminal(ToolPanelTab.Problems, runtimeReady = true, alreadyAttempted = false))
        assertFalse(shouldAutoOpenTerminal(ToolPanelTab.Output, runtimeReady = true, alreadyAttempted = false))
    }

    @Test fun terminalStartsOnlyOnceWhenRuntimeIsReady() {
        assertFalse(shouldAutoOpenTerminal(ToolPanelTab.Terminal, runtimeReady = false, alreadyAttempted = false))
        assertTrue(shouldAutoOpenTerminal(ToolPanelTab.Terminal, runtimeReady = true, alreadyAttempted = false))
        assertFalse(shouldAutoOpenTerminal(ToolPanelTab.Terminal, runtimeReady = true, alreadyAttempted = true))
    }

    @Test fun endingShellOnlyReturnsWhenTerminalIsVisible() {
        assertTrue(shouldReturnAfterLastTerminal(ToolPanelTab.Terminal, hadSessions = true))
        assertFalse(shouldReturnAfterLastTerminal(ToolPanelTab.Problems, hadSessions = true))
        assertFalse(shouldReturnAfterLastTerminal(ToolPanelTab.Output, hadSessions = true))
        assertFalse(shouldReturnAfterLastTerminal(ToolPanelTab.Terminal, hadSessions = false))
    }
}
