package dev.icode.mobile.data.language

import dev.icode.mobile.data.assistant.tools.TestRuntime
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceFileSource
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.SemanticToken
import dev.icode.mobile.domain.TextEncoding
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor's semantic tokens over in-process servers: asked only of a server that offers them, in full and then as deltas (S7). */
class LanguageSemanticTokensTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtime = TestRuntime(Files.createTempDirectory("semantic").toFile())
    private val stateHolder = ICodeStateHolder()
    private val store = object : LanguageServiceSaves {
        override fun load() = emptyList<LanguageServiceConfiguration>()
        override fun save(services: List<LanguageServiceConfiguration>) = Unit
    }
    private val hosts = LanguageHosts(WorkspaceFileSource(runtime.paths), { _, _ -> }, {}, { 4 to true })
    private val file = File(runtime.root, "main.py").apply { writeText(TEXT) }

    private fun services(programs: FakePrograms) = LanguageServices(store, stateHolder, scope, programs, LspUris(runtime.paths), { emptyMap() }, hosts) { runtime.root.path }

    private fun open() = stateHolder.update {
        it.copy(activeWorkspaceLocation = runtime.root.path, openDocuments = listOf(OpenDocument("main.py", file.path, TEXT, TextEncoding.Utf8, LineEnding.Lf, false, false)))
    }

    private suspend fun started(programs: FakePrograms): LanguageServices {
        val services = services(programs)
        services.start()
        services.save(LanguageServiceConfiguration("", "Python", "pyright", languages = "python")).getOrThrow()
        open()
        withTimeout(10_000) { while (programs.started.firstOrNull()?.sent("textDocument/didOpen").isNullOrEmpty()) delay(10) }
        return services
    }

    @After
    fun stop() = scope.cancel()

    @Test
    fun `a server offering semantic tokens is asked for the whole file, then for the change since`() = runBlocking {
        val programs = FakePrograms(OFFERING)
        programs.answers["textDocument/semanticTokens/full"] = """{"resultId":"1","data":[0,4,1,0,1,1,4,6,1,0]}"""
        programs.answers["textDocument/semanticTokens/full/delta"] = """{"resultId":"2","edits":[{"start":5,"deleteCount":5}]}"""
        val services = started(programs)
        val server = programs.started.single()
        val capabilities = server.sent("initialize").single()["params"]!!.jsonObject["capabilities"]!!.jsonObject
        assertTrue("textDocument.semanticTokens", capabilities["textDocument"]!!.jsonObject.containsKey("semanticTokens"))

        val first = services.highlights.semanticTokens(file.path)!!
        assertEquals(TEXT, first.text)
        assertEquals(listOf(SemanticToken(0, 4, 1, "parameter", setOf("declaration")), SemanticToken(1, 4, 6, "property", emptySet())), first.tokens)
        assertEquals(1, server.sent("textDocument/semanticTokens/full").size)

        val second = services.highlights.semanticTokens(file.path)!!
        assertEquals(listOf(SemanticToken(0, 4, 1, "parameter", setOf("declaration"))), second.tokens)
        val delta = server.sent("textDocument/semanticTokens/full/delta").single()["params"]!!.jsonObject
        assertEquals("1", delta["previousResultId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `no server offering them, nothing is asked and the grammar's colours stay`() = runBlocking {
        val programs = FakePrograms()
        val services = started(programs)
        assertNull(services.highlights.semanticTokens(file.path))
        val methods = programs.started.single().methods()
        assertTrue(methods.toString(), methods.none { it.startsWith("textDocument/semanticTokens") })
        // A file no service serves: no server is even started.
        val none = FakePrograms(OFFERING)
        val unserved = services(none).apply { start() }
        stateHolder.update { it.copy(openDocuments = listOf(OpenDocument("notes.txt", File(runtime.root, "notes.txt").path, "x", TextEncoding.Utf8, LineEnding.Lf, false, false))) }
        assertNull(unserved.highlights.semanticTokens(File(runtime.root, "notes.txt").path))
        assertTrue(none.started.isEmpty())
    }

    private companion object {
        const val TEXT = "def f(a):\n    a.length\n"
        const val OFFERING = """{"textDocumentSync":{"openClose":true,"change":2},"semanticTokensProvider":{"legend":{"tokenTypes":["parameter","property"],"tokenModifiers":["declaration"]},"full":{"delta":true}}}"""
    }
}
