package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.agent.client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.AgentCapabilities
import com.agentclientprotocol.model.AuthMethod
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.AuthenticateResponse
import com.agentclientprotocol.model.AvailableCommand
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.DeleteSessionResponse
import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.SessionConfigId
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigOptionValue
import com.agentclientprotocol.model.SessionCapabilities
import com.agentclientprotocol.model.SessionConfigValueId
import com.agentclientprotocol.model.SessionDeleteCapabilities
import com.agentclientprotocol.model.SessionInfo
import com.agentclientprotocol.model.SessionListCapabilities
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionMode
import com.agentclientprotocol.model.SessionModeId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.SetSessionConfigOptionResponse
import com.agentclientprotocol.model.SetSessionModeResponse
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.protocol.JsonRpcException
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.rpc.JsonRpcErrorCode
import com.agentclientprotocol.transport.StdioTransport
import dev.icode.mobile.data.language.jsonrpc.BytePipe
import dev.icode.mobile.data.runtime.StdioProgram
import dev.icode.mobile.data.runtime.StdioPrograms
import dev.icode.mobile.domain.AgentSettings
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
@OptIn(UnstableApi::class)
internal class FakeAgents : StdioPrograms {
    /** What each start was given: its command, its variables and its folder. */
    data class Start(val id: String, val argv: List<String>, val variables: Map<String, String>, val folder: String)

    val starts = CopyOnWriteArrayList<Start>()
    val programs = CopyOnWriteArrayList<StdioProgram>()

    /** The ways it offers to be signed in, and whether it is: a new session asks for it until then. */
    @Volatile var methods: List<AuthMethod> = emptyList()
    @Volatile var signedIn = true

    /** The modes each new session offers and the one it starts in; its options; the commands it says once made. */
    @Volatile var modes: List<SessionMode> = emptyList()
    @Volatile var startMode: String? = null
    @Volatile var options: List<SessionConfigOption> = emptyList()
    @Volatile var commands: List<AvailableCommand> = emptyList()

    /** What the client said it can do, as it connected. */
    @Volatile var client: ClientInfo? = null

    /** Whether it says it loads a session again, and each it loaded. */
    @Volatile var loads = false
    val loaded = CopyOnWriteArrayList<String>()

    /** Whether it says it deletes sessions, and each it deleted. */
    @Volatile var deletes = false
    val deleted = CopyOnWriteArrayList<String>()

    /** Whether it says it lists sessions, and those it lists, each in its folder. */
    @Volatile var lists = false
    @Volatile var listed: List<SessionInfo> = emptyList()

    /** What a session loaded replays of what was said in it. */
    @Volatile var history: List<SessionUpdate> = emptyList()

    /** Each mode and option set, as the client asked: "mode" to "default", "auto_approve" to "false". */
    val sets = CopyOnWriteArrayList<Pair<String, String>>()

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
        override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
            client = clientInfo
            val sessions = SessionCapabilities(list = if (lists) SessionListCapabilities() else null, delete = if (deletes) SessionDeleteCapabilities() else null)
            return AgentInfo(capabilities = AgentCapabilities(loadSession = loads, sessionCapabilities = sessions), authMethods = methods)
        }

        override suspend fun listSessions(cwd: String?, additionalDirectories: List<String>?, _meta: JsonElement?): Sequence<SessionInfo> =
            listed.filter { cwd == null || it.cwd == cwd }.asSequence()

        override suspend fun deleteSession(sessionId: SessionId, _meta: JsonElement?): DeleteSessionResponse {
            deleted += sessionId.value
            return DeleteSessionResponse()
        }

        override suspend fun loadSession(sessionId: SessionId, sessionParameters: SessionCreationParameters): AgentSession {
            loaded += sessionId.value
            return session(sessionId, sessionParameters)
        }

        override suspend fun authenticate(methodId: AuthMethodId, _meta: JsonElement?): AuthenticateResponse {
            signedIn = true
            return AuthenticateResponse()
        }

        override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
            if (!signedIn) throw JsonRpcException(JsonRpcErrorCode.AUTH_REQUIRED.code, "Authentication required")
            sessions += sessionParameters.cwd
            return session(SessionId("s${ids.incrementAndGet()}"), sessionParameters)
        }

        private fun session(id: SessionId, sessionParameters: SessionCreationParameters): AgentSession {
            var mode = startMode ?: modes.firstOrNull()?.id?.value.orEmpty()
            var settings = options
            return object : AgentSession {
                override val sessionId = id

                override val availableModes: List<SessionMode> get() = modes

                override val defaultMode: SessionModeId get() = SessionModeId(mode)

                override suspend fun setMode(modeId: SessionModeId, _meta: JsonElement?): SetSessionModeResponse {
                    sets += AgentSettings.MODE to modeId.value
                    mode = modeId.value
                    return SetSessionModeResponse()
                }

                override val configOptions: List<SessionConfigOption> get() = settings

                override suspend fun setConfigOption(configId: SessionConfigId, value: SessionConfigOptionValue, _meta: JsonElement?): SetSessionConfigOptionResponse {
                    val given = when (value) {
                        is SessionConfigOptionValue.StringValue -> value.value
                        is SessionConfigOptionValue.BoolValue -> value.value.toString()
                        else -> error("Unknown value")
                    }
                    sets += configId.value to given
                    settings = settings.map { option ->
                        when {
                            option.id != configId -> option
                            option is SessionConfigOption.Select -> option.copy(currentValue = SessionConfigValueId(given))
                            option is SessionConfigOption.BooleanOption -> option.copy(currentValue = given.toBoolean())
                            else -> option
                        }
                    }
                    return SetSessionConfigOptionResponse(settings)
                }

                override suspend fun replayHistory() {
                    history.forEach { currentCoroutineContext().client.notify(it) }
                }

                override suspend fun postInitialize() {
                    if (commands.isNotEmpty()) currentCoroutineContext().client.notify(SessionUpdate.AvailableCommandsUpdate(commands))
                }

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
        fun chunk(text: String) = SessionUpdate.AgentMessageChunk(ContentBlock.Text(text))

        fun ended(reason: StopReason = StopReason.END_TURN) = Event.PromptResponseEvent(PromptResponse(reason))
    }
}
