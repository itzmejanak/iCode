package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemClipboardTest {
    private fun held(cut: Boolean, folder: Boolean, workspace: Boolean) = ItemClipboard("/w/app/src", "src", cut, folder, workspace)

    @Test fun `only a folder becomes a workspace, and never a workspace that is cut`() {
        assertEquals("A workspace is a folder: paste src inside one", held(cut = true, folder = false, workspace = false).notAWorkspace)
        assertEquals("A workspace is a folder: paste src inside one", held(cut = false, folder = false, workspace = false).notAWorkspace)
        assertEquals("src is a workspace already", held(cut = true, folder = true, workspace = true).notAWorkspace)
        assertNull(held(cut = false, folder = true, workspace = true).notAWorkspace)
        assertNull(held(cut = true, folder = true, workspace = false).notAWorkspace)
    }

    @Test fun `a path in a workspace is named as the runtime names it`() {
        val root = "/data/user/0/dev.icode.mobile/files/workspaces/app"
        assertEquals("/workspace/app/src/Main.kt", WorkspaceRuntimePath.within(root, "/workspace/app", "$root/src/Main.kt"))
        assertEquals("/workspace/app", WorkspaceRuntimePath.within(root, "/workspace/app", root))
    }
}
