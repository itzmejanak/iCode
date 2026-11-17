package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.ClientCapabilities
import com.agentclientprotocol.model.ElicitationCapabilities
import com.agentclientprotocol.model.ElicitationFormCapabilities
import com.agentclientprotocol.model.ElicitationUrlCapabilities
import com.agentclientprotocol.model.FileSystemCapability
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.McpServer
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import dev.icode.mobile.BuildConfig
import dev.icode.mobile.data.runtime.StdioProgram
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One coding agent's process and its ACP connection (docs/assistant-and-mcp.md): JSON-RPC lines over the
 * process's standard streams through the official SDK, its sessions sharing the one connection. What
 * passes over it is chat content: none of it is logged. The process writes its own log to standard
 * error, which [GuestStdio][dev.icode.mobile.data.runtime.GuestStdio] keeps in a file.
 *
 * iCode advertises neither the file-system nor the terminal methods: an agent uses its own tools in
 * the runtime, and what it changes reaches open editors as any change made outside them does. It
 * advertises questions (elicitation) in form and URL modes, which the assistant's question card shows.
 */
@OptIn(UnstableApi::class)
internal class AcpConnection(
    val entry: AgentEntry,
    parent: CoroutineScope,
    private val program: StdioProgram,
    /** The process ended without being asked to: with its exit code. */
    private val ended: (AcpConnection, Int) -> Unit,
) {
    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job)
    private val output = program.input.bufferedWriter(Charsets.UTF_8)
    private val transport = StdioTransport(scope, Dispatchers.IO, lines(), ::write, name = "acp-${entry.agent.id}")
    private val protocol = Protocol(scope, transport)
    val client = Client(protocol)

    @Volatile private var closing = false

    /** The sessions opened on this connection, by the agent's ID for each. */
    val sessions = ConcurrentHashMap<String, ClientSession>()

    /** What the agent said it is and can do; set once [start] returns. */
    lateinit var info: AgentInfo
        private set

    val running: Boolean get() = !closing && !program.ended.isCompleted

    /** Reads, then the agent's `initialize`: its version, what it can do, and how it signs in. */
    suspend fun start(): AgentInfo {
        scope.launch {
            val code = program.ended.await()
            if (!closing) ended(this@AcpConnection, code)
        }
        protocol.start()
        info = client.initialize(
            ClientInfo(
                capabilities = ClientCapabilities(
                    fs = FileSystemCapability(readTextFile = false, writeTextFile = false),
                    terminal = false,
                    elicitation = ElicitationCapabilities(form = ElicitationFormCapabilities(), url = ElicitationUrlCapabilities()),
                ),
                implementation = Implementation(name = "icode", version = BuildConfig.VERSION_NAME, title = "iCode"),
            ),
        )
        return info
    }

    suspend fun newSession(cwd: String, mcp: List<McpServer>, operations: ClientSessionOperations): ClientSession =
        client.newSession(SessionCreationParameters(cwd, mcp)) { _, _ -> operations }.also { sessions[it.sessionId.value] = it }

    /** [session] opened again: the agent sends what was said and done in it to [operations] before it answers. */
    suspend fun loadSession(session: String, cwd: String, mcp: List<McpServer>, operations: ClientSessionOperations): ClientSession =
        client.loadSession(SessionId(session), SessionCreationParameters(cwd, mcp), operations).also { sessions[session] = it }

    suspend fun authenticate(method: String) {
        client.authenticate(AuthMethodId(method))
    }

    /** Asked to end: input closed, then the process stopped as a local MCP server's is. */
    suspend fun stop() = withContext(NonCancellable) {
        closing = true
        runCatching { protocol.close() }
        program.stop { runCatching { output.close() } }
        job.cancel()
    }

    /** The process's standard output, a line at a time: the protocol's frames. */
    private fun lines(): Flow<String> = flow {
        program.output.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) emit(reader.readLine() ?: break)
        }
    }

    /** One frame to the process's standard input; a closed stream ends the transport cleanly. */
    private suspend fun write(line: String) = withContext(Dispatchers.IO) {
        try {
            synchronized(output) {
                output.write(line)
                output.write("\n")
                output.flush()
            }
        } catch (closed: IOException) {
            throw IllegalStateException("The agent's input is closed", closed)
        }
    }
}
