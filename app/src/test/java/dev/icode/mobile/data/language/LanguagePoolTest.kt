package dev.icode.mobile.data.language

import dev.icode.mobile.data.assistant.tools.TestRuntime
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServiceStatus
import java.nio.file.Files
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePoolTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtime = TestRuntime(Files.createTempDirectory("pool").toFile())
    private val uris = LspUris(runtime.paths)
    private val programs = FakePrograms()
    private val statuses = Collections.synchronizedList(mutableListOf<Pair<String, LanguageServiceStatus>>())
    private val messages = Collections.synchronizedList(mutableListOf<String?>())
    private val readied = Collections.synchronizedList(mutableListOf<LanguageServerSession>())
    private val events = object : LanguagePool.Events {
        override fun status(id: String, status: LanguageServiceStatus, pid: Int?, message: String?, capabilities: List<String>) {
            statuses += id to status
            messages += message
        }
        override fun log(id: String, line: String) = Unit
        override suspend fun ready(session: LanguageServerSession) { readied += session }
        override fun gone(session: LanguageServerSession) = Unit
    }
    private val client = object : LanguageServerSession.Client {
        override fun diagnostics(session: LanguageServerSession, params: JsonObject) = Unit
        override fun log(session: LanguageServerSession, line: String) = Unit
        override fun message(session: LanguageServerSession, type: Int, text: String) = Unit
        override fun progress(session: LanguageServerSession, words: String?) = Unit
        override fun capabilitiesChanged(session: LanguageServerSession) = Unit
        override fun refreshDiagnostics(session: LanguageServerSession) = Unit
        override suspend fun applyEdit(session: LanguageServerSession, edit: JsonObject): Boolean = false
    }
    private val pool = LanguagePool(scope, programs, uris, { emptyMap() }, client, events, backoff = longArrayOf(10, 10, 10, 10, 10))
    private val workspace = runtime.root.path

    private fun config(id: String, command: String = "server-$id --stdio") = LanguageServiceConfiguration(id, id, command, languages = "python")

    private suspend fun until(what: String, condition: () -> Boolean) = withTimeout(10_000) {
        while (!condition()) delay(10)
    }.also { assertTrue(what, condition()) }

    @After
    fun stop() = scope.cancel()

    @Test
    fun `a server starts in the workspace's folder with iCode's handshake, then runs`() = runBlocking {
        pool.ensure(config("a"), workspace, asked = false)
        until("running") { pool.ready("a") != null }
        val server = programs.started.single()
        assertEquals(listOf("server-a", "--stdio"), server.argv)
        assertEquals("/workspace/app", server.folder)
        val initialize = server.sent("initialize").single()["params"]!!.jsonObject
        assertEquals("file:///workspace/app", initialize["rootUri"]!!.jsonPrimitive.content)
        assertEquals("utf-16", initialize["capabilities"]!!.jsonObject["general"]!!.jsonObject["positionEncodings"]!!.jsonArray.single().jsonPrimitive.content)
        until("initialized") { "initialized" in server.methods() }
        assertEquals(listOf("a" to LanguageServiceStatus.Starting, "a" to LanguageServiceStatus.Running), statuses.toList())
        assertEquals(listOf("Completion", "Hover", "Go to definition"), pool.ready("a")!!.capabilities.labels())
        assertEquals(1, readied.size)
    }

    @Test
    fun `stopping sends shutdown then exit`() = runBlocking {
        pool.ensure(config("a"), workspace, asked = false)
        until("running") { pool.ready("a") != null }
        pool.stop("a")
        val server = programs.started.single()
        until("exit") { "exit" in server.methods() }
        assertEquals(listOf("initialize", "initialized", "shutdown", "exit"), server.methods())
        until("stopped") { statuses.lastOrNull() == ("a" to LanguageServiceStatus.Stopped) }
    }

    @Test
    fun `a crashing server starts again, and after five crashes is held as failed until asked`() = runBlocking {
        pool.ensure(config("a"), workspace, asked = false)
        for (crash in 1..LanguagePool.CRASHES) {
            until("server $crash running") { programs.started.size == crash && pool.ready("a") === readied.lastOrNull() && pool.ready("a") != null }
            programs.started.last().crash(2)
        }
        until("failed") { statuses.lastOrNull() == ("a" to LanguageServiceStatus.Failed) }
        assertTrue(messages.last()!!.startsWith("Stopped after 5 exits"))
        pool.ensure(config("a"), workspace, asked = false)
        delay(100)
        assertEquals(LanguagePool.CRASHES, programs.started.size)
        pool.ensure(config("a"), workspace, asked = true)
        until("started again when asked") { programs.started.size == LanguagePool.CRASHES + 1 }
    }

    @Test
    fun `a fourth server stops the one unused longest`() = runBlocking {
        for (id in listOf("a", "b", "c")) {
            pool.ensure(config(id), workspace, asked = false)
            until("$id running") { pool.ready(id) != null }
            delay(5)
        }
        pool.used("a")
        pool.ensure(config("d"), workspace, asked = false)
        until("d running") { pool.ready("d") != null }
        assertEquals(setOf("a", "c", "d"), pool.running.map { it.config.id }.toSet())
        until("b shut down") { "shutdown" in programs.started.first { it.id == "b" }.methods() }
    }

    @Test
    fun `a program missing from the runtime fails with how to install it`() = runBlocking {
        programs.missing = setOf("pylsp")
        pool.ensure(config("p", "pylsp"), workspace, asked = false)
        until("failed") { statuses.lastOrNull() == ("p" to LanguageServiceStatus.Failed) }
        assertEquals("pylsp is not in the Linux runtime. Install it in a terminal: sudo apt install -y python3-pylsp", messages.last())
        assertTrue(programs.started.isEmpty())
    }

    @Test
    fun `diagnostics about text older than the server has are dropped`() = runBlocking {
        val stateHolder = ICodeStateHolder()
        val diagnostics = LanguageDiagnostics(stateHolder, scope, uris)
        pool.ensure(config("a"), workspace, asked = false)
        until("running") { pool.ready("a") != null }
        val session = pool.ready("a")!!
        val file = java.io.File(runtime.root, "x.py").apply { writeText("x = 1\n") }
        session.documents.open(file.path, uris.uriOf(file.path)!!, "python", "x = 1\n")
        session.documents.change(file.path, "x = 12\n")
        val uri = uris.uriOf(file.path)!!
        fun report(version: Int, message: String) = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"uri":"$uri","version":$version,"diagnostics":[{"range":{"start":{"line":0,"character":0},"end":{"line":0,"character":1}},"severity":2,"message":"$message","source":"fake","code":"W1"}]}""",
        ).jsonObject
        diagnostics.published(session, report(1, "stale"))
        diagnostics.published(session, report(2, "current"))
        until("published") { stateHolder.current.languageDiagnostics.isNotEmpty() }
        val shown = stateHolder.current.languageDiagnostics.single()
        assertEquals("current", shown.message)
        assertEquals("x.py", shown.file)
        assertEquals("1:1", shown.location)
        assertEquals("W1", shown.code)
        diagnostics.clear("a")
        until("cleared") { stateHolder.current.languageDiagnostics.isEmpty() }
    }
}
