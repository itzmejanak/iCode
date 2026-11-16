package dev.icode.mobile.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one clipboard, holding many (docs/workspaces-and-files.md). */
class FileClipboardTest {
    private val src = HeldItem("/w/app/src", "src", folder = true, workspace = false)
    private val readme = HeldItem("/w/app/README.md", "README.md", folder = false, workspace = false)
    private val app = HeldItem("/w/app", "app", folder = true, workspace = true)

    @Test fun `one item is named, more are counted`() {
        assertEquals("src", FileClipboard(listOf(src), cut = true).name)
        assertEquals("2 items", FileClipboard(listOf(src, readme), cut = false).name)
    }

    @Test fun `only folders become workspaces, and never a workspace that is cut`() {
        assertEquals("A workspace is a folder: paste README.md inside one", FileClipboard(listOf(src, readme), cut = false).notAWorkspace)
        assertEquals("A workspace is a folder: paste README.md inside one", FileClipboard(listOf(readme), cut = true).notAWorkspace)
        assertEquals("app is a workspace already", FileClipboard(listOf(src, app), cut = true).notAWorkspace)
        assertNull(FileClipboard(listOf(src, app), cut = false).notAWorkspace)
        assertNull(FileClipboard(listOf(src), cut = true).notAWorkspace)
    }
}
