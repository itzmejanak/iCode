package dev.icode.mobile.data.workspace

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceIndexerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val indexer = WorkspaceIndexer

    @Test
    fun `index lists files breadth first and skips git directory`() {
        val root = temporaryFolder.newFolder("ws")
        File(root, "src/main").mkdirs()
        File(root, ".git").mkdirs()
        File(root, ".git/config").writeText("[core]")
        File(root, "README.md").writeText("# hi")
        File(root, "src/main/App.kt").writeText("fun main() {}")

        val (files, truncated) = indexer.indexFiles(root.path)

        assertFalse(truncated)
        assertEquals(listOf("README.md", "App.kt"), files.map { it.name })
        assertFalse(files.any { it.path.contains(".git") })
    }

    @Test
    fun `search finds matches with case and word options and groups by file`() {
        val root = temporaryFolder.newFolder("ws2")
        File(root, "a.txt").writeText("Alpha alpha\nbeta ALPHA\n")
        File(root, "b.txt").writeText("only beta here\n")

        val caseSensitive = indexer.search(root.path, "alpha", matchCase = true, wholeWord = false, regex = false)
        assertEquals(1, caseSensitive.results.single { it.name == "a.txt" }.matches.size)
        val insensitive = indexer.search(root.path, "alpha", matchCase = false, wholeWord = false, regex = false)
        assertEquals(3, insensitive.results.single { it.name == "a.txt" }.matches.size)
        assertEquals(
            listOf(1 to 0, 1 to 6, 2 to 5),
            insensitive.results.single { it.name == "a.txt" }.matches.map { it.lineNumber to it.column },
        )

        val wholeWord = indexer.search(root.path, "alpha", matchCase = false, wholeWord = true, regex = false)
        assertEquals(3, wholeWord.results.single { it.name == "a.txt" }.matches.size)

        val beta = indexer.search(root.path, "beta", matchCase = true, wholeWord = false, regex = false)
        assertEquals(2, beta.results.size)
        assertEquals(listOf(2), beta.results.single { it.name == "a.txt" }.matches.map { it.lineNumber })
    }

    @Test
    fun `regex search validates pattern and skips binary files`() {
        val root = temporaryFolder.newFolder("ws3")
        File(root, "code.txt").writeText("val x = 1\nval y = 22\n")
        File(root, "blob.bin").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))

        val regex = indexer.search(root.path, "val x = \\d", matchCase = true, wholeWord = false, regex = true)
        assertEquals(1, regex.results.single().matches.size)
        assertEquals("code.txt", regex.results.single().name)
        assertFalse(regex.results.any { it.name == "blob.bin" })

        try {
            indexer.search(root.path, "([unclosed", matchCase = true, wholeWord = false, regex = true)
            throw AssertionError("expected PatternSyntaxException")
        } catch (expected: java.util.regex.PatternSyntaxException) {
            // callers translate this into user feedback
        }
    }

    @Test
    fun `search enforces match limit and reports truncation`() {
        val root = temporaryFolder.newFolder("ws4")
        val big = File(root, "big.txt")
        big.writeText((1..600).joinToString("\n") { "needle $it" })

        val outcome = indexer.search(root.path, "needle", matchCase = true, wholeWord = false, regex = false)

        assertTrue(outcome.truncated)
        assertTrue(outcome.results.single().matches.size < 600)
    }

    @Test
    fun `empty query yields no results instead of scanning everything`() {
        val root = temporaryFolder.newFolder("ws5")
        File(root, "a.txt").writeText("content\n")

        val outcome = indexer.search(root.path, "", matchCase = true, wholeWord = false, regex = false)

        assertTrue(outcome.results.isEmpty())
        assertEquals(0, outcome.searchedFiles)
        assertFalse(outcome.truncated)
    }
}
