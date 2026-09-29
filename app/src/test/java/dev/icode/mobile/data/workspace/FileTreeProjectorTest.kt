package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.FileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTreeProjectorTest {
    @Test
    fun `only expanded descendants are visible at the correct depth`() {
        val children = mapOf(
            "root" to listOf(DocumentNode("src", "src", FileKind.Folder), DocumentNode("README.md", "readme", FileKind.File)),
            "src" to listOf(DocumentNode("main", "main", FileKind.Folder), DocumentNode("App.kt", "app", FileKind.File)),
            "main" to listOf(DocumentNode("Hidden.kt", "hidden", FileKind.File)),
        )

        val files = projectVisibleFiles("root", children, expandedLocations = setOf("src"))

        assertEquals(listOf("src", "main", "app", "readme"), files.map { it.path })
        assertEquals(listOf(0, 1, 1, 0), files.map { it.depth })
        assertTrue(files.first().expanded)
        assertFalse(files[1].expanded)
    }

    @Test
    fun `projection terminates when a provider returns a cycle`() {
        val children = mapOf(
            "root" to listOf(DocumentNode("loop", "loop", FileKind.Folder)),
            "loop" to listOf(DocumentNode("root", "root", FileKind.Folder)),
        )

        val files = projectVisibleFiles("root", children, expandedLocations = setOf("loop", "root"))

        assertEquals(2, files.size)
    }
}
