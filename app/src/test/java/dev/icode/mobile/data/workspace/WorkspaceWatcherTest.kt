package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.TextEncoding
import dev.icode.mobile.domain.paletteReadySnapshot
import dev.icode.mobile.domain.FilesBrowserState
import dev.icode.mobile.domain.StoragePlace
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceWatcherTest {
    @Test fun `a burst ends after a second of quiet`() {
        assertEquals(1_000L, settleDelay(first = 0, last = 0, now = 0))
        assertEquals(700L, settleDelay(first = 0, last = 200, now = 500))
        assertTrue(settleDelay(first = 0, last = 200, now = 1_200) <= 0)
    }

    @Test fun `a burst that never quiets is still reported every five seconds`() {
        assertEquals(100L, settleDelay(first = 0, last = 4_900, now = 4_900))
        assertTrue(settleDelay(first = 0, last = 4_990, now = 5_000) <= 0)
    }

    private val workspaces = File("/files/workspaces")

    @Test fun `the workspaces, and in the open one its top, expanded folders and open files' folders, are watched`() {
        val root = "/w"
        val snapshot = paletteReadySnapshot.copy(
            activeWorkspaceLocation = root,
            files = listOf(
                FileEntry("src", "/w/src", FileKind.Folder, expanded = true),
                FileEntry("docs", "/w/docs", FileKind.Folder, expanded = false),
                FileEntry("a.kt", "/w/src/a.kt", FileKind.File, depth = 1),
            ),
            openDocuments = listOf(
                OpenDocument("b.md", "/w/notes/b.md", "", TextEncoding.Utf8, LineEnding.Lf, dirty = false, readOnly = false),
            ),
        )
        assertEquals(setOf(workspaces, File("/w"), File("/w/src"), File("/w/notes")), watchedFolders(snapshot, workspaces))
    }

    @Test fun `without an open workspace only the workspaces' folder is watched`() {
        assertEquals(setOf(workspaces), watchedFolders(paletteReadySnapshot.copy(activeWorkspaceLocation = null), workspaces))
    }

    @Test fun `the file explorer's folder is watched too, a workspace open or not`() {
        val browsing = FilesBrowserState(StoragePlace.Private, workspaces.path, "${workspaces.path}/tools/cli")
        assertEquals(
            setOf(workspaces, File("${workspaces.path}/tools/cli")),
            watchedFolders(paletteReadySnapshot.copy(activeWorkspaceLocation = null, filesBrowser = browsing), workspaces),
        )
    }
}
