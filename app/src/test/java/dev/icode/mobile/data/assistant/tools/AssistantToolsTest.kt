package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.search.RipgrepAvailability
import dev.icode.mobile.data.search.RipgrepIndexOutcome
import dev.icode.mobile.data.search.RipgrepSearchOutcome
import dev.icode.mobile.data.search.WorkspaceSearchEngine
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.workspace.WorkspaceFileSource
import dev.icode.mobile.domain.ContentMatch
import dev.icode.mobile.domain.ContentSearchResult
import dev.icode.mobile.domain.IndexedFile
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AssistantToolsTest {
    @get:Rule val temp = TemporaryFolder()

    private val workspaces by lazy { temp.newFolder("workspaces").canonicalFile }
    private val root by lazy { File(workspaces, "app").apply { mkdirs() } }
    private val workspace by lazy { AssistantWorkspace(root, "/workspace/app") }
    private val source by lazy { WorkspaceFileSource(WorkspacePaths(workspaces)) }
    private var unsaved = emptySet<String>()
    private var changes = 0
    private val writer by lazy { WorkspaceWriter(workspace, source, { it in unsaved }) { changes++ } }

    private fun file(path: String, text: String) = File(root, path).apply { parentFile!!.mkdirs(); writeText(text) }
    private fun args(vararg pairs: Pair<String, Any>) = JSONObject(pairs.toMap())

    private fun refused(block: suspend () -> Unit): String = runBlocking {
        try {
            block()
            fail("expected a refusal")
            ""
        } catch (refusal: AssistantToolException) {
            refusal.message!!
        }
    }

    @Test fun `a path is the workspace's, relative or under its runtime path, and nothing leads out`() {
        assertEquals(File(root, "src/a.kt"), workspace.resolve("src/a.kt"))
        assertEquals(File(root, "src/a.kt"), workspace.resolve("/workspace/app/src/a.kt"))
        assertEquals(root, workspace.resolve("."))
        val outside = temp.newFolder("outside")
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())
        listOf("../other", "/etc/passwd", "/workspace/other/a", "~/notes", "escape/x", "src/../../app2").forEach { path ->
            assertTrue(path, refused { workspace.resolve(path) }.contains("outside this workspace"))
        }
    }

    @Test fun `a file is read with numbered lines, a window at a time`() = runBlocking {
        file("notes.md", (1..5).joinToString("\n") { "line $it" })
        val read = ReadFileTool(workspace, source)
        assertEquals("1\tline 1\n2\tline 2\n3\tline 3\n4\tline 4\n5\tline 5", read.run(args("path" to "notes.md")))
        assertEquals("2\tline 2\n3\tline 3\n(lines 2–3 of 5; read on with start_line)", read.run(args("path" to "notes.md", "start_line" to 2, "line_count" to 2)))
        assertEquals("Read notes.md", read.summary(args("path" to "notes.md")))
        assertTrue(refused { read.run(args("path" to "missing.kt")) }.contains("is not a file"))
        assertTrue(refused { read.run(JSONObject()) }.contains("Missing the “path” argument"))
    }

    @Test fun `a write creates the file and its folders, or replaces one, and the workspace refreshes`() = runBlocking {
        val write = WriteFileTool(writer)
        assertEquals("Created src/main/App.kt (2 lines)", write.run(args("path" to "src/main/App.kt", "content" to "fun main() {\n}")))
        assertEquals("fun main() {\n}", File(root, "src/main/App.kt").readText())
        assertEquals("Replaced src/main/App.kt (1 line)", write.run(args("path" to "src/main/App.kt", "content" to "// gone")))
        assertEquals("// gone", File(root, "src/main/App.kt").readText())
        assertEquals(2, changes)
    }

    @Test fun `nothing is written into Git's folder, over a folder, or under the user's unsaved edits`() {
        val write = WriteFileTool(writer)
        File(root, ".git").mkdirs()
        assertTrue(refused { write.run(args("path" to ".git/config", "content" to "x")) }.contains("Git's own"))
        File(root, "src").mkdirs()
        assertTrue(refused { write.run(args("path" to "src", "content" to "x")) }.contains("is a folder"))
        val open = file("open.kt", "mine")
        unsaved = setOf(open.path)
        assertTrue(refused { write.run(args("path" to "open.kt", "content" to "theirs")) }.contains("unsaved changes in open.kt"))
        assertEquals("mine", open.readText())
        assertEquals(0, changes)
    }

    @Test fun `an edit replaces exact text once, or everywhere when asked, and says when it cannot`() = runBlocking {
        val edit = EditFileTool(writer)
        val kotlin = file("a.kt", "val x = 1\nval y = 1\n")
        assertTrue(refused { edit.run(args("path" to "a.kt", "old_text" to "= 1", "new_text" to "= 2")) }.contains("2 times"))
        assertTrue(refused { edit.run(args("path" to "a.kt", "old_text" to "val z", "new_text" to "")) }.contains("is not in a.kt"))
        assertEquals("Edited a.kt", edit.run(args("path" to "a.kt", "old_text" to "val x = 1", "new_text" to "val x = 3")))
        assertEquals("Edited a.kt (2 places)", edit.run(args("path" to "a.kt", "old_text" to "val", "new_text" to "var", "replace_all" to true)))
        assertEquals("var x = 3\nvar y = 1\n", kotlin.readText())
        assertTrue(refused { edit.run(args("path" to "new.kt", "old_text" to "a", "new_text" to "b")) }.contains("does not exist"))
    }

    @Test fun `an edit written with newlines lands in a file that ends its lines with CRLF`() = runBlocking {
        val windows = file("w.txt", "one\r\ntwo\r\nthree\r\n")
        EditFileTool(writer).run(args("path" to "w.txt", "old_text" to "one\ntwo", "new_text" to "1\n2"))
        assertEquals("1\r\n2\r\nthree\r\n", windows.readText())
    }

    @Test fun `a folder lists its own entries, or every file Git sees under it`() = runBlocking {
        file("src/a.kt", "")
        file("README.md", "")
        File(root, ".git").mkdirs()
        val engine = FakeSearch(listOf("src/a.kt", "src/b/c.kt", "README.md"))
        val list = ListFilesTool(workspace, engine)
        assertEquals("src/\nREADME.md", list.run(JSONObject()))
        assertEquals("src/a.kt\nsrc/b/c.kt", list.run(args("path" to "src", "recursive" to true)))
        assertTrue(refused { list.run(args("path" to "README.md")) }.contains("is not a folder"))
    }

    @Test fun `a search answers path, line and text, relative to the workspace`() = runBlocking {
        val engine = FakeSearch(emptyList(), listOf(ContentSearchResult(File(root, "src/a.kt").path, "a.kt", listOf(ContentMatch(3, 4, "  val answer = 42  ")))))
        val search = SearchTextTool(workspace, engine)
        assertEquals("src/a.kt:3: val answer = 42", search.run(args("query" to "answer")))
        assertEquals("No matches", SearchTextTool(workspace, FakeSearch(emptyList())).run(args("query" to "nothing")))
        assertFalse(engine.lastRegex)
    }

    @Test fun `output too long keeps its start and its end`() {
        val text = "a".repeat(50) + "b".repeat(50)
        val kept = bounded(text, 20)
        assertTrue(kept.startsWith("a".repeat(10)) && kept.endsWith("b".repeat(10)))
        assertTrue(kept.contains("80 characters not shown"))
        assertEquals("short", bounded("short", 20))
    }

    private inner class FakeSearch(
        private val files: List<String>,
        private val results: List<ContentSearchResult> = emptyList(),
    ) : WorkspaceSearchEngine {
        var lastRegex = true
        override fun isAvailable() = true
        override suspend fun ensureAvailable(): RipgrepAvailability = RipgrepAvailability.Ready
        override suspend fun index(rootLocation: String) =
            RipgrepIndexOutcome(files.map { IndexedFile(File(it).name, File(rootLocation, it).path) }, truncated = false, warning = null)
        override suspend fun search(rootLocation: String, query: String, matchCase: Boolean, wholeWord: Boolean, regex: Boolean): RipgrepSearchOutcome {
            lastRegex = regex
            return RipgrepSearchOutcome(results, searchedFiles = 1, truncated = false)
        }
    }
}
