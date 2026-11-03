package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.search.RipgrepAvailability
import dev.icode.mobile.data.search.RipgrepIndexOutcome
import dev.icode.mobile.data.search.RipgrepSearchOutcome
import dev.icode.mobile.data.search.WorkspaceSearchEngine
import dev.icode.mobile.data.workspace.WorkspaceFileSource
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.ContentMatch
import dev.icode.mobile.domain.ContentSearchResult
import dev.icode.mobile.domain.IndexedFile
import dev.icode.mobile.domain.LineChanges
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

    private val runtime by lazy { TestRuntime(temp.root) }
    private val root by lazy { runtime.root }
    private val workspace by lazy { runtime.workspace }
    private val source by lazy { WorkspaceFileSource(runtime.paths) }
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

    @Test fun `a path is the runtime's - relative from the workspace's top, absolute as written, ~ as root's home - its links followed as PRoot does`() {
        assertEquals(File(root, "src/a.kt"), workspace.resolve("src/a.kt"))
        assertEquals(File(root, "src/a.kt"), workspace.resolve("/workspace/app/src/a.kt"))
        assertEquals(root, workspace.resolve("."))
        assertEquals(File(runtime.workspaces, "other/a"), workspace.resolve("../other/a"))
        assertEquals(File(runtime.workspaces, "other/a"), workspace.resolve("/workspace/other/a"))
        assertEquals(File(runtime.rootfs, "etc/passwd"), workspace.resolve("/etc/passwd"))
        assertEquals(File(runtime.rootfs, "root/notes"), workspace.resolve("~/notes"))
        assertEquals(File(runtime.rootfs, "root"), workspace.resolve("~"))
        // A link made in the runtime names a runtime path.
        Files.createSymbolicLink(File(root, "conf").toPath(), File("/etc").toPath())
        assertEquals(File(runtime.rootfs, "etc/hosts"), workspace.resolve("conf/hosts"))
        // Each told as the model writes it back.
        assertEquals("src/a.kt", workspace.shown(File(root, "src/a.kt")))
        assertEquals(".", workspace.shown(root))
        assertEquals("/etc/passwd", workspace.shown(File(runtime.rootfs, "etc/passwd")))
        assertEquals("/workspace/other/a", workspace.shown(File(runtime.workspaces, "other/a")))
    }

    @Test fun `phone storage is in the runtime where Android has it and at sdcard, outside the workspace, read only with All Files Access`() = runBlocking {
        val shared = runtime.shared
        File(shared, "Download/notes.txt").apply { parentFile!!.mkdirs(); writeText("from the phone\n") }
        assertEquals(File(shared, "Download/notes.txt"), workspace.resolve("/sdcard/Download/notes.txt"))
        assertEquals(File(shared, "Download/notes.txt"), workspace.resolve("${shared.path}/Download/notes.txt"))
        assertEquals("${shared.path}/Download/notes.txt", workspace.shown(File(shared, "Download/notes.txt")))
        assertEquals(listOf("${shared.path}/Download"), workspace.outside(listOf(File(shared, "Download"))))
        assertTrue(ReadFileTool(workspace, source).run(args("path" to "/sdcard/Download/notes.txt")).contains("from the phone"))
        val withoutAccess = AssistantWorkspace(root, runtime.paths, storageAccess = { false })
        assertEquals(AssistantWorkspace.NO_STORAGE_ACCESS, refused { ReadFileTool(withoutAccess, source).run(args("path" to "/sdcard/Download/notes.txt")) })
        assertEquals(emptyList<File>(), ReadFileTool(withoutAccess, source).reach(args("path" to "/sdcard/Download/notes.txt")))
        // Elsewhere, nothing changes without it.
        assertEquals(File(root, "a.kt"), withoutAccess.resolve("a.kt"))
    }

    @Test fun `the phone's chats work from root's home, named Phone, with no Run tab`() {
        val phone = AssistantWorkspace.phone(runtime.paths) { true }
        assertEquals("/root", phone.guestPath)
        assertEquals("Phone", phone.name)
        assertEquals(AssistantPlace.PHONE, phone.place)
        assertEquals(File(runtime.rootfs, "root/notes.md"), phone.resolve("notes.md"))
        assertEquals(listOf("/workspace/app"), phone.outside(listOf(File(runtime.rootfs, "root/x"), root)))
        assertEquals(root.path, workspace.place)
    }

    @Test fun `each tool reaches the path it is given, the workspace's top by default, and the gate sees what is outside`() {
        val list = ListFilesTool(workspace, FakeSearch(emptyList()))
        assertEquals(listOf(root), list.reach(JSONObject()))
        assertEquals(listOf(File(runtime.rootfs, "etc")), list.reach(args("path" to "/etc")))
        assertEquals(listOf(File(runtime.workspaces, "other/x")), WriteFileTool(writer).reach(args("path" to "../other/x")))
        assertEquals(emptyList<File>(), WriteFileTool(writer).reach(JSONObject()))
        assertEquals(listOf("/etc", "/workspace/other/x"), workspace.outside(listOf(root, File(root, "src"), File(runtime.rootfs, "etc"), File(runtime.workspaces, "other/x"))))
    }

    @Test fun `Allow in Chat opens another workspace whole, a repository at its top, else the folder`() {
        File(runtime.rootfs, "root/projects/api/.git").mkdirs()
        File(runtime.rootfs, "root/projects/api/lib").mkdirs()
        File(runtime.rootfs, "etc").mkdirs()
        assertEquals("/workspace/other", workspace.rootOf("/workspace/other/src/App.tsx"))
        assertEquals("/root/projects/api", workspace.rootOf("/root/projects/api/lib/x.py"))
        assertEquals("/etc", workspace.rootOf("/etc/hosts"))
        assertEquals("/etc", workspace.rootOf("/etc"))
        assertEquals(null, workspace.rootOf("/"))
    }

    @Test fun `a file anywhere in the runtime is read, and what is wrong is said as the system says it`() = runBlocking {
        File(runtime.rootfs, "root/x").apply { parentFile!!.mkdirs(); writeText("home") }
        File(runtime.workspaces, "other/y").apply { parentFile!!.mkdirs(); writeText("other") }
        val read = ReadFileTool(workspace, source)
        assertEquals("1\thome", read.run(args("path" to "/root/x")))
        assertEquals("1\thome", read.run(args("path" to "~/x")))
        assertEquals("1\tother", read.run(args("path" to "/workspace/other/y")))
        // The page opens this workspace's files only.
        assertEquals(null, read.file(args("path" to "/root/x")))
        assertEquals("missing.kt does not exist", refused { read.run(args("path" to "missing.kt")) })
        File(root, "src").mkdirs()
        assertEquals("src is a folder", refused { read.run(args("path" to "src")) })
        file("README.md", "")
        assertEquals("README.md is not a folder", refused { ListFilesTool(workspace, FakeSearch(emptyList())).run(args("path" to "README.md")) })
        assertEquals("/nowhere does not exist", refused { SearchTextTool(workspace, FakeSearch(emptyList())).run(args("query" to "x", "path" to "/nowhere")) })
    }

    @Test fun `a file is read with numbered lines, a window at a time`() = runBlocking {
        file("notes.md", (1..5).joinToString("\n") { "line $it" })
        val read = ReadFileTool(workspace, source)
        assertEquals("1\tline 1\n2\tline 2\n3\tline 3\n4\tline 4\n5\tline 5", read.run(args("path" to "notes.md")))
        assertEquals("2\tline 2\n3\tline 3\n(lines 2–3 of 5; read on with start_line)", read.run(args("path" to "notes.md", "start_line" to 2, "line_count" to 2)))
        assertEquals("Read notes.md", read.summary(args("path" to "notes.md")))
        assertTrue(refused { read.run(JSONObject()) }.contains("Missing the “path” argument"))
    }

    @Test fun `a write creates the file and its folders, or replaces one, and the workspace refreshes`() = runBlocking {
        val write = WriteFileTool(writer)
        assertEquals("Created src/main/App.kt: +2 lines", write.run(args("path" to "src/main/App.kt", "content" to "fun main() {\n}")))
        assertEquals(LineChanges(2, 0), write.lastChanges)
        assertEquals("src/main/App.kt", write.file(args("path" to "/workspace/app/src/main/App.kt")))
        assertEquals("fun main() {\n}", File(root, "src/main/App.kt").readText())
        assertEquals("Replaced src/main/App.kt: +1 −2 lines", write.run(args("path" to "src/main/App.kt", "content" to "// gone")))
        assertEquals(LineChanges(1, 2), write.lastChanges)
        assertEquals("Replaced src/main/App.kt: no lines changed", write.run(args("path" to "src/main/App.kt", "content" to "// gone")))
        assertEquals("// gone", File(root, "src/main/App.kt").readText())
        assertEquals(3, changes)
    }

    @Test fun `a write reaches any file in the runtime, Git's folder included, never over a folder or the user's unsaved edits`() = runBlocking {
        val write = WriteFileTool(writer)
        assertEquals("Created /root/x: +1 lines", write.run(args("path" to "/root/x", "content" to "x")))
        assertEquals("x", File(runtime.rootfs, "root/x").readText())
        assertEquals("Created /workspace/other/y: +1 lines", write.run(args("path" to "/workspace/other/y", "content" to "y")))
        assertEquals("y", File(runtime.workspaces, "other/y").readText())
        assertEquals(null, write.file(args("path" to "/root/x")))
        File(root, ".git").mkdirs()
        assertEquals("Created .git/info/exclude: +1 lines", write.run(args("path" to ".git/info/exclude", "content" to "build/")))
        assertEquals(".git/info/exclude", write.file(args("path" to ".git/info/exclude")))
        File(root, "src").mkdirs()
        assertEquals("src is a folder", refused { write.run(args("path" to "src", "content" to "x")) })
        val open = file("open.kt", "mine")
        unsaved = setOf(open.path)
        assertTrue(refused { write.run(args("path" to "open.kt", "content" to "theirs")) }.contains("unsaved changes in open.kt"))
        assertEquals("mine", open.readText())
        assertEquals(3, changes)
    }

    @Test fun `an edit replaces exact text once, or everywhere when asked, and says when it cannot`() = runBlocking {
        val edit = EditFileTool(writer)
        val kotlin = file("a.kt", "val x = 1\nval y = 1\n")
        assertTrue(refused { edit.run(args("path" to "a.kt", "old_text" to "= 1", "new_text" to "= 2")) }.contains("2 times"))
        assertTrue(refused { edit.run(args("path" to "a.kt", "old_text" to "val z", "new_text" to "")) }.contains("is not in a.kt"))
        assertEquals("Edited a.kt: +1 −1 lines", edit.run(args("path" to "a.kt", "old_text" to "val x = 1", "new_text" to "val x = 3")))
        assertEquals(LineChanges(1, 1), edit.lastChanges)
        assertEquals("Edited a.kt in 2 places: +2 −2 lines", edit.run(args("path" to "a.kt", "old_text" to "val", "new_text" to "var", "replace_all" to true)))
        assertEquals("a.kt", edit.file(args("path" to "a.kt")))
        assertEquals(null, edit.file(args("path" to "../elsewhere.kt")))
        File(runtime.rootfs, "etc").mkdirs()
        val hosts = File(runtime.rootfs, "etc/hosts").apply { writeText("127.0.0.1 localhost\n") }
        assertEquals("Edited /etc/hosts: +1 −1 lines", edit.run(args("path" to "/etc/hosts", "old_text" to "localhost", "new_text" to "localhost phone")))
        assertEquals("127.0.0.1 localhost phone\n", hosts.readText())
        assertEquals("var x = 3\nvar y = 1\n", kotlin.readText())
        assertTrue(refused { edit.run(args("path" to "new.kt", "old_text" to "a", "new_text" to "b")) }.contains("does not exist"))
    }

    @Test fun `an edit written with newlines lands in a file that ends its lines with CRLF`() = runBlocking {
        val windows = file("w.txt", "one\r\ntwo\r\nthree\r\n")
        EditFileTool(writer).run(args("path" to "w.txt", "old_text" to "one\ntwo", "new_text" to "1\n2"))
        assertEquals("1\r\n2\r\nthree\r\n", windows.readText())
    }

    @Test fun `a folder lists its own entries, Git's folder too, or every file under it, anywhere in the runtime`() = runBlocking {
        file("src/a.kt", "")
        file("README.md", "")
        File(root, ".git").mkdirs()
        val engine = FakeSearch(listOf("a.kt", "b/c.kt"))
        val list = ListFilesTool(workspace, engine)
        assertEquals(".git/\nsrc/\nREADME.md", list.run(JSONObject()))
        assertEquals("src/a.kt\nsrc/b/c.kt", list.run(args("path" to "src", "recursive" to true)))
        assertEquals(File(root, "src").path to false, engine.lastRoot to engine.lastIgnored)
        File(runtime.rootfs, "usr/lib").mkdirs()
        assertEquals("/usr/lib/a.kt\n/usr/lib/b/c.kt", list.run(args("path" to "/usr/lib", "recursive" to true, "ignored" to true)))
        assertEquals(File(runtime.rootfs, "usr/lib").path to true, engine.lastRoot to engine.lastIgnored)
    }

    @Test fun `a search answers path, line and text, from the workspace's top or any folder, ignored files when asked`() = runBlocking {
        val engine = FakeSearch(emptyList(), listOf(ContentSearchResult(File(root, "src/a.kt").path, "a.kt", listOf(ContentMatch(3, 4, "  val answer = 42  ")))))
        val search = SearchTextTool(workspace, engine)
        assertEquals("src/a.kt:3: val answer = 42", search.run(args("query" to "answer")))
        assertEquals(root.path to false, engine.lastRoot to engine.lastIgnored)
        assertFalse(engine.lastRegex)
        File(runtime.rootfs, "etc").mkdirs()
        val etc = FakeSearch(emptyList(), listOf(ContentSearchResult(File(runtime.rootfs, "etc/hosts").path, "hosts", listOf(ContentMatch(1, 0, "127.0.0.1 localhost")))))
        assertEquals("/etc/hosts:1: 127.0.0.1 localhost", SearchTextTool(workspace, etc).run(args("query" to "localhost", "path" to "/etc", "ignored" to true)))
        assertEquals(File(runtime.rootfs, "etc").path to true, etc.lastRoot to etc.lastIgnored)
        assertEquals("No matches", SearchTextTool(workspace, FakeSearch(emptyList())).run(args("query" to "nothing")))
    }

    @Test fun `output too long keeps its start and its end`() {
        val text = "a".repeat(50) + "b".repeat(50)
        val kept = bounded(text, 20)
        assertTrue(kept.startsWith("a".repeat(10)) && kept.endsWith("b".repeat(10)))
        assertTrue(kept.contains("80 characters not shown"))
        assertEquals("short", bounded("short", 20))
    }

    private inner class FakeSearch(
        /** Each file under the folder indexed, from that folder. */
        private val files: List<String>,
        private val results: List<ContentSearchResult> = emptyList(),
    ) : WorkspaceSearchEngine {
        var lastRegex = true
        var lastRoot: String? = null
        var lastIgnored: Boolean? = null
        override fun isAvailable() = true
        override suspend fun ensureAvailable(): RipgrepAvailability = RipgrepAvailability.Ready
        override suspend fun index(rootLocation: String, ignored: Boolean): RipgrepIndexOutcome {
            lastRoot = rootLocation
            lastIgnored = ignored
            return RipgrepIndexOutcome(files.map { IndexedFile(File(it).name, File(rootLocation, it).path) }, truncated = false, warning = null)
        }
        override suspend fun search(rootLocation: String, query: String, matchCase: Boolean, wholeWord: Boolean, regex: Boolean, ignored: Boolean): RipgrepSearchOutcome {
            lastRegex = regex
            lastRoot = rootLocation
            lastIgnored = ignored
            return RipgrepSearchOutcome(results, searchedFiles = 1, truncated = false)
        }
    }
}
