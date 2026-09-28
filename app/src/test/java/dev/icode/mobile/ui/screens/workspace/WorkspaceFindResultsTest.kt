package dev.icode.mobile.ui.screens.workspace

import dev.icode.mobile.domain.FileKind
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
        assertTrue(ranked.truncated)
    }

    private fun file(path: String) = IndexedFile(path.substringAfterLast('/'), path, FileKind.Code)
}
