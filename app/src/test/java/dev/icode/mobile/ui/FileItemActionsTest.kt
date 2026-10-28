package dev.icode.mobile.ui

import android.content.ContextWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.files.CompressFormat
import dev.icode.mobile.domain.files.ExplorerState
import dev.icode.mobile.domain.files.FileClipboard
import dev.icode.mobile.domain.files.HeldItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every explorer's actions are one list, in one order and its groups (docs/explorer-gaps-plan.md §3.1). */
class FileItemActionsTest {
    private val sent = mutableListOf<ICodeIntent>()
    private val held = FileClipboard(listOf(HeldItem("/w/app/README.md", "README.md", folder = false, workspace = false)), cut = true)
    private val pages = SheetPageActions(compress = {}, properties = {})

    private fun actions(
        target: ActionTarget = ActionTarget("/w/app/src", "src", folder = true, runtimePath = "/workspace/app/src"),
        explorer: ExplorerState = ExplorerState(clipboard = held),
        openable: Boolean = true,
        listed: Boolean = false,
        sheet: Boolean = false,
    ) = FileItemActions.of(
        context = ContextWrapper(null),
        dispatch = { sent += it },
        target = target,
        explorer = explorer,
        open = IcodeMenuAction("Open", Icons.Outlined.FolderOpen, {}).takeIf { openable },
        onRename = {},
        onPaste = {},
        openTerminal = if (target.workspace) ({}) else null,
        showInList = if (listed) ({}) else null,
        pages = pages.takeIf { sheet },
    )

    @Test fun `a folder's actions, in the one order, Paste named by what it holds`() {
        val all = actions()
        assertEquals(
            listOf("Open", "Cut", "Copy", "Paste README.md", "Duplicate", "Rename…", "Compress to src.zip", "Compress to src.7z", "Add to Favorites", "Copy path", "Delete"),
            all.map { it.label },
        )
        assertEquals(listOf("Cut", "Rename…", "Compress to src.zip", "Add to Favorites", "Copy path"), all.filter { it.beginsGroup }.map { it.label })
        assertTrue(all.single { it.label.startsWith("Paste") }.enabled)
        assertTrue(all.last().destructive)
    }

    @Test fun `a sheet adds its pages, which open within it`() {
        val all = actions(sheet = true)
        assertEquals(
            listOf("Open", "Cut", "Copy", "Paste README.md", "Duplicate", "Rename…", "Compress to src.zip", "Compress to src.7z", "Compress…", "Add to Favorites", "Copy path", "Properties", "Delete"),
            all.map { it.label },
        )
        assertEquals(listOf("Rename…", "Compress…", "Properties"), all.filter { it.opensPage }.map { it.label })
        assertTrue(actions().none { it.opensPage })
    }

    @Test fun `with nothing held, Paste is there and greyed, and a folder pinned can be let go`() {
        val all = actions(explorer = ExplorerState(favorites = listOf("/w/app/src")))
        val paste = all.single { it.label.startsWith("Paste") }
        assertEquals("Paste", paste.label)
        assertFalse(paste.enabled)
        all.single { it.label == "Remove from Favorites" }.onClick()
        assertEquals(listOf<ICodeIntent>(FilesIntent.Favorite("/w/app/src", pinned = false)), sent)
    }

    @Test fun `a workspace adds Open Terminal and, listed apart, Show in Workspaces, and is never compressed at Private's top`() {
        assertEquals(
            listOf("Open", "Open Terminal", "Show in Workspaces", "Cut", "Copy", "Paste README.md", "Duplicate", "Rename…", "Add to Favorites", "Copy path", "Delete"),
            actions(ActionTarget("/w/app", "app", folder = true, runtimePath = "/workspace/app", workspace = true), listed = true, sheet = false).map { it.label },
        )
    }

    @Test fun `a file is handed to other apps, and an archive extracted, or said why not`() {
        assertEquals(
            listOf("Open", "Open with…", "Share…", "Cut", "Copy", "Paste README.md", "Duplicate", "Rename…", "Compress to notes.zip", "Compress to notes.7z", "Copy path", "Delete"),
            actions(ActionTarget("/w/app/notes.txt", "notes.txt", folder = false, runtimePath = "/workspace/app/notes.txt")).map { it.label },
        )
        val archive = actions(ActionTarget("/w/app/site.tar.gz", "site.tar.gz", folder = false, runtimePath = "/workspace/app/site.tar.gz"))
        assertTrue(archive.none { it.label.startsWith("Compress") })
        archive.single { it.label == "Extract here" }.onClick()
        val rar = actions(ActionTarget("/w/app/old.rar", "old.rar", folder = false, runtimePath = "/workspace/app/old.rar")).single { it.label == "Extract here" }
        assertFalse(rar.enabled)
        assertTrue(rar.detail!!.contains(".rar"))
        assertEquals(listOf<ICodeIntent>(FilesIntent.Extract("/w/app/site.tar.gz")), sent)
    }

    @Test fun `each acts on the item itself, held, copied beside it, compressed, and Delete moves it to Trash at once`() {
        val all = actions()
        listOf("Cut", "Copy", "Duplicate", "Compress to src.7z", "Delete").forEach { label -> all.single { it.label == label }.onClick() }
        val src = listOf("/w/app/src")
        assertEquals(
            listOf<ICodeIntent>(FilesIntent.Cut(src), FilesIntent.Copy(src), FilesIntent.Duplicate(src), FilesIntent.Compress(src, CompressFormat.SevenZ), FilesIntent.Trash(src)),
            sent,
        )
    }

    @Test fun `a file with nothing to open it in has no Open, and the rest as any other`() {
        assertEquals("Cut", actions(openable = false).first().label)
    }

    @Test fun `+ makes a folder, and a file where one can go, and Paste is the paste bar's`() {
        assertEquals(listOf("New folder", "New file"), FileItemActions.creation({}, {}).map { it.label })
        assertEquals(listOf("New folder"), FileItemActions.creation(null, {}).map { it.label })
    }
}
