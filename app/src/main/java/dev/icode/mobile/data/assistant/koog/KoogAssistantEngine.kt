package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.io.use
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantEngine
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.data.assistant.AssistantTurnRequest
import dev.icode.mobile.domain.AssistantProviderKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The assistant on Koog (§4.2): a new agent per turn over the chat so far, running iCode's own loop
 * as a functional strategy — the reply streamed, its tool calls run one at a time through Koog's
 * environment, their results sent back — until a reply calls no tool or the step limit is reached.
 */
internal class KoogAssistantEngine(
    /** One of Koog's clients for a connection: [KoogClients] on the device, a scripted one in tests. */
    private val clients: (AssistantConnection) -> LLMClient,
) : AssistantEngine {
    /** The last connection's client, kept between turns and replaced when the settings change. */
    override val suggestions: Map<AssistantProviderKind, List<String>> by lazy { KoogModels.suggestions() }

    private val clientLock = Mutex()
    private var client: Pair<AssistantConnection, LLMClient>? = null

    override suspend fun run(turn: AssistantTurnRequest, events: AssistantEvents) {
        try {
            val tools = turn.tools.associateBy { it.name }
            val agent = AIAgent(
                promptExecutor = MultiLLMPromptExecutor(clientFor(turn.connection)),
                agentConfig = AIAgentConfig(
                    prompt = KoogHistory.prompt(turn.instructions, turn.history),
                    model = KoogModels.resolve(turn.connection.kind, turn.connection.model),
                    maxAgentIterations = turn.maxSteps,
                ),
                strategy = functionalStrategy<String, Unit>(STRATEGY) { input -> converse(input, turn.maxSteps, tools, events) },
                toolRegistry = ToolRegistry.builder().tools(turn.tools.map(::KoogTool)).build(),
            )
            agent.use { it.run(turn.input) }
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (failure: Exception) {
            throw KoogFailures.explain(failure, turn.connection)
        }
    }

    private suspend fun clientFor(connection: AssistantConnection): LLMClient = clientLock.withLock {
        client?.takeIf { it.first == connection }?.let { return@withLock it.second }
        client?.second?.close()
        clients(connection).also { client = connection to it }
    }

    /** The turn's loop, inside Koog's agent: Koog keeps the prompt, iCode decides each step. */
    private suspend fun AIAgentFunctionalContext.converse(
        input: String,
        maxSteps: Int,
        tools: Map<String, AssistantTool>,
        events: AssistantEvents,
    ) {
        appendPrompt { user(input) }
        repeat(maxSteps) {
            val reply = llm.writeSession {
                val frames = mutableListOf<StreamFrame>()
                requestLLMStreaming().collect { frame ->
                    frames += frame
                    if (frame is StreamFrame.TextDelta) events.text(frame.text)
                }
                KoogHistory.reply(frames) { "icode-" + UUID.randomUUID() }.also { whole ->
                    if (whole.parts.isNotEmpty()) appendPrompt { message(whole) }
                }
            }
            val said = KoogHistory.reply(reply, tools)
            if (said.text.isNotEmpty() || said.calls.isNotEmpty()) events.replied(said)
            val calls = reply.parts.filterIsInstance<MessagePart.Tool.Call>()
            if (calls.isEmpty()) return
            val received = calls.mapIndexed { index, call ->
                events.calling(said.calls[index])
                executeTool(call)
            }
            appendPrompt { user(received.map { it.toMessagePart() }) }
            events.answered(KoogHistory.results(said.calls, received))
        }
        throw AssistantTurnException("Stopped after $maxSteps steps — send a message to continue")
    }

    companion object {
        private const val STRATEGY = "icode-assistant"

        /**
         * On OkHttp, handed to Koog explicitly: nothing is found through `ServiceLoader`. One per
         * process, as the backend is; a client replaced by new settings is closed, the engine never.
         */
        fun create(): KoogAssistantEngine {
            val http = KtorKoogHttpClient.Factory(baseClient = HttpClient(OkHttp))
            return KoogAssistantEngine { connection -> KoogClients.create(connection, http) }
        }
    }
}
