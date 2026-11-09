package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteCommandsTest {
    private val snapshot = paletteReadySnapshot

    @Test fun queryMatchesTitleAndKeywordsWithoutChangingIdentity() {
        assertEquals(
            listOf(PaletteCommandId.PushBranch),
            PaletteCommands.search(snapshot, "git PUSH").map { it.id },
        )
        assertEquals(
            listOf(PaletteCommandId.SearchWorkspace),
            PaletteCommands.search(snapshot, "find content").map { it.id },
        )
        assertTrue(PaletteCommands.search(snapshot, "no-such-command").isEmpty())
    }

    @Test fun unavailableCommandsRemainDiscoverableWithReason() {
        val noWorkspace = snapshot.copy(activeWorkspaceLocation = null, gitRepository = null, activeDocument = null)
        val commands = PaletteCommands.search(noWorkspace, "")
        assertFalse(commands.first { it.id == PaletteCommandId.PushBranch }.available)
        assertFalse(commands.first { it.id == PaletteCommandId.SaveCurrent }.available)
        assertFalse(commands.first { it.id == PaletteCommandId.QuickOpen }.available)
        assertTrue(commands.first { it.id == PaletteCommandId.OpenTerminal }.available)
        assertEquals("Open a workspace first", commands.first { it.id == PaletteCommandId.OpenAssistant }.unavailableReason)
        assertEquals(PaletteCommandId.OpenAssistant, PaletteCommands.search(snapshot, "assistant").single().id)
    }

    @Test fun remoteAndStagedChangesAreRequiredForGitMutations() {
        val localOnly = snapshot.copy(
            gitRepository = snapshot.gitRepository!!.copy(remoteName = null),
            gitChanges = emptyList(),
        )
        val commands = PaletteCommands.search(localOnly, "git")
        assertEquals("Add a Git remote first", commands.first { it.id == PaletteCommandId.PullBranch }.unavailableReason)
        assertEquals("Stage changes first", commands.first { it.id == PaletteCommandId.CommitStaged }.unavailableReason)
        assertTrue(commands.first { it.id == PaletteCommandId.OpenSourceControl }.available)
    }

    @Test fun languageCommandsShowOnlyWhatTheFilesRunningServerOffers() {
        val document = OpenDocument("App.tsx", "/w/App.tsx", "", TextEncoding.Utf8, LineEnding.Lf, false, false)
        val served = snapshot.copy(
            activeDocument = document,
            openDocuments = listOf(document),
            languageServices = listOf(
                LanguageService("TS", "TS", "", id = "ts", languages = "typescriptreact", status = LanguageServiceStatus.Running, capabilities = listOf("Hover", "Rename")),
                LanguageService("PY", "PY", "", id = "py", languages = "python", status = LanguageServiceStatus.Running, capabilities = listOf("Formatting")),
            ),
        )
        assertEquals(listOf(PaletteCommandId.LanguageHover, PaletteCommandId.LanguageRename), PaletteCommands.search(served, "").filter { it.id.language != null }.map { it.id })
        val stopped = served.copy(languageServices = served.languageServices.map { it.copy(status = LanguageServiceStatus.Stopped) })
        assertTrue(PaletteCommands.search(stopped, "").none { it.id.language != null })
    }
}
