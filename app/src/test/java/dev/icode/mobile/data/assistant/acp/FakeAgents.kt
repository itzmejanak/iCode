package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.agent.client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.AuthMethod
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.AuthenticateResponse
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.protocol.JsonRpcException
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.rpc.JsonRpcErrorCode
import com.agentclientprotocol.transport.StdioTransport
import dev.icode.mobile.data.language.jsonrpc.BytePipe
import dev.icode.mobile.data.runtime.StdioProgram
import dev.icode.mobile.data.runtime.StdioPrograms
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement

/**
 * Coding agents for tests, in this process: each started program is the ACP SDK's own agent side on
 * pipes, speaking the protocol as a real agent's stdout would. [answer] is what a prompt does — its
 * updates sent and its end — by default one line of text and the end of the turn.
 */
internal class FakeAgents : StdioPrograms {
    /** What each start was given: its command, its variables and its folder. */
    data class Start(val id: String, val argv: List<String>, val variables: Map<String, String>, val folder: String)

    val starts = CopyOnWriteArrayList<Start>()
    val programs = CopyOnWriteArrayList<StdioProgram>()

    /** The ways it offers to be signed in, and whether it is: a new session asks for it until then. */
    @Volatile var methods: List<AuthMethod> = emptyList()
    @Volatile var signedIn = true

    /** What each prompt was sent, as text; the folder each session was made in; each session cancelled. */
    val prompts = CopyOnWriteArrayList<String>()
    val sessions = CopyOnWriteArrayList<String>()
    val cancelled = CopyOnWriteArrayList<String>()

    @Volatile var answer: suspend FlowCollector<Event>.(ClientSessionOperations) -> Unit = {
        emit(Event.SessionUpdateEvent(chunk("Done.")))
        emit(ended())
    }

    private val ids = AtomicInteger()

    override suspend fun missing(command: String, variables: Map<String, String>): String? = null

    override fun start(id: String, argv: List<String>, variables: Map<String, String>, folder: String, scope: CoroutineScope): StdioProgram {
        starts += Start(id, argv, variables, folder)
        val toAgent = BytePipe()
        val toClient = BytePipe()
        val running = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val protocol = Protocol(running, StdioTransport(running, Dispatchers.IO, toAgent.input.asSource().buffered(), toClient.output.asSink().buffered(), "fake-$id"))
        Agent(protocol, support())
        protocol.start()
        val ended = CompletableDeferred<Int>()
        return object : StdioProgram {
            override val input = toAgent.output
            override val output = toClient.input
            override val pid: Int = 1
            override val ended: Deferred<Int> = ended

            override suspend fun stop(closeInput: suspend () -> Unit) {
                runCatching { closeInput() }
                kill()
            }

            override fun kill() {
                if (!ended.complete(0)) return
                running.cancel()
                runCatching { toClient.output.close() }
            }
        }.also { programs += it }
    }

    override fun tail(id: String, lines: Int): List<String> = listOf("fake agent's stderr")

    private fun support() = object : AgentSupport {
        override suspend fun initialize(clientInfo: ClientInfo) = AgentInfo(authMethods = methods)

        override suspend fun authenticate(methodId: AuthMethodId, _meta: JsonElement?): AuthenticateResponse {
            signedIn = true
            return AuthenticateResponse()
        }

        override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
            if (!signedIn) throw JsonRpcException(JsonRpcErrorCode.AUTH_REQUIRED.code, "Authentication required")
            sessions += sessionParameters.cwd
            val id = SessionId("s${ids.incrementAndGet()}")
            return object : AgentSession {
                override val sessionId = id

                override suspend fun prompt(content: List<ContentBlock>, _meta: JsonElement?): Flow<Event> {
                    prompts += content.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
                    return flow { answer(currentCoroutineContext().client) }
                }

                override suspend fun cancel() {
                    cancelled += id.value
                }
            }
        }
    }

    companion object {
        fun chunk(text: String) = com.agentclientprotocol.model.SessionUpdate.AgentMessageChunk(ContentBlock.Text(text))

        fun ended(reason: StopReason = StopReason.END_TURN) = Event.PromptResponseEvent(PromptResponse(reason))
    }
}
