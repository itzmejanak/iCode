package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FilesRulesTest {
    private val src = BrowsedItem("src", "/w/app/src", folder = true, changedAt = 10, entries = 4)
    private val docs = BrowsedItem("Docs", "/w/app/Docs", folder = true, changedAt = 30, entries = 1)
    private val readme = BrowsedItem("README.md", "/w/app/README.md", folder = false, bytes = 900, changedAt = 50)
    private val build = BrowsedItem("build.gradle", "/w/app/build.gradle", folder = false, bytes = 4_000, changedAt = 20)
    private val all = listOf(readme, src, build, docs)

    @Test fun `folders come first, then by name ignoring case, the newest, or the largest`() {
        assertEquals(listOf(docs, src, build, readme), FilesRules.sorted(all, FilesSort.Name, emptyMap()))
        assertEquals(listOf(docs, src, readme, build), FilesRules.sorted(all, FilesSort.Date, emptyMap()))
        val sizes = mapOf(src.location to StorageMeasurement(10_000))
        assertEquals(listOf(src, docs, build, readme), FilesRules.sorted(all, FilesSort.Size, sizes))
    }

    @Test fun `search finds by name, and a blank one finds all`() {
        assertEquals(listOf(readme), FilesRules.matching(all, "read"))
        assertEquals(all, FilesRules.matching(all, " "))
    }

    @Test fun `a folder says what it holds, and the breadcrumb starts at workspace`() {
        assertEquals("2 folders, 2 files", FilesRules.contents(all))
        val state = FilesBrowserState("/data/w", "/data/w/tools/cli")
        assertEquals(listOf("workspace", "tools", "cli"), state.crumbs.map { it.name })
        assertEquals("/data/w/tools", state.crumbs[1].path)
    }

    @Test fun `Back goes up a folder in both browsers, and leaves only at their root`() {
        assertEquals("/data/w/tools", FilesBrowserState("/data/w", "/data/w/tools/cli").parent)
        assertEquals(null, FilesBrowserState("/data/w", "/data/w").parent)
        val sdcard = "/storage/emulated/0"
        assertEquals(sdcard, ImportBrowserState(access = true, rootPath = sdcard, currentPath = "$sdcard/Download").parent)
        assertEquals(null, ImportBrowserState(access = true, rootPath = sdcard, currentPath = sdcard).parent)
    }
}
