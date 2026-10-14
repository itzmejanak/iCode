package dev.icode.mobile.ui

import android.content.ContextWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ItemClipboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every explorer's actions are one list, in one order (docs/file-explorer-unification-plan.md §3; storage-explorer-plan.md §3). */
class FileItemActionsTest {
    private val sent = mutableListOf<ICodeIntent>()
    private val held = ItemClipboard("/w/app/README.md", "README.md", cut = true, folder = false, workspace = false)

    private fun actions(clipboard: ItemClipboard?, workspace: Boolean = false, listed: Boolean = false, openable: Boolean = true) = FileItemActions.of(
        context = ContextWrapper(null),
        dispatch = { sent += it },
        location = "/w/app/src",
        runtimePath = "/workspace/app/src",
        clipboard = clipboard,
        open = IcodeMenuAction("Open", Icons.Outlined.FolderOpen, {}).takeIf { openable },
        onRename = {},
        onPaste = {},
        onDelete = {},
        openTerminal = if (workspace) ({}) else null,
        showInList = if (listed) ({}) else null,
    )

    @Test fun `a file's or a folder's actions, in the one order, Paste named by what it holds`() {
        assertEquals(
            listOf("Open", "Rename…", "Cut", "Copy", "Paste README.md", "Copy path", "Delete…"),
            actions(held).map { it.label },
        )
        assertTrue(actions(held).single { it.label.startsWith("Paste") }.enabled)
        assertTrue(actions(held).last().destructive)
    }

    @Test fun `with nothing held, Paste is there and greyed`() {
        val paste = actions(null).single { it.label.startsWith("Paste") }
        assertEquals("Paste", paste.label)
        assertFalse(paste.enabled)
    }

    @Test fun `a workspace adds Open Terminal after Open, and Show in Workspaces where it is listed apart`() {
        assertEquals(
            listOf("Open", "Open Terminal", "Rename…", "Cut", "Copy", "Paste README.md", "Show in Workspaces", "Copy path", "Delete…"),
            actions(held, workspace = true, listed = true).map { it.label },
        )
    }

    @Test fun `Cut and Copy hold the item itself`() {
        actions(held).single { it.label == "Cut" }.onClick()
        actions(held).single { it.label == "Copy" }.onClick()
        assertEquals(listOf<ICodeIntent>(ICodeIntent.CutItem("/w/app/src"), ICodeIntent.CopyItem("/w/app/src")), sent)
    }

    @Test fun `a file with nothing to open it in has no Open, and the rest as any other`() {
        assertEquals(
            listOf("Rename…", "Cut", "Copy", "Paste README.md", "Copy path", "Delete…"),
            actions(held, openable = false).map { it.label },
        )
    }

    @Test fun `+ makes a folder, and a file where one can go, and Paste is the paste bar's`() {
        assertEquals(listOf("New folder", "New file"), FileItemActions.creation({}, {}).map { it.label })
        assertEquals(listOf("New folder"), FileItemActions.creation(null, {}).map { it.label })
    }
}
