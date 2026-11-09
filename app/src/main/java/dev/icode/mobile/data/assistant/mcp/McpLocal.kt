package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.GuestEnvironment
import dev.icode.mobile.data.runtime.GuestStdio
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.domain.McpValue
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * A local server in the Linux runtime (docs/mcp-plan.md §7), started as every protocol program in
 * the runtime is ([GuestStdio]): a PRoot of its own that ends with iCode, the user's login
 * environment with the server's over it, its command and arguments as argv, and stderr in its own log.
 */
internal class McpLocal(layout: RuntimeLayout, private val environment: GuestEnvironment) {
    private val stdio = GuestStdio(layout, GuestContext.Command, GUEST_LOGS, tag = "mcp")

    /** A started server: what MCP speaks over, and the process to stop or watch end. */
    class Started(val transport: StdioClientTransport, private val program: GuestStdio.Started) {
        val ended: CompletableDeferred<Int> get() = program.ended

        /** As the spec stops one: its input closed, then SIGTERM, then SIGKILL, [GRACE_MILLIS] apart. */
        suspend fun stop() = program.stop { transport.close() }
    }

    /**
     * Starts [transport] for the server [id], with [values] its environment, its secrets read. Throws
     * [McpFailures.Missing] when the command is not in the runtime.
     */
    suspend fun start(id: String, transport: McpTransport.Local, values: List<McpValue>, scope: CoroutineScope): Started {
        val login = environment.of(GuestContext.Command).variables
        val variables = login + values.mapNotNull { value -> value.value?.let { value.name to it } }
        stdio.missing(transport.command, variables)?.let { throw McpFailures.Missing(it) }
        val program = stdio.start(id, listOf(transport.command) + transport.args, variables, transport.folder ?: RuntimeLayout.GUEST_HOME, scope)
        val connection = StdioClientTransport(input = program.output.asSource().buffered(), output = program.input.asSink().buffered())
        return Started(connection, program)
    }

    /** The last [lines] its stderr wrote, since it last started. */
    fun tail(id: String, lines: Int = GuestStdio.LOG_LINES): List<String> = stdio.tail(id, lines)

    companion object {
        private const val GUEST_LOGS = "/tmp/icode-mcp"
        const val GRACE_MILLIS = GuestStdio.GRACE_MILLIS
    }
}
