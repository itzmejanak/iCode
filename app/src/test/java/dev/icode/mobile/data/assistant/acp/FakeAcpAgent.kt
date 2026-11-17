package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.agent.client
import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.AgentCapabilities
import com.agentclientprotocol.model.AuthMethod
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.AuthenticateResponse
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.SessionConfigId
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigOptionCategory
import com.agentclientprotocol.model.SessionConfigOptionValue
import com.agentclientprotocol.model.SessionConfigSelectOption
import com.agentclientprotocol.model.SessionConfigSelectOptions
import com.agentclientprotocol.model.SessionConfigValueId
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.SetSessionConfigOptionResponse
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.protocol.JsonRpcException
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import dev.icode.mobile.data.runtime.StdioProgram
import dev.icode.mobile.data.runtime.StdioPrograms
import java.nio.channels.Channels
import java.nio.channels.Pipe
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * A coding agent on the protocol's other side, in this process: the official SDK's agent runtime over a
 * pair of pipes, as a real agent is over its process's standard streams. What it does in a turn is
 * [script]; every update it sends is kept, by session, and sent again when the session is loaded.
 */
@OptIn(UnstableApi::class)
internal class FakeAcpAgent : AgentSupport {
    val prompts = CopyOnWriteArrayList<String>()
    val cancels = AtomicInteger()
    val created = AtomicInteger()
    val loaded = CopyOnWriteArrayList<String>()
    val history = mutableMapOf<String, MutableList<SessionUpdate>>()
    var needsSignIn = false
    var signedIn = 0

    /** A turn: what the agent sends, and asks, for [text]; it ends the turn after. */
    var script: suspend Turn.(text: String) -> Unit = { send(SessionUpdate.AgentMessageChunk(ContentBlock.Text("Re: $it"))) }

    inner class Turn(private val session: String, private val out: FlowCollector<Event>, val client: ClientSessionOperations) {
        suspend fun send(update: SessionUpdate) {
            history.getOrPut(session) { mutableListOf() } += update
            out.emit(Event.SessionUpdateEvent(update))
        }
    }

    override suspend fun initialize(clientInfo: ClientInfo) = AgentInfo(
        capabilities = AgentCapabilities(loadSession = true),
        authMethods = listOf(AuthMethod.AgentAuth(AuthMethodId("login"), "Log In")),
        implementation = Implementation("fake", "1.0.0", "Fake Agent"),
    )

    override suspend fun authenticate(methodId: AuthMethodId, _meta: kotlinx.serialization.json.JsonElement?): AuthenticateResponse {
        signedIn++
        needsSignIn = false
        return AuthenticateResponse()
    }

    override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
        if (needsSignIn) throw JsonRpcException(AcpFailures.AUTH_REQUIRED, "Authentication required")
        return Session(SessionId("s-${created.incrementAndGet()}"))
    }

    override suspend fun loadSession(sessionId: SessionId, sessionParameters: SessionCreationParameters): AgentSession {
        loaded += sessionId.value
        return Session(sessionId)
    }

    private inner class Session(override val sessionId: SessionId) : AgentSession {
        private var model = "sonnet"

        override suspend fun replayHistory() {
            val client = currentCoroutineContext().client
            history[sessionId.value].orEmpty().toList().forEach { client.notify(it) }
        }

        override suspend fun prompt(content: List<ContentBlock>, _meta: kotlinx.serialization.json.JsonElement?): Flow<Event> = flow {
            val text = content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
            prompts += text
            history.getOrPut(sessionId.value) { mutableListOf() } += SessionUpdate.UserMessageChunk(ContentBlock.Text(text))
            Turn(sessionId.value, this, currentCoroutineContext().client).script(text)
            emit(Event.PromptResponseEvent(PromptResponse(StopReason.END_TURN)))
        }

        override suspend fun cancel() {
            cancels.incrementAndGet()
        }

        override val configOptions: List<SessionConfigOption>
            get() = listOf(
                SessionConfigOption.select(
                    "model", "Model", model,
                    SessionConfigSelectOptions.Flat(listOf(SessionConfigSelectOption(SessionConfigValueId("sonnet"), "Sonnet"), SessionConfigSelectOption(SessionConfigValueId("opus"), "Opus"))),
                    category = SessionConfigOptionCategory.MODEL,
                ),
            )

        override suspend fun setConfigOption(configId: SessionConfigId, value: SessionConfigOptionValue, _meta: kotlinx.serialization.json.JsonElement?): SetSessionConfigOptionResponse {
            model = (value as SessionConfigOptionValue.StringValue).value
            return SetSessionConfigOptionResponse(configOptions)
        }
    }

    /** Its process, as [StdioPrograms] starts one: each start a new connection to this same agent. */
    fun programs(scope: CoroutineScope): StdioPrograms = object : StdioPrograms {
        val starts = AtomicInteger()
        override suspend fun missing(command: String, variables: Map<String, String>): String? = null
        override fun start(id: String, argv: List<String>, variables: Map<String, String>, folder: String, scope: CoroutineScope): StdioProgram {
            starts.incrementAndGet()
            return Program(scope)
        }
        override fun tail(id: String, lines: Int) = listOf("stderr of $id")
    }

    private inner class Program(parent: CoroutineScope) : StdioProgram {
        private val toAgent = Pipe.open()
        private val fromAgent = Pipe.open()
        private val job = SupervisorJob(parent.coroutineContext[Job])
        private val scope = CoroutineScope(Dispatchers.IO + job)
        override val input = Channels.newOutputStream(toAgent.sink())
        override val output = Channels.newInputStream(fromAgent.source())
        override val pid: Int = 1
        override val ended = CompletableDeferred<Int>()
        private val writer = Channels.newOutputStream(fromAgent.sink()).bufferedWriter()

        init {
            val reader = Channels.newInputStream(toAgent.source()).bufferedReader()
            val lines = flow { while (true) emit(reader.readLine() ?: break) }
            val transport = StdioTransport(scope, Dispatchers.IO, lines, { line ->
                withContext(Dispatchers.IO) {
                    synchronized(writer) {
                        writer.write(line)
                        writer.write("\n")
                        writer.flush()
                    }
                }
            })
            val protocol = Protocol(scope, transport)
            Agent(protocol, this@FakeAcpAgent)
            protocol.start()
        }

        override suspend fun stop(closeInput: suspend () -> Unit) {
            runCatching { closeInput() }
            end(0)
        }

        override fun kill() = end(137)

        private fun end(code: Int) {
            runCatching { toAgent.sink().close() }
            runCatching { fromAgent.sink().close() }
            job.cancel()
            ended.complete(code)
        }
    }
}
