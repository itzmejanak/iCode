package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
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
import dev.icode.mobile.domain.AssistantModelSpec
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

    override fun model(kind: AssistantProviderKind, spec: AssistantModelSpec): AssistantModel = KoogModels.model(kind, spec)

    /** A server's models: an Ollama's cards, which say what each model does; any other's list of IDs. */
    override suspend fun available(connection: AssistantConnection): List<AssistantModel> = explained(connection, model = null) {
        // A client of its own, closed after: never one a turn may be using.
        val client = clients(connection)
        try {
            val listed = if (client is OllamaClient) {
                client.getModels().map { LLModel(LLMProvider.Ollama, it.name, it.capabilities, it.contextLength) }
            } else {
                client.models()
            }
            val models = listed.mapNotNull { KoogModels.listed(connection.kind, it) }.distinctBy { it.id }
            models.filter { it.known } + models.filterNot { it.known }.sortedBy { it.id }
        } finally {
            client.close()
        }
    }

    /**
     * The clients of the servers used lately, by connection: kept between turns, so a provider's
     * client is made once. The eldest is closed past [MAX_CLIENTS] — never the one in use, which is
     * the newest.
     */
    private val clientLock = Mutex()
    private val cached = object : LinkedHashMap<AssistantConnection, LLMClient>(MAX_CLIENTS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AssistantConnection, LLMClient>): Boolean =
            (size > MAX_CLIENTS).also { if (it) eldest.value.close() }
    }

    override suspend fun run(turn: AssistantTurnRequest, events: AssistantEvents) = explained(turn.connection, turn.model.id) {
        val tools = turn.tools.associateBy { it.name }
        // Whether the last reply ended at the output limit: then the turn says so under it.
        var cutShort = false
        val agent = agent(
            connection = turn.connection,
            model = turn.model,
            effort = turn.effort,
            instructions = turn.instructions,
            messages = turn.history + turn.input,
            tools = turn.tools,
            // Koog counts each node it runs: the start, a request, then a run of the tools and a
            // request per step, and the finish — so this allows exactly maxSteps requests.
            maxIterations = 2 * turn.maxSteps + 1,
        ) { report(tools, events) { cutShort = it } }
        agent.use { it.run(START) }
        if (cutShort) throw AssistantTurnException(KoogFailures.cutShort(turn.model))
        Unit
    }

    override suspend fun compact(
        connection: AssistantConnection,
        model: AssistantModel,
        instructions: String,
        history: List<AssistantMessage>,
    ): String = explained(connection, model.id) {
        // No tools: Koog writes the chat's calls and results into it as text (its MissingToolsConversionStrategy).
        val messages = history + AssistantMessage.User(AssistantInstructions.COMPACT_REQUEST)
        var cutShort = false
        val agent = agent(connection, model, effort = null, instructions, messages, tools = emptyList(), maxIterations = 3) {
            handleEvents { onLLMCallCompleted { event -> event.response?.let { cutShort = KoogHistory.cutShort(it) } } }
        }
        val summary = agent.use { it.run(START) }.trim()
        if (cutShort) throw AssistantTurnException(KoogFailures.SUMMARY_CUT_SHORT)
        summary.ifEmpty { throw AssistantTurnException("The model sent an empty summary — try again") }
    }

    private suspend fun agent(
        connection: AssistantConnection,
        model: AssistantModel,
        effort: AssistantEffort?,
        instructions: String,
        messages: List<AssistantMessage>,
        tools: List<AssistantTool>,
        maxIterations: Int,
        features: GraphAIAgent.FeatureContext.() -> Unit = {},
    ): GraphAIAgent<String, String> {
        val llModel = KoogModels.llModel(connection.kind, model.spec)
        return AIAgent(
            promptExecutor = MultiLLMPromptExecutor(clientFor(connection)),
            agentConfig = AIAgentConfig(
                prompt = KoogHistory.prompt(instructions, messages, KoogParams.of(connection.kind, llModel, model, effort), llModel),
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
     * names its call by ID, and one the provider gave no ID is the first still open. [ended] hears,
     * for each reply, whether it stopped at the output limit.
     */
    private fun GraphAIAgent.FeatureContext.report(tools: Map<String, AssistantTool>, events: AssistantEvents, ended: (cutShort: Boolean) -> Unit) {
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
                ended(KoogHistory.cutShort(response))
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
        cached.getOrPut(connection) { clients(connection) }
    }

    private suspend fun <T> explained(connection: AssistantConnection, model: String?, block: suspend () -> T): T = try {
        block()
    } catch (stopped: CancellationException) {
        throw stopped
    } catch (failure: Exception) {
        throw KoogFailures.explain(failure, connection, model)
    }

    companion object {
        /**
         * Koog's `singleRunStrategy()`, its own nodes and edges, with two changes. It opens with a node
         * that only requests — Koog's `reActStrategy` has one — since the message, with its images
         * and files, is already the prompt's last (`nodeLLMRequest` would add a text-only one). And
         * after tool results it checks a reply's calls before its text: Koog's checks the text first
         * and finishes, leaving the calls of a reply with both ("Now the tests:" and a call — how
         * models work) unrun, as it already does after the first request.
         */
        private val TURN: AIAgentGraphStrategy<String, String> = strategy("icode-turn") {
            val request by node<String, Message.Assistant> { llm.writeSession { requestLLM() } }
            val runTools by nodeExecuteTools()
            val sendResults by nodeLLMSendToolResults()
            edge(nodeStart forwardTo request)
            edge(request forwardTo runTools onToolCalls { true })
            edge(request forwardTo nodeFinish onTextMessage { true })
            edge(runTools forwardTo sendResults)
            edge(sendResults forwardTo runTools onToolCalls { true })
            edge(sendResults forwardTo nodeFinish onTextMessage { true })
        }

        /** The agent's input: [TURN] reads none, the prompt being whole. */
        private const val START = ""

        private const val MAX_CLIENTS = 8

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
