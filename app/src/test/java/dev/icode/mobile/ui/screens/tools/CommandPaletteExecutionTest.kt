package dev.icode.mobile.ui.screens.tools

import dev.icode.mobile.demo.DemoWorkbenchGateway
import dev.icode.mobile.domain.PaletteCommandId
import dev.icode.mobile.domain.PaletteCommands
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteExecutionTest {
    private val snapshot = DemoWorkbenchGateway().snapshot.value

    @Test fun pushAndPullRunTheirOwnIntentThenOpenProgress() {
        for ((id, expectedIntent) in listOf(
            PaletteCommandId.PushBranch to WorkbenchIntent.PushRemote,
            PaletteCommandId.PullBranch to WorkbenchIntent.PullRemote,
        )) {
            val calls = mutableListOf<Any>()
            val command = PaletteCommands.search(snapshot, "").first { it.id == id }
            runPaletteCommand(command, snapshot, { calls += it }, { calls += it }, { calls += "back" })
            assertEquals(listOf(expectedIntent, Route.PullFetchProgress), calls)
        }
    }

    @Test fun saveUsesActiveDocumentAndReturnsWithoutOpeningAnotherFile() {
        val calls = mutableListOf<Any>()
        val command = PaletteCommands.search(snapshot, "").first { it.id == PaletteCommandId.SaveCurrent }
        runPaletteCommand(command, snapshot, { calls += it }, { calls += it }, { calls += "back" })
        assertEquals(listOf(WorkbenchIntent.SaveDocument(snapshot.activeDocument!!.location), "back"), calls)
    }

    @Test fun commitOpensMessageFormAndUnavailableCommandDoesNothing() {
        val calls = mutableListOf<Any>()
        val commit = PaletteCommands.search(snapshot, "").first { it.id == PaletteCommandId.CommitStaged }
        runPaletteCommand(commit, snapshot, { calls += it }, { calls += it }, { calls += "back" })
        assertEquals(listOf(Route.SourceControl), calls)
        calls.clear()
        val unavailable = PaletteCommands.search(snapshot.copy(gitRepository = null), "").first { it.id == PaletteCommandId.PushBranch }
        runPaletteCommand(unavailable, snapshot, { calls += it }, { calls += it }, { calls += "back" })
        assertTrue(calls.isEmpty())
    }
}
