package dev.icode.mobile.ui

import android.content.ContextWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.files.ExplorerState
import dev.icode.mobile.domain.files.FavoriteFolder
import dev.icode.mobile.domain.files.FileClipboard
import dev.icode.mobile.domain.files.FolderPick
import dev.icode.mobile.domain.files.HeldItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every explorer's actions are one list, in one order and its groups, shown in one sheet (docs/explorer-gaps-plan.md §3.1). */
class FileItemActionsTest {
    private val sent = mutableListOf<ICodeIntent>()
    private val held = FileClipboard(listOf(HeldItem("/w/app/README.md", "README.md", folder = false, workspace = false)), cut = true)
    private val opened = mutableListOf<String>()
    private val pages = SheetPageActions(compress = { opened += "Compress" }, properties = { opened += "Properties" })

    private val picked = mutableListOf<FolderPick>()
    private val selected = mutableListOf<List<String>>()
    private val screen = ScreenActions(pick = { picked += it }, select = { selected += it })
    private val src = ActionTarget("/w/app/src", "src", folder = true, runtimePath = "/workspace/app/src")
    private val notes = ActionTarget("/w/app/notes.txt", "notes.txt", folder = false, runtimePath = "/workspace/app/notes.txt")

    private fun actions(
        target: ActionTarget = src,
        explorer: ExplorerState = ExplorerState(clipboard = held),
        openable: Boolean = true,
        listed: Boolean = false,
    ) = FileItemActions.of(
        context = ContextWrapper(null),
        dispatch = { sent += it },
        targets = listOf(target),
        explorer = explorer,
        onRename = {},
        pages = pages,
        open = IcodeMenuAction("Open", Icons.Outlined.FolderOpen, {}).takeIf { openable },
        onPaste = {},
        openTerminal = if (target.workspace) ({}) else null,
        showInList = if (listed) ({}) else null,
    )

    /** A selection's sheet in the explorer: many. */
    private fun many(vararg targets: ActionTarget) = FileItemActions.of(
        context = ContextWrapper(null),
        dispatch = { sent += it },
        targets = targets.toList(),
        explorer = ExplorerState(),
        onRename = {},
        pages = pages,
        screen = screen,
    )

    @Test fun `a folder's actions, in the one order, Paste named by what it holds`() {
        val all = actions()
        assertEquals(
            listOf("Open", "Cut", "Copy", "Paste README.md", "Duplicate", "Rename…", "Compress…", "Add to Favorites", "Copy path", "Properties", "Delete"),
            all.map { it.label },
        )
        assertEquals(listOf("Cut", "Rename…", "Compress…", "Add to Favorites", "Copy path"), all.filter { it.beginsGroup }.map { it.label })
        assertTrue(all.single { it.label.startsWith("Paste") }.enabled)
        assertTrue(all.last().destructive)
    }

    @Test fun `Compress is one action, whose page chooses the format, and there are no shortcuts to a format`() {
        val all = actions()
        assertEquals(listOf("Compress…"), all.filter { it.label.startsWith("Compress") }.map { it.label })
        all.single { it.label == "Compress…" }.onClick()
        assertEquals(listOf("Compress"), opened)
        assertTrue(sent.isEmpty())
    }

    @Test fun `Rename, Compress and Properties open pages within the sheet`() {
        val all = actions()
        assertEquals(listOf("Rename…", "Compress…", "Properties"), all.filter { it.opensPage }.map { it.label })
        all.single { it.label == "Properties" }.onClick()
        assertEquals(listOf("Properties"), opened)
    }

    @Test fun `with nothing held, Paste is there and greyed, and a folder pinned can be let go`() {
        val all = actions(explorer = ExplorerState(favorites = listOf(FavoriteFolder("src", "/w/app/src", "/workspace/app/src"))))
        val paste = all.single { it.label.startsWith("Paste") }
        assertEquals("Paste", paste.label)
        assertFalse(paste.enabled)
        all.single { it.label == "Remove from Favorites" }.onClick()
        assertEquals(listOf<ICodeIntent>(FilesIntent.Favorite("/w/app/src", pinned = false)), sent)
    }

