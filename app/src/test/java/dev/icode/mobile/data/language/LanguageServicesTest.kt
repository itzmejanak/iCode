package dev.icode.mobile.data.language

import dev.icode.mobile.data.assistant.tools.TestRuntime
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceFileSource
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.Severity
import dev.icode.mobile.domain.TextEncoding
import dev.icode.mobile.domain.TextPosition
import java.io.File
import java.nio.file.Files
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The owner end to end over in-process servers: configuration, lazy start, documents, problems, the assistant's tools. */
class LanguageServicesTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtime = TestRuntime(Files.createTempDirectory("services").toFile())
    private val stateHolder = ICodeStateHolder()
    private val programs = FakePrograms()
    private val saved = Collections.synchronizedList(mutableListOf<List<LanguageServiceConfiguration>>())
    private val store = object : LanguageServiceSaves {
        override fun load() = emptyList<LanguageServiceConfiguration>()
        override fun save(services: List<LanguageServiceConfiguration>) { saved += services }
    }
    private val edited = Collections.synchronizedMap(mutableMapOf<String, String>())
    private val hosts = LanguageHosts(WorkspaceFileSource(runtime.paths), { location, text -> editOpen(location, text) }, {}, { 4 to true })
    private val services = LanguageServices(store, stateHolder, scope, programs, LspUris(runtime.paths), { emptyMap() }, hosts) { runtime.root.path }
    private val file = File(runtime.root, "main.py").apply { writeText("print(1)\n") }

    private fun editOpen(location: String, text: String) {
        edited[location] = text
        stateHolder.update { state -> state.copy(openDocuments = state.openDocuments.map { if (it.location == location) it.copy(text = text) else it }) }
    }

    private fun open(text: String = "print(1)\n") = stateHolder.update {
        it.copy(activeWorkspaceLocation = runtime.root.path, openDocuments = listOf(OpenDocument("main.py", file.path, text, TextEncoding.Utf8, LineEnding.Lf, false, false)))
    }

    private fun type(text: String) = stateHolder.update { state -> state.copy(openDocuments = state.openDocuments.map { it.copy(text = text) }) }

    private suspend fun until(what: String, condition: () -> Boolean) {
        withTimeout(10_000) { while (!condition()) delay(10) }
        assertTrue(what, condition())
    }

    private suspend fun python(): String = services.save(LanguageServiceConfiguration("", "Python", "pylsp", languages = "python")).getOrThrow().id

    @After
    fun stop() = scope.cancel()

    @Test
    fun `a saved service starts when a file it serves opens, and follows it as it is typed and closed`() = runBlocking {
        services.start()
        python()
        assertEquals(1, saved.size)
        open()
        until("started") { services.pool.ready(saved.last().single().id) != null }
        val server = programs.started.single()
        until("opened") { server.sent("textDocument/didOpen").isNotEmpty() }
        val opened = server.sent("textDocument/didOpen").single()["params"]!!.jsonObject["textDocument"]!!.jsonObject
        assertEquals("python", opened["languageId"]!!.jsonPrimitive.content)
        assertEquals("file:///workspace/app/main.py", opened["uri"]!!.jsonPrimitive.content)
        type("print(12)\n")
        type("print(123)\n")
        until("changed") { server.sent("textDocument/didChange").isNotEmpty() }
        delay(LanguageDocumentSync.MAX_WAIT_MILLIS)
        // Two keystrokes inside the quiet time: one change, the last text's.
        val changes = server.sent("textDocument/didChange")
        assertEquals(1, changes.size)
        assertEquals(2, changes.single()["params"]!!.jsonObject["textDocument"]!!.jsonObject["version"]!!.jsonPrimitive.int)
        stateHolder.update { it.copy(openDocuments = emptyList()) }
        until("closed") { server.sent("textDocument/didClose").isNotEmpty() }
        assertEquals(LanguageServiceStatus.Running, services.rows().single().status)
    }

    @Test
    fun `problems a server publishes reach the snapshot, and the assistant's check waits for the report on the text now`() = runBlocking {
        programs.hook = { server, message ->
            val method = message["method"]?.jsonPrimitive?.content
            if (method == "textDocument/didOpen" || method == "textDocument/didChange") {
                val params = message["params"]!!.jsonObject
                val document = params["textDocument"]!!.jsonObject
                val version = document["version"]!!.jsonPrimitive.int
                server.notifyClient("textDocument/publishDiagnostics", """{"uri":${document["uri"]},"version":$version,"diagnostics":[{"range":{"start":{"line":0,"character":0},"end":{"line":0,"character":5}},"severity":1,"message":"bad $version","source":"fake"}]}""")
            }
        }
        services.start()
        python()
        val tools = languageServiceTools(services, runtime.workspace).associateBy { it.name }
        // The file is not open in the editor: the check opens it at the server from disk.
        stateHolder.update { it.copy(activeWorkspaceLocation = runtime.root.path) }
        val answer = tools.getValue("language_diagnostics").run(JSONObject().put("path", "main.py").put("wait_seconds", 10))
        assertEquals("error 1:1: bad 1 (fake)", answer)
        until("released") { programs.started.single().sent("textDocument/didClose").isNotEmpty() }
        open()
        until("in the snapshot") { stateHolder.current.languageDiagnostics.any { it.message.startsWith("bad") } }
        val shown = stateHolder.current.languageDiagnostics.first()
        assertEquals(Severity.Error, shown.severity)
        assertEquals("main.py", shown.file)
    }

    @Test
    fun `the assistant's tools save through the owner, always asked, and find definitions there`() = runBlocking {
        programs.answers["textDocument/definition"] = """[{"uri":"file:///workspace/app/main.py","range":{"start":{"line":0,"character":0},"end":{"line":0,"character":5}}}]"""
        services.start()
        val tools = languageServiceTools(services, runtime.workspace).associateBy { it.name }
        val save = tools.getValue("save_language_service")
        assertTrue(save.asksAlways)
        assertTrue(tools.getValue("remove_language_service").asksAlways)
        save.run(JSONObject().put("preset", "pylsp"))
        assertEquals("pylsp", saved.last().single().command)
        assertTrue(tools.getValue("list_language_services").run(JSONObject()).contains("Python (pylsp)"))
        stateHolder.update { it.copy(activeWorkspaceLocation = runtime.root.path) }
        val found = tools.getValue("find_definition").run(JSONObject().put("path", "main.py").put("line", 1).put("column", 1))
        assertEquals("main.py:1:1  print(1)", found)
    }

    @Test
    fun `formatting applies the server's edits to the open file as typing would`() = runBlocking {
        programs.answers["textDocument/formatting"] = """[{"range":{"start":{"line":0,"character":5},"end":{"line":0,"character":5}},"newText":" "}]"""
        val capable = FakePrograms("""{"textDocumentSync":{"openClose":true,"change":2},"documentFormattingProvider":true}""").also { it.answers.putAll(programs.answers) }
        val owner = LanguageServices(store, stateHolder, scope, capable, LspUris(runtime.paths), { emptyMap() }, hosts) { runtime.root.path }
        owner.start()
        owner.save(LanguageServiceConfiguration("", "Python", "pylsp", languages = "python")).getOrThrow()
        open()
        until("opened") { capable.started.firstOrNull()?.sent("textDocument/didOpen")?.isNotEmpty() == true }
        assertEquals(1, owner.features.format(file.path).getOrThrow())
        assertEquals("print (1)\n", edited[file.path])
        val options = capable.started.single().sent("textDocument/formatting").single()["params"]!!.jsonObject["options"]!!.jsonObject
        assertEquals(4, options["tabSize"]!!.jsonPrimitive.int)
    }

    @Test
    fun `a stale edit is refused whole`() = runBlocking {
        services.start()
        python()
        open()
        until("opened") { programs.started.firstOrNull()?.sent("textDocument/didOpen")?.isNotEmpty() == true }
        val session = services.pool.ready(saved.last().single().id)!!
        val edit = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"documentChanges":[{"textDocument":{"uri":"file:///workspace/app/main.py","version":7},"edits":[{"range":{"start":{"line":0,"character":0},"end":{"line":0,"character":5}},"newText":"echo"}]}]}""",
        ).jsonObject
        assertTrue(services.features.edits.apply(session, edit)!!.contains("changed since"))
        assertTrue(edited.isEmpty())
    }

    @Test
    fun `switching workspace stops every server gracefully and clears its problems`() = runBlocking {
        services.start()
        python()
        open()
        until("running") { programs.started.isNotEmpty() && services.pool.ready(saved.last().single().id) != null }
        services.stopAll()
        until("shut down") { "exit" in programs.started.single().methods() }
        assertTrue(stateHolder.current.languageDiagnostics.isEmpty())
        assertEquals(listOf("initialize", "initialized", "textDocument/didOpen", "shutdown", "exit"), programs.started.single().methods())
        assertTrue(programs.started.single().sent("initialize").single()["params"]!!.jsonObject["workspaceFolders"]!!.jsonArray.isNotEmpty())
    }

    @Test
    fun `a server stopped with the screens starts again when the file is edited, and stopping never takes the next one`() = runBlocking {
        services.start()
        val id = python()
        stateHolder.update { it.copy(activeDocument = null) }
        open()
        stateHolder.update { it.copy(activeDocument = it.openDocuments.single()) }
        until("first running") { services.pool.ready(id) != null }
        services.stopAll()
        // Started again at once, as the next workspace's would be: the stop that was asked must not reach it.
        services.pool.ensure(services.configuration(id)!!, runtime.root.path, asked = false)
        until("second running") { programs.started.size == 2 && services.pool.ready(id) != null }
        delay(200)
        assertTrue(services.pool.ready(id) != null)
        services.stopAll()
        until("stopped") { services.pool.ready(id) == null && "exit" in programs.started[1].methods() }
        type("print(9)\n")
        until("third running") { programs.started.size == 3 && services.pool.ready(id) != null }
    }

    @Test
    fun `a server not installed is said only when asked to start, and its page offers its install command`() = runBlocking {
        val notices = Collections.synchronizedList(mutableListOf<String>())
        scope.launch { stateHolder.effects.collect { effect -> (effect as? dev.icode.mobile.domain.ICodeEffect.Told)?.let { notices += it.notice.title } } }
        delay(50)
        programs.missing = setOf("pylsp")
        services.start()
        val id = services.save(LanguageServiceConfiguration("", "Python", "pylsp", languages = "python")).getOrThrow().id
        open()
        until("failed for it") { services.rows().single().notInstalled }
        assertEquals("sudo apt install -y python3-pylsp", services.rows().single().install)
        delay(300)
        assertTrue(notices.isEmpty())
        services.start(id).getOrThrow()
        until("said") { notices.isNotEmpty() }
        assertEquals(listOf("Install Python first"), notices.toList())
        programs.missing = emptySet()
        services.start(id).getOrThrow()
        until("running") { services.pool.ready(id) != null }
        assertTrue(!services.rows().single().notInstalled)
    }

    @Test
    fun `installing types the preset's command in a terminal, and refuses a program iCode knows no install for`() = runBlocking {
        val typed = Collections.synchronizedList(mutableListOf<String>())
        val terminal = LanguageHosts(WorkspaceFileSource(runtime.paths), { _, _ -> }, {}, { 4 to true }) { typed += it; true }
        val owner = LanguageServices(store, stateHolder, scope, programs, LspUris(runtime.paths), { emptyMap() }, terminal) { runtime.root.path }
        assertEquals("npm install -g yaml-language-server", owner.install("yaml-language-server --stdio").getOrThrow())
        assertEquals(listOf("npm install -g yaml-language-server"), typed.toList())
        assertTrue(owner.install("kotlin-lsp --stdio").isFailure)
    }
}
