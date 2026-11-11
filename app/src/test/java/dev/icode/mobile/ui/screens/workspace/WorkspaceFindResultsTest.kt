package dev.icode.mobile.ui.screens.workspace

import dev.icode.mobile.domain.IndexedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFindResultsTest {
    @Test fun `quick open ranks exact and name matches before path matches`() {
        val files = listOf(
            file("src/other.kt"),
            file("src/readme-helper.kt"),
            file("readme/docs/guide.kt"),
            file("readme"),
        )

        val ranked = rankFiles(files, "readme")

        assertEquals(listOf("readme", "src/readme-helper.kt", "readme/docs/guide.kt"), ranked.entries.map { it.path })
        assertFalse(ranked.truncated)
    }

    @Test fun `quick open reports its visible result cap`() {
        val files = (1..80).map { file("src/file-$it.kt") }

        val ranked = rankFiles(files, "file")

        assertEquals(50, ranked.entries.size)
        assertEquals(80, ranked.total)
        assertTrue(ranked.truncated)
    }

    @Test fun `the phone's folders iCode keeps a workspace in never match a query`() {
        val root = "/data/data/dev.icode.mobile/files/workspaces/app"
        val files = listOf(IndexedFile("a.kt", "$root/src/a.kt"), IndexedFile("data.json", "$root/data.json"))

        assertEquals(listOf("data.json"), rankFiles(files, "data", root).entries.map { it.name })
        assertEquals(listOf("a.kt"), rankFiles(files, "src", root).entries.map { it.name })
        assertTrue(rankFiles(files, "workspaces", root).entries.isEmpty())
    }

    private fun file(path: String) = IndexedFile(path.substringAfterLast('/'), path)
}
