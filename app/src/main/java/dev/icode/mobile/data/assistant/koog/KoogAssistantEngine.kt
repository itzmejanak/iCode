package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.serialization.JSONPrimitive
import ai.koog.utils.io.use
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantEngine
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantInstructions
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.data.assistant.AssistantTurnRequest
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The assistant on Koog (§4.2, §12 R1): Koog's standard agent — model, tools, results, model again,
 * until a reply calls no tool — built per turn over the chat so far ([TURN]). Its events tell the
 * page each reply, each call as it starts and ends, and the tokens used.
 */
internal class KoogAssistantEngine(
    /** One of Koog's clients for a connection: [KoogClients] on the device, a scripted one in tests. */
    private val clients: (AssistantConnection) -> LLMClient,
) : AssistantEngine {
    override val catalogue: Map<AssistantProviderKind, List<AssistantModel>> get() = KoogModels.catalogue

    override fun model(kind: AssistantProviderKind, id: String, contextWindow: Long?, maxOutput: Long?): AssistantModel =
        KoogModels.model(kind, id, contextWindow, maxOutput)

    /** The last connection's client, kept between turns and replaced when the settings change. */
    private val clientLock = Mutex()
    private var client: Pair<AssistantConnection, LLMClient>? = null

    override suspend fun run(turn: AssistantTurnRequest, events: AssistantEvents) = explained(turn.connection) {
        val tools = turn.tools.associateBy { it.name }
        val agent = agent(
            connection = turn.connection,
            model = turn.model,
            effort = turn.effort,
            instructions = turn.instructions,
            history = turn.history,
            tools = turn.tools,
            // Koog counts each node it runs: the start, a request, then a run of the tools and a
            // request per step, and the finish — so this allows exactly maxSteps requests.
            maxIterations = 2 * turn.maxSteps + 1,
        ) { report(tools, events) }
        agent.use { it.run(AssistantInstructions.message(turn.input)) }
        Unit
    }

    override suspend fun compact(
        connection: AssistantConnection,
        model: AssistantModel,
        instructions: String,
        history: List<AssistantMessage>,
    ): String = explained(connection) {
        // No tools: Koog writes the chat's calls and results into it as text (its MissingToolsConversionStrategy).
        val agent = agent(connection, model, effort = null, instructions, history, tools = emptyList(), maxIterations = 3)
        agent.use { it.run(AssistantInstructions.COMPACT_REQUEST) }.trim()
            .ifEmpty { throw AssistantTurnException("The model sent an empty summary — try again") }
    }

    private suspend fun agent(
        connection: AssistantConnection,
        model: AssistantModel,
        effort: AssistantEffort?,
        instructions: String,
        history: List<AssistantMessage>,
        tools: List<AssistantTool>,
        maxIterations: Int,
        features: GraphAIAgent.FeatureContext.() -> Unit = {},
    ): GraphAIAgent<String, String> {
        val llModel = KoogModels.llModel(connection.kind, connection.model)
        return AIAgent(
            promptExecutor = MultiLLMPromptExecutor(clientFor(connection)),
            agentConfig = AIAgentConfig(
                prompt = KoogHistory.prompt(instructions, history, KoogParams.of(connection.kind, llModel, model, effort)),
                model = llModel,
                maxAgentIterations = maxIterations,
            ),
            strategy = TURN,
            toolRegistry = ToolRegistry.builder().tools(tools.map(::KoogTool)).build(),
            installFeatures = features,
        )
    }

    /**
     * Koog's events in iCode's words. Koog runs a reply's calls one at a time, in order; each event
     * names its call by ID, and one the provider gave no ID is the first still open.
     */
    private fun GraphAIAgent.FeatureContext.report(tools: Map<String, AssistantTool>, events: AssistantEvents) {
        val open = ArrayDeque<AssistantCall>()
        fun callOf(id: String?): AssistantCall? = open.firstOrNull { it.id == id } ?: open.firstOrNull()
        fun answer(id: String?, output: String, failed: Boolean) {
            val call = callOf(id) ?: return
            open.remove(call)
            events.answered(AssistantResult(call.id, call.tool, output, failed))
        }
        handleEvents {
            onLLMCallCompleted { event ->
                val response = event.response ?: return@onLLMCallCompleted
                KoogHistory.tokens(response)?.let(events::used)
                val reply = KoogHistory.reply(response, tools) { "icode-" + UUID.randomUUID() }
                open.clear()
                open.addAll(reply.calls)
                if (reply.text.isNotEmpty() || reply.calls.isNotEmpty()) events.replied(reply)
            }
            onToolCallStarting { event -> callOf(event.toolCallId)?.let(events::calling) }
            onToolCallCompleted { event ->
                answer(event.toolCallId, (event.toolResult as? JSONPrimitive)?.contentOrNull.orEmpty(), failed = false)
            }
            onToolCallFailed { event -> answer(event.toolCallId, event.message, failed = true) }
            // Also arguments that are not JSON: Koog refuses those before the call starts.
            onToolValidationFailed { event -> answer(event.toolCallId, event.message, failed = true) }
        }
    }

    private suspend fun clientFor(connection: AssistantConnection): LLMClient = clientLock.withLock {
        client?.takeIf { it.first == connection }?.let { return@withLock it.second }
        client?.second?.close()
        clients(connection).also { client = connection to it }
    }

    private suspend fun <T> explained(connection: AssistantConnection, block: suspend () -> T): T = try {
        block()
    } catch (stopped: CancellationException) {
        throw stopped
    } catch (failure: Exception) {
        throw KoogFailures.explain(failure, connection)
    }

    companion object {
        /**
         * Koog's `singleRunStrategy()`, its own nodes and edges, with one pair in the other order:
         * after tool results, Koog's checks the reply's text first and finishes, leaving the calls of
         * a reply with both ("Now the tests:" and a call — how models work) unrun. Here calls are
         * checked first after every request, as Koog's already does after the first.
         */
        private val TURN: AIAgentGraphStrategy<String, String> = strategy("icode-turn") {
            val request by nodeLLMRequest()
            val runTools by nodeExecuteTools()
            val sendResults by nodeLLMSendToolResults()
            edge(nodeStart forwardTo request)
            edge(request forwardTo runTools onToolCalls { true })
            edge(request forwardTo nodeFinish onTextMessage { true })
            edge(runTools forwardTo sendResults)
            edge(sendResults forwardTo runTools onToolCalls { true })
            edge(sendResults forwardTo nodeFinish onTextMessage { true })
        }

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