    @Test fun `a workspace adds Open Terminal and, listed apart, Show in Workspaces, and is never compressed at Private's top`() {
        assertEquals(
            listOf("Open", "Open Terminal", "Show in Workspaces", "Cut", "Copy", "Paste README.md", "Duplicate", "Rename…", "Add to Favorites", "Copy path", "Properties", "Delete"),
            actions(ActionTarget("/w/app", "app", folder = true, runtimePath = "/workspace/app", workspace = true), listed = true).map { it.label },
        )
    }

    @Test fun `a view's file shows its folder, and many gathered from more than one folder are not renamed or compressed`() {
        assertEquals("Show in folder", actions(notes, listed = true).single { it.label.startsWith("Show in") }.label)
        val gathered = FileItemActions.of(
            context = ContextWrapper(null),
            dispatch = { sent += it },
            targets = listOf(notes, notes.copy(location = "/phone/Download/b.txt", name = "b.txt")),
            explorer = ExplorerState(),
            onRename = null,
            pages = SheetPageActions(compress = null, properties = {}),
            screen = screen,
        )
        assertEquals(
            listOf("Share…", "Cut", "Copy", "Move to…", "Copy to…", "Duplicate", "Copy paths", "Properties", "Select", "Delete"),
            gathered.map { it.label },
        )
    }

    @Test fun `a file is handed to other apps, and an archive extracted, or said why not`() {
        assertEquals(
            listOf("Open", "Open with…", "Share…", "Cut", "Copy", "Paste README.md", "Duplicate", "Rename…", "Compress…", "Copy path", "Properties", "Delete"),
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

    @Test fun `each acts on the item itself, held, copied beside it, and Delete moves it to Trash at once`() {
        val all = actions()
        listOf("Cut", "Copy", "Duplicate", "Delete").forEach { label -> all.single { it.label == label }.onClick() }
        val src = listOf("/w/app/src")
        assertEquals(
            listOf<ICodeIntent>(FilesIntent.Cut(src), FilesIntent.Copy(src), FilesIntent.Duplicate(src), FilesIntent.Trash(src)),
            sent,
        )
    }

    @Test fun `a file with nothing to open it in has no Open, and the rest as any other`() {
        assertEquals("Cut", actions(openable = false).first().label)
    }

    @Test fun `many have no Open and no Paste, and act on all, one archive made on Compress's page`() {
        val all = many(src, notes)
        assertEquals(
            listOf("Cut", "Copy", "Move to…", "Copy to…", "Duplicate", "Rename…", "Compress…", "Add to Favorites", "Copy paths", "Properties", "Select", "Delete"),
            all.map { it.label },
        )
        all.single { it.label == "Move to…" }.onClick()
        all.single { it.label == "Select" }.onClick()
        all.single { it.label == "Compress…" }.onClick()
        assertEquals(listOf<FolderPick>(FolderPick.Transfer(listOf(src.held, notes.held), move = true)), picked)
        assertEquals(listOf(listOf(src.location, notes.location)), selected)
        assertEquals(listOf("Compress"), opened)
        assertTrue(sent.isEmpty())
    }

    @Test fun `files alone are shared in one share, and archives alone extracted, here or to a folder`() {
        assertTrue(many(notes, notes.copy(location = "/w/app/b.txt", name = "b.txt")).any { it.label == "Share…" })
        assertTrue(many(src, notes).none { it.label == "Share…" })
        val zip = ActionTarget("/w/app/a.zip", "a.zip", folder = false, runtimePath = "/workspace/app/a.zip")
        val gz = ActionTarget("/w/app/b.tar.gz", "b.tar.gz", folder = false, runtimePath = "/workspace/app/b.tar.gz")
        val archives = many(zip, gz)
        archives.single { it.label == "Extract here" }.onClick()
        archives.single { it.label == "Extract to…" }.onClick()
        assertEquals(listOf<ICodeIntent>(FilesIntent.Extract(zip.location), FilesIntent.Extract(gz.location)), sent)
        assertEquals(listOf<FolderPick>(FolderPick.Extract(listOf(zip.location, gz.location))), picked)
        assertTrue(archives.none { it.label.startsWith("Compress") })
    }

    @Test fun `+ makes a folder, and a file where one can go, and Paste is the paste bar's`() {
        assertEquals(listOf("New folder", "New file"), FileItemActions.creation({}, {}).map { it.label })
        assertEquals(listOf("New folder"), FileItemActions.creation(null, {}).map { it.label })
    }
}
