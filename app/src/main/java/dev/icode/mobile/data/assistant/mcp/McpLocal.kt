package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.GuestEnvironment
import dev.icode.mobile.data.runtime.PosixSignal
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeProcess
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.domain.McpValue
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * A local server in the Linux runtime (docs/mcp-plan.md §7): a PRoot of its own that ends with iCode,
 * the user's login environment with the server's over it, its command and arguments as argv — never
 * read again by a shell — and stderr in its own log, out of the stdout MCP speaks on.
 */
internal class McpLocal(private val layout: RuntimeLayout, private val environment: GuestEnvironment) {
    private val guest = GuestCommand(layout, GuestContext.Command)

    /** A started server: what MCP speaks over, and the process to stop or watch end. */
    class Started(val transport: StdioClientTransport, private val process: RuntimeProcess, val ended: CompletableDeferred<Int>) {
        /** As the spec stops one: its input closed, then SIGTERM, then SIGKILL, [GRACE_MILLIS] apart. */
        suspend fun stop() {
            runCatching { transport.close() }
            if (withTimeoutOrNull(GRACE_MILLIS) { ended.await() } != null) return
            process.sendSignal(PosixSignal.TERM)
            if (withTimeoutOrNull(GRACE_MILLIS) { ended.await() } != null) return
            process.destroy()
        }
    }

    /**
     * Starts [transport] for the server [id], with [values] its environment, its secrets read. Throws
     * [McpFailures.Missing] when the command is not in the runtime.
     */
    suspend fun start(id: String, transport: McpTransport.Local, values: List<McpValue>, scope: CoroutineScope): Started {
        val login = environment.of(GuestContext.Command).variables
        val variables = login + values.mapNotNull { value -> value.value?.let { value.name to it } }
        missing(transport.command, variables)?.let { throw McpFailures.Missing(it) }
        val log = log(id).apply { parentFile?.mkdirs(); writeText("") }
        val process = RuntimeProcess.start(
            argv = layout.prootCommand(
                // $0 is the log; "$@" the command and its arguments, exactly as given.
                guestCommand = listOf("/bin/bash", "-c", "exec 2>>\"\$0\"; exec \"\$@\"", "$GUEST_LOGS/$id.log", transport.command) + transport.args,
                variables = variables,
                guestWorkingDirectory = transport.folder ?: RuntimeLayout.GUEST_HOME,
                killOnExit = true,
            ),
            environment = layout.hostEnvironment(),
            workingDirectory = layout.runtimeRoot.absolutePath,
        )
        val ended = CompletableDeferred<Int>()
        scope.launch(Dispatchers.IO) { ended.complete(process.waitFor()) }
        val stdio = StdioClientTransport(input = process.output.asSource().buffered(), output = process.input.asSink().buffered())
        return Started(stdio, process, ended)
    }

    /** The last [lines] its stderr wrote, since it last started. */
    fun tail(id: String, lines: Int = LOG_LINES): List<String> =
        runCatching { log(id).readLines().takeLast(lines) }.getOrDefault(emptyList())

    /** What a command missing from the runtime needs; null when the runtime has it. */
    private suspend fun missing(command: String, variables: Map<String, String>): String? {
        val found = guest.capture(null, listOf("/bin/bash", "-c", "command -v \"\$0\"", command), "mcp", variables)
        return if (found.exitCode == 0) null else command
    }

    private fun log(id: String) = File(layout.rootfs, "${GUEST_LOGS.removePrefix("/")}/$id.log")

    companion object {
        private const val GUEST_LOGS = "/tmp/icode-mcp"
        private const val LOG_LINES = 200
        const val GRACE_MILLIS = 3_000L
    }
}
