package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.utils.io.use
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantEngine
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantInstructions
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.data.assistant.AssistantTurnRequest
import dev.icode.mobile.data.assistant.koog.KoogTurn.report
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The assistant on Koog (§4.2, §12 R1): Koog's standard agent — model, tools, results, model again,
 * until a reply calls no tool — built per turn over the chat so far ([KoogTurn]). Its events tell the
 * page each reply, as it is written where it streams, each call as it starts and ends, and the
 * tokens used.
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
            // Those iCode knows first, in its order; then the rest by name, whatever their case.
            models.filter { it.known } + models.filterNot { it.known }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.id })
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

    /** The servers and models that refused to stream (`kind|address|model`): they are asked for whole replies. */
    private val refusedStreams: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val cached = object : LinkedHashMap<AssistantConnection, LLMClient>(MAX_CLIENTS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AssistantConnection, LLMClient>): Boolean =
            (size > MAX_CLIENTS).also { if (it) eldest.value.close() }
    }

    override suspend fun run(turn: AssistantTurnRequest, events: AssistantEvents) = explained(turn.connection, turn.model.id) {
        val reporter = KoogTurn.Reporter(turn.tools.associateBy { it.name }, events)
        // Replies stream where Koog's streams are sound, so the page shows them as they are written.
        val kind = turn.connection.kind
        val wholeOnly = "$kind|${turn.connection.baseUrl}|${turn.model.id}"
        val stream = KoogTurn.streamed(reporter, finishSaid = kind != AssistantProviderKind.OpenAICompatible) { refusedStreams += wholeOnly }
            .takeIf { KoogTurn.streams(kind, KoogModels.llModel(kind, turn.model.spec)) && wholeOnly !in refusedStreams }
        val agent = agent(
            connection = turn.connection,
            model = turn.model,
            effort = turn.effort,
            instructions = turn.instructions,
            messages = turn.history + listOfNotNull(turn.input),
            tools = turn.tools,
            // Koog counts each node it runs: the start, a request, then a run of the tools and a
            // request per step, and the finish — so this allows exactly maxSteps requests.
            maxIterations = 2 * turn.maxSteps + 1,
            strategy = KoogTurn.strategy(stream, turn.picture, KoogTurn.picturePlace(kind, KoogModels.llModel(kind, turn.model.spec), turn.model.images)),
        ) { report(reporter) }
        ran(stoppedShort = { reporter.cutShort || reporter.filtered }) { agent.use { it.run(START) } }
        if (reporter.cutShort) throw AssistantTurnException(KoogFailures.cutShort(turn.model))
        if (reporter.filtered) throw AssistantTurnException(KoogFailures.FILTERED)
        Unit
    }

    override suspend fun compact(
        connection: AssistantConnection,
        model: AssistantModel,
        instructions: String,
        history: List<AssistantMessage>,
    ): String = written(connection, model, instructions, history + AssistantMessage.User(AssistantInstructions.COMPACT_REQUEST), KoogFailures.SUMMARY)

    override suspend fun answer(connection: AssistantConnection, model: AssistantModel, instructions: String, request: String): String =
        written(connection, model, instructions, listOf(AssistantMessage.User(request)), KoogFailures.ANSWER)

    /** One whole reply to [messages], with no tools; [words] say why there is none. */
    private suspend fun written(
        connection: AssistantConnection,
        model: AssistantModel,
        instructions: String,
        messages: List<AssistantMessage>,
        words: KoogFailures.Writing,
    ): String = explained(connection, model.id) {
        // No tools: Koog writes a chat's calls and results into it as text (its MissingToolsConversionStrategy).
        var ended: Message.Assistant? = null
        val agent = agent(connection, model, effort = null, instructions, messages, tools = emptyList(), maxIterations = 3, strategy = KoogTurn.strategy(stream = null)) {
            handleEvents { onLLMCallCompleted { event -> event.response?.let { ended = it } } }
        }
        val cutShort = { ended?.let(KoogHistory::cutShort) == true }
        val filtered = { ended?.let(KoogHistory::filtered) == true }
        val text = ran(stoppedShort = { cutShort() || filtered() }) { agent.use { it.run(START) } }.orEmpty().trim()
        if (cutShort()) throw AssistantTurnException(words.cutShort)
        if (filtered()) throw AssistantTurnException(words.filtered)
        text.ifEmpty { throw AssistantTurnException(words.empty) }
    }

    /**
     * [block]'s result; null when it failed after a reply [stoppedShort] — at the output limit, or by
     * a content filter. Such a reply with no text in it leaves Koog's agent with no edge to take:
     * why it stopped is the reason, said by the caller.
     */
    private suspend fun <T> ran(stoppedShort: () -> Boolean, block: suspend () -> T): T? = try {
        block()
    } catch (failure: Exception) {
        if (failure is CancellationException || !stoppedShort()) throw failure
        null
    }

    private suspend fun agent(
        connection: AssistantConnection,
        model: AssistantModel,
        effort: AssistantEffort?,
        instructions: String,
        messages: List<AssistantMessage>,
        tools: List<AssistantTool>,
        maxIterations: Int,
        strategy: AIAgentGraphStrategy<String, String>,
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
            strategy = strategy,
            toolRegistry = ToolRegistry.builder().tools(tools.map(::KoogTool)).build(),
            installFeatures = features,
        )
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
        /** The agent's input: the turn reads none, the prompt being whole. */
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
