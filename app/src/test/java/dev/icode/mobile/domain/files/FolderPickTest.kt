package dev.icode.mobile.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The folder picker's foot bar (docs/workspaces-and-files.md). */
class FolderPickTest {
    private val src = HeldItem("/w/app/src", "src", folder = true, workspace = false)
    private val readme = HeldItem("/w/app/README.md", "README.md", folder = false, workspace = false)

    @Test fun `a clone goes in the folder shown, or becomes a workspace at the top`() {
        assertEquals(PickBar("/workspace/app", "It goes in this folder", "Place here"), FolderPicks.bar(FolderPick.Clone, "/w/app", "/workspace/app", top = false))
        assertEquals("New workspace", FolderPicks.bar(FolderPick.Clone, "/w", "/workspace", top = true).action)
    }

    @Test fun `items move or are copied into the folder shown, never inside themselves, and a move needs somewhere new`() {
        val move = FolderPick.Transfer(listOf(src, readme), move = true)
        assertEquals(PickBar("/workspace/lib", "Moving 2 items into /workspace/lib", "Move here"), FolderPicks.bar(move, "/w/lib", "/workspace/lib", top = false))
        assertEquals("“src” cannot go inside itself", FolderPicks.bar(move, "/w/app/src/ui", "/workspace/app/src/ui", top = false).refusal)
        assertEquals("Already here", FolderPicks.bar(move, "/w/app", "/workspace/app", top = false).refusal)
        assertNull(FolderPicks.bar(move.copy(move = false), "/w/app", "/workspace/app", top = false).refusal)
        assertEquals("Copying src into /workspace/app", FolderPicks.bar(FolderPick.Transfer(listOf(src), move = false), "/w/app", "/workspace/app", top = false).caption)
    }

    @Test fun `at Private's top, folders become workspaces and a file is refused`() {
        val bar = FolderPicks.bar(FolderPick.Transfer(listOf(src), move = false), "/w", "/workspace", top = true)
        assertEquals(PickBar("/workspace", "Copying src here, each a workspace", "Copy here as workspaces"), bar)
        assertEquals("A workspace is a folder: paste README.md inside one", FolderPicks.bar(FolderPick.Transfer(listOf(readme), move = true), "/w", "/workspace", top = true).refusal)
    }

    @Test fun `archives are extracted into the folder shown`() {
        assertEquals(
            PickBar("/storage/emulated/0/Download", "Extracting site.tar.gz into /storage/emulated/0/Download", "Extract here"),
            FolderPicks.bar(FolderPick.Extract(listOf("/w/app/site.tar.gz")), "/storage/emulated/0/Download", "/storage/emulated/0/Download", top = false),
        )
    }
}
