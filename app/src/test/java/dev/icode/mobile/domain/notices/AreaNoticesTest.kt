package dev.icode.mobile.domain.notices

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.RuntimeInstallReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The builders of runtime, language servers and MCP: what each says, where, and what it offers. */
class AreaNoticesTest {
    private val open = ICodeStateHolder.emptySnapshot().copy(activeWorkspace = "phone", activeWorkspaceLocation = "/w/phone")

    @Test fun `a runtime install says what it did, and a first install that failed can be tried again`() {
        val installed = RuntimeNotices.installed(RuntimeInstallReason.Repair, away = true)
        assertEquals("Repaired the Linux runtime" to "Linux runtime", installed.title to installed.place?.line)
        assertTrue(installed.tellAway)
        val failed = RuntimeNotices.notInstalled(RuntimeInstallReason.Initial, "E: Unable to fetch")
        assertEquals("Couldn't install the Linux runtime" to "E: Unable to fetch", failed.title to failed.output)
        assertEquals(listOf(NoticeAction.Run(NoticeCommand.InstallRuntime, "Try Again")), failed.actions)
        assertTrue(RuntimeNotices.notInstalled(RuntimeInstallReason.Reset, "x").actions.isEmpty())
    }

    @Test fun `a language server is named where it works, and the same message again folds`() {
        val crashed = LanguageNotices.stopped(open, "pylsp", started = true, words = "Traceback")
        assertEquals("pylsp stopped" to "phone · pylsp", crashed.title to crashed.place?.line)
        assertEquals("Traceback", crashed.output)
        val said = LanguageNotices.said(open, "pylsp", error = false, words = "No interpreter")
        assertEquals(said.repeatKey, LanguageNotices.said(open, "pylsp", error = false, words = "No interpreter").repeatKey)
        assertEquals("pylsp reported a warning" to "No interpreter", said.title to said.detail)
    }

    @Test fun `an MCP server is named once, in the place, never again in the detail`() {
        val signIn = McpNotices.notSignedIn(open, "github", "github did not answer in time.")
        assertEquals("Couldn't sign in to github", signIn.title)
        assertEquals("phone · github", signIn.place?.line)
        assertEquals("github did not answer in time.", signIn.detail)
        assertEquals("Name the server", McpNotices.notChanged(open, null, "Name the server").title)
        assertNull(McpNotices.notChanged(open, null, "Name the server").place)
    }

    @Test fun `what a tap asked for with nothing to do it on is its answer, not kept`() {
        assertFalse(NoticeRules.kept(AssistantNotices.nothingToShare()))
        assertTrue(NoticeRules.rings(AssistantNotices.nothingToShare()))
        assertFalse(NoticeRules.kept(StorageNotices.cacheCleared(0)))
        assertTrue(NoticeRules.kept(StorageNotices.cacheCleared(3)))
    }
}
