package dev.icode.mobile.data.files

import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.MemoryWorkspaceStore
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.files.BrowsedItem
import dev.icode.mobile.domain.files.FavoriteFolder
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.StoragePlace
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Recent and Favorites (docs/explorer-gaps-plan.md §3.14): kept across places, following iCode's own moves. */
class FileShortcutsTest {
    @get:Rule val temp = TemporaryFolder()

    private val stateHolder = ICodeStateHolder()
    private val store = MemoryWorkspaceStore()
    private val workspaces by lazy { temp.newFolder("files", "workspaces").canonicalFile }
    private val phone by lazy { temp.newFolder("emulated", "0").canonicalFile }
    private var now = 0L
    private val shortcuts by lazy { FileShortcuts(stateHolder, store, WorkspacePaths(workspaces)) { ++now } }

    private fun file(parent: File, name: String) = File(parent, name).apply { parentFile!!.mkdirs(); writeText(name) }

    private val recent get() = stateHolder.current.explorer.recent
    private val favorites get() = stateHolder.current.explorer.favorites.map { it.location }

    @Test fun `files opened in a workspace and on phone storage, newest first, as the explorer lists them`() = runBlocking {
        val main = file(workspaces, "app/src/Main.kt")
        val notes = file(phone, "Download/notes.txt")
        shortcuts.opened(main.path)
        shortcuts.handle(FilesIntent.Opened(notes.path))
        assertEquals(
            listOf(
                BrowsedItem("notes.txt", notes.path, notes.path, StoragePlace.Shared, folder = false, bytes = notes.length(), changedAt = 2),
                BrowsedItem("Main.kt", main.path, "/workspace/app/src/Main.kt", StoragePlace.Private, folder = false, bytes = main.length(), changedAt = 1),
            ),
            recent,
        )
        // A save puts it on top again, once.
        shortcuts.opened(main.path)
        assertEquals(listOf(main.path, notes.path), recent.map { it.location })
    }

    @Test fun `a file gone is kept, not shown, and shown again once it is back`() = runBlocking {
        val main = file(workspaces, "app/Main.kt")
        shortcuts.opened(main.path)
        main.delete()
        shortcuts.publish()
        assertEquals(emptyList<BrowsedItem>(), recent)
        file(workspaces, "app/Main.kt")
        shortcuts.publish()
        assertEquals(listOf(main.path), recent.map { it.location })
    }

    @Test fun `folders pinned in order, once each, and let go`() = runBlocking {
        val src = File(workspaces, "app/src").apply { mkdirs() }
        val download = File(phone, "Download").apply { mkdirs() }
        shortcuts.handle(FilesIntent.Favorite(src.path, pinned = true))
        shortcuts.handle(FilesIntent.Favorite(download.path, pinned = true))
        shortcuts.handle(FilesIntent.Favorite(src.path, pinned = true))
        assertEquals(listOf(download.path, src.path), favorites)
        shortcuts.handle(FilesIntent.Favorite(download.path, pinned = false))
        assertEquals(listOf(src.path), favorites)
    }

    @Test fun `a rename above them carries both along, and a delete leaves them where they were`() = runBlocking {
        val app = File(workspaces, "app")
        val main = file(app, "src/Main.kt")
        shortcuts.opened(main.path)
        shortcuts.handle(FilesIntent.Favorite(File(app, "src").path, pinned = true))
        val renamed = File(workspaces, "tool")
        app.renameTo(renamed)
        shortcuts.follow(listOf(FileStep.Moved(app.path, renamed.path)))
        assertEquals(listOf(File(renamed, "src/Main.kt").path), recent.map { it.location })
        assertEquals("/workspace/tool/src/Main.kt", recent.single().path)
        assertEquals(listOf(File(renamed, "src").path), favorites)
        assertEquals(listOf(FavoriteFolder("src", File(renamed, "src").path, "/workspace/tool/src")), stateHolder.current.explorer.favorites)
        shortcuts.follow(listOf(FileStep.Trashed("id", File(renamed, "src").path)))
        assertEquals(listOf(File(renamed, "src").path), store.loadFavorites())
        assertEquals(listOf(File(renamed, "src/Main.kt").path), store.loadRecentDocuments().map { it.location })
    }
}
