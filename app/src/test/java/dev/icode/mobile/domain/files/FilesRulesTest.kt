package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.StorageMeasurement
import org.junit.Assert.assertEquals
import org.junit.Test

class FilesRulesTest {
    private val src = BrowsedItem("src", "/w/app/src", "/workspace/app/src", StoragePlace.Private, folder = true, changedAt = 10, entries = 4)
    private val docs = BrowsedItem("Docs", "/w/app/Docs", "/workspace/app/Docs", StoragePlace.Private, folder = true, changedAt = 30, entries = 1)
    private val readme = BrowsedItem("README.md", "/w/app/README.md", "/workspace/app/README.md", StoragePlace.Private, folder = false, bytes = 900, changedAt = 50)
    private val build = BrowsedItem("build.gradle", "/w/app/build.gradle", "/workspace/app/build.gradle", StoragePlace.Private, folder = false, bytes = 4_000, changedAt = 20)
    private val all = listOf(readme, src, build, docs)

    @Test fun `folders come first, then by name ignoring case, the newest, or the largest`() {
        assertEquals(listOf(docs, src, build, readme), FilesRules.sorted(all, FilesSort.Name, descending = false, emptyMap()))
        assertEquals(listOf(docs, src, readme, build), FilesRules.sorted(all, FilesSort.Date, descending = true, emptyMap()))
        val sizes = mapOf(src.location to StorageMeasurement(10_000))
        assertEquals(listOf(src, docs, build, readme), FilesRules.sorted(all, FilesSort.Size, descending = true, sizes))
    }

    @Test fun `descending flips each order, folders still first and the unmeasured still last`() {
        assertEquals(listOf(src, docs, readme, build), FilesRules.sorted(all, FilesSort.Name, descending = true, emptyMap()))
        assertEquals(listOf(src, docs, build, readme), FilesRules.sorted(all, FilesSort.Date, descending = false, emptyMap()))
        val sizes = mapOf(src.location to StorageMeasurement(10_000))
        assertEquals(listOf(src, docs, readme, build), FilesRules.sorted(all, FilesSort.Size, descending = false, sizes))
    }

    @Test fun `by type, files of one extension come together, in groups named by it`() {
        val notes = BrowsedItem("notes.md", "/w/app/notes.md", "/workspace/app/notes.md", StoragePlace.Private, folder = false, bytes = 10, changedAt = 1)
        val makefile = BrowsedItem("Makefile", "/w/app/Makefile", "/workspace/app/Makefile", StoragePlace.Private, folder = false, bytes = 10, changedAt = 1)
        val sorted = FilesRules.sorted(all + notes + makefile, FilesSort.Type, descending = false, emptyMap())
        assertEquals(listOf(docs, src, makefile, build, notes, readme), sorted)
        assertEquals(listOf("Folders", "Folders", "No extension", ".gradle", ".md", ".md"), sorted.map(FilesRules::typeGroup))
        assertEquals(listOf("Folders · 2", "No extension · 1", ".gradle · 1", ".md · 2"), FilesRules.grouped(sorted, FilesSort.Type).map { it.label })
        assertEquals(listOf(RowGroup(null, sorted)), FilesRules.grouped(sorted, FilesSort.Name))
        assertEquals("", FilesRules.extension(".gitignore"))
        assertEquals("gz", FilesRules.extension("site.tar.gz"))
    }

    @Test fun `a view mixes folders in, and each row says the folder it is in with its size`() {
        val trashed = src.copy(location = "t1", bytes = 2048, entries = null)
        assertEquals(listOf(readme, build, trashed), FilesRules.sorted(listOf(build, trashed, readme), FilesSort.Date, descending = true, emptyMap(), foldersFirst = false))
        assertEquals("/workspace/app · 900 B", FilesRules.gatheredDetail(readme))
        assertEquals("/workspace/app · 2.0 KB", FilesRules.gatheredDetail(trashed))
        assertEquals("/ · 900 B", FilesRules.gatheredDetail(readme.copy(path = "/README.md")))
        assertEquals(2048L, FilesRules.sizeOf(trashed, mapOf("t1" to StorageMeasurement(1))))
        assertEquals(10_000L, FilesRules.sizeOf(src, mapOf(src.location to StorageMeasurement(10_000))))
    }

    @Test fun `each sort reads its own way first`() {
        assertEquals(listOf(false, true, true, false), FilesSort.entries.map { it.descendingFirst })
    }

    @Test fun `search finds by name, and a blank one finds all`() {
        assertEquals(listOf(readme), FilesRules.matching(all, "read"))
        assertEquals(all, FilesRules.matching(all, " "))
    }

    @Test fun `a row says what a folder holds — a workspace its branch too — or a file's size`() {
        assertEquals("4 items", FilesRules.detail(src))
        assertEquals("1 item", FilesRules.detail(docs))
        assertEquals("900 B", FilesRules.detail(readme))
        assertEquals("Empty", FilesRules.detail(src.copy(entries = 0)))
        assertEquals("Folder", FilesRules.detail(src.copy(entries = null)))
        assertEquals("4 items · main", FilesRules.detail(src.copy(branch = "main")))
    }

    @Test fun `the title is the folder and under it where that folder is, at the top the place and its path`() {
        val private = FilesBrowserState(StoragePlace.Private, "/data/w", "/data/w/tools/cli")
        assertEquals("cli", private.name)
        assertEquals("/workspace/tools", private.where)
        assertEquals("/workspace/tools/cli/README.md", private.runtimePathOf("/data/w/tools/cli/README.md"))
        assertEquals("/data/w/tools", private.workspace)
        val top = FilesBrowserState(StoragePlace.Private, "/data/w", "/data/w")
        assertEquals("Private", top.name)
        assertEquals("/workspace", top.where)
        assertEquals(null, top.workspace)
        val sdcard = "/storage/emulated/0"
        val shared = FilesBrowserState(StoragePlace.Shared, sdcard, "$sdcard/Download")
        assertEquals("Download", shared.name)
        assertEquals(sdcard, shared.where)
        assertEquals("$sdcard/Download/a.txt", shared.runtimePathOf("$sdcard/Download/a.txt"))
        assertEquals(null, shared.workspace)
        assertEquals("Shared", FilesBrowserState(StoragePlace.Shared, sdcard, sdcard).name)
    }

    @Test fun `up goes a folder up in both places, and only at their top is there none`() {
        assertEquals("/data/w/tools", FilesBrowserState(StoragePlace.Private, "/data/w", "/data/w/tools/cli").parent)
        assertEquals(null, FilesBrowserState(StoragePlace.Private, "/data/w", "/data/w").parent)
        val sdcard = "/storage/emulated/0"
        assertEquals(sdcard, FilesBrowserState(StoragePlace.Shared, sdcard, "$sdcard/Download").parent)
        assertEquals(null, FilesBrowserState(StoragePlace.Shared, sdcard, sdcard).parent)
    }

    @Test fun `the breadcrumb is the folder's path in segments, from the place's top as the runtime names it`() {
        val private = FilesBrowserState(StoragePlace.Private, "/data/files/workspaces", "/data/files/workspaces/RevPlayer/src")
        assertEquals(
            listOf(Crumb("/workspace", "/data/files/workspaces"), Crumb("RevPlayer", "/data/files/workspaces/RevPlayer"), Crumb("src", "/data/files/workspaces/RevPlayer/src")),
            private.crumbs,
        )
        val shared = FilesBrowserState(StoragePlace.Shared, "/storage/emulated/0", "/storage/emulated/0")
        assertEquals(listOf(Crumb("/storage/emulated/0", "/storage/emulated/0")), shared.crumbs)
    }
}
