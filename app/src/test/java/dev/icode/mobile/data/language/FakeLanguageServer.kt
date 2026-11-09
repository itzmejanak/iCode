package dev.icode.mobile.data.language

import dev.icode.mobile.data.language.jsonrpc.BytePipe
import dev.icode.mobile.data.language.jsonrpc.LspFraming
import dev.icode.mobile.data.runtime.StdioProgram
import dev.icode.mobile.data.runtime.StdioPrograms
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Language servers for tests, in this process: each started program is a [FakeServer] that answers
 * `initialize` with [capabilities] and `shutdown`, records what it was sent, and can be made to crash.
 */
internal class FakePrograms(private val capabilities: String = DEFAULT_CAPABILITIES) : StdioPrograms {
    val started: MutableList<FakeServer> = Collections.synchronizedList(mutableListOf())
    var missing: Set<String> = emptySet()
    /** What each started server answers a request with, by method; JSON text. */
    val answers = mutableMapOf<String, String>()

    override suspend fun missing(command: String, variables: Map<String, String>): String? = command.takeIf { it in missing }

    override fun start(id: String, argv: List<String>, variables: Map<String, String>, folder: String, scope: CoroutineScope): StdioProgram =
        FakeServer(id, argv, folder, capabilities, answers).also { started += it; it.serve() }

    override fun tail(id: String, lines: Int): List<String> = listOf("stderr of $id")

    companion object {
        const val DEFAULT_CAPABILITIES = """{"textDocumentSync":{"openClose":true,"change":2,"save":{"includeText":false}},"hoverProvider":true,"completionProvider":{"triggerCharacters":["."],"resolveProvider":true},"definitionProvider":true}"""
    }
}

internal class FakeServer(
    val id: String,
    val argv: List<String>,
    val folder: String,
    private val capabilities: String,
    private val answers: Map<String, String>,
) : StdioProgram {
    private val toServer = BytePipe()
    private val toClient = BytePipe()
    private val done = CompletableDeferred<Int>()
    val received: MutableList<JsonObject> = Collections.synchronizedList(mutableListOf())

    override val input = toServer.output
    override val output = toClient.input
    override val pid: Int = 1000 + id.hashCode() % 1000
    override val ended: Deferred<Int> = done

    fun methods(): List<String> = synchronized(received) { received.mapNotNull { it["method"]?.jsonPrimitive?.content } }

    fun sent(method: String): List<JsonObject> = synchronized(received) { received.filter { it["method"]?.jsonPrimitive?.content == method } }

    /** Tells iCode [method] with [params], as a server's notification. */
    fun notifyClient(method: String, params: String) = LspFraming.write(toClient.output, """{"jsonrpc":"2.0","method":"$method","params":$params}""")

    /** The process ends with [code], as a crash would end it. */
    fun crash(code: Int = 1) = end(code)

    fun serve() {
        Thread {
            while (true) {
                val text = runCatching { LspFraming.read(toServer.input) }.getOrNull() ?: break
                val message = Json.parseToJsonElement(text).jsonObject
                received += message
                val method = message["method"]?.jsonPrimitive?.content
                if (method == "exit") {
                    end(0)
                    continue
                }
                val requestId: JsonElement = message["id"] ?: continue
                if (method == null) continue
                val result = when (method) {
                    "initialize" -> """{"capabilities":$capabilities,"serverInfo":{"name":"fake"}}"""
                    "shutdown" -> "null"
                    else -> answers[method] ?: "null"
                }
                runCatching { LspFraming.write(toClient.output, """{"jsonrpc":"2.0","id":$requestId,"result":$result}""") }
            }
        }.apply { isDaemon = true }.start()
    }

    override suspend fun stop(closeInput: suspend () -> Unit) {
        runCatching { closeInput() }
        end(0)
    }

    override fun kill() = end(137)

    private fun end(code: Int) {
        if (done.complete(code)) runCatching { toClient.output.close() }
    }
}
