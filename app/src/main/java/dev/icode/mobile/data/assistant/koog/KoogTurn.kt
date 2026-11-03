package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.http.client.KoogHttpClientException
import io.ktor.client.plugins.sse.SSEClientException
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.IncompleteStreamException
import ai.koog.serialization.JSONPrimitive
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantPicture
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantResult
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

/** A request of the turn's: whole, or one that streams ([KoogTurn.streamed]). */
internal typealias KoogRequest = suspend AIAgentLLMWriteSession.() -> Message.Assistant

/**
 * One turn on Koog's agent (§12 R1): its strategy, its requests, and what it reports
 * (docs/runtime-foundation-plan.md §3.8).
 */
internal object KoogTurn {
    /**
     * Koog's `singleRunStrategy()`, its own nodes and edges, with two changes. It opens with a node
     * that only requests — Koog's `reActStrategy` has one — since the message, with its images and
     * files, is already the prompt's last (`nodeLLMRequest` would add a text-only one). And after
     * tool results it checks a reply's calls before its text: Koog's checks the text first and
     * finishes, leaving the calls of a reply with both ("Now the tests:" and a call — how models
     * work) unrun, as it already does after the first request. Its requests are [stream]'s where it
     * is given; Koog's whole-reply request otherwise.
     */
    fun strategy(
        stream: KoogRequest?,
        picture: () -> AssistantPicture? = { null },
        place: PicturePlace = PicturePlace.None,
    ): AIAgentGraphStrategy<String, String> = strategy("icode-turn") {
        val request by node<String, Message.Assistant> { llm.writeSession { stream?.invoke(this) ?: requestLLM() } }
        val runTools by nodeExecuteTools()
        // Koog's nodeLLMSendToolResults: the results as one user message, then the next request. The
        // screen the step's calls left goes with them, where the client takes a picture (L2).
        val sendResults by node<ReceivedToolResults, Message.Assistant> { received ->
            // Taken whether or not it is sent, so a later step never sends an older one.
            val screen = picture()?.takeIf { place != PicturePlace.None }?.let(::screenPart)
            llm.writeSession {
                // Only the latest screen is ever in the prompt; the user's own photos stay.
                if (screen != null) rewritePrompt { prompt -> prompt.withMessages { messages -> messages.map(::withoutScreens) } }
                val results = received.toolResults.map { it.toMessagePart() }
                appendPrompt {
                    user {
                        results.forEachIndexed { index, result ->
                            val last = index == results.lastIndex
                            toolResult(if (screen != null && place == PicturePlace.InResult && last) result.copy(parts = result.parts + screen) else result)
                        }
                        if (screen != null && place == PicturePlace.Beside) attachment(screen.source)
                    }
                }
                stream?.invoke(this) ?: requestLLM()
            }
        }
        edge(nodeStart forwardTo request)
        edge(request forwardTo runTools onToolCalls { true })
        edge(request forwardTo nodeFinish onTextMessage { true })
        edge(runTools forwardTo sendResults)
        edge(sendResults forwardTo runTools onToolCalls { true })
        edge(sendResults forwardTo nodeFinish onTextMessage { true })
    }

    /** Where a step's picture goes for the model a turn asks (docs/phone-agent-plan.md §3.6, read in Koog's clients). */
    enum class PicturePlace {
        /** A model that sees no pictures: it is dropped. */
        None,

        /** In the message with the results: Chat Completions (a user message after the tool messages), Anthropic, Gemini, Ollama. */
        Beside,

        /** In the last result itself: OpenAI's Responses API, whose results take an image and whose messages beside them do not. */
        InResult,
    }

    /** [kind]'s client for [model], which [sees] pictures or not: where a step's picture goes. */
    fun picturePlace(kind: AssistantProviderKind, model: LLModel, sees: Boolean): PicturePlace = when {
        !sees -> PicturePlace.None
        kind == AssistantProviderKind.OpenAI && !model.supports(LLMCapability.OpenAIEndpoint.Completions) &&
            model.supports(LLMCapability.OpenAIEndpoint.Responses) -> PicturePlace.InResult
        else -> PicturePlace.Beside
    }

    /** [picture] as the part a prompt carries, named so an older one is known again ([withoutScreens]). */
    private fun screenPart(picture: AssistantPicture) = MessagePart.Attachment(
        AttachmentSource.Image(AttachmentContent.Binary.Bytes(picture.bytes), KoogHistory.FORMAT_JPEG, picture.mimeType, SCREEN_NAME),
    )

    private fun MessagePart.isScreen(): Boolean = ((this as? MessagePart.Attachment)?.source as? AttachmentSource.Image)?.fileName == SCREEN_NAME

    /** [message] without a step's screen: a request's own, or one in a tool's result. */
    private fun withoutScreens(message: Message): Message = when (message) {
        is Message.User -> message.copy(
            parts = message.parts.filterNot { it.isScreen() }.map { part ->
                if (part is MessagePart.Tool.Result && part.parts.any { it.isScreen() }) part.copy(parts = part.parts.filterNot { it.isScreen() }) else part
            },
        )
        else -> message
    }

    /**
     * Whether [kind]'s replies stream (docs/runtime-foundation-plan.md §1.1, D8): Koog's streams are
     * sound for OpenRouter and for OpenAI's Chat Completions, which every OpenAI-compatible server
     * speaks; for Anthropic, Gemini and Ollama they lose what a turn needs.
     */
    fun streams(kind: AssistantProviderKind, model: LLModel): Boolean = when (kind) {
        AssistantProviderKind.OpenRouter -> true
        AssistantProviderKind.OpenAI, AssistantProviderKind.OpenAICompatible -> model.supports(LLMCapability.OpenAIEndpoint.Completions)
        AssistantProviderKind.Anthropic, AssistantProviderKind.Google, AssistantProviderKind.Ollama -> false
    }

    /**
     * A request that streams: what is written shown as it comes, at most ten times a second, then
     * the whole reply reported as a whole one is, and added to the prompt without its reasoning.
     *
     * A server that refuses the stream before anything came — a 400 or 422 for a field it does not
     * take (`stream_options`), OpenAI's rule that only verified organizations stream some models, a
     * reply that is not a stream, or one that cannot be read — is asked the same request whole; once
     * that works, [refused] hears it, and the connection is not streamed again.
     *
     * [finishSaid]: the provider ends every reply with a finish reason (OpenRouter, OpenAI), so a
     * stream without one closed early. A compatible server may never send one, and is not held to it.
     */
    fun streamed(reporter: Reporter, finishSaid: Boolean, refused: () -> Unit): KoogRequest {
        // Refused once, the turn's later requests go whole at once.
        var whole = false
        return request@{
            if (whole) return@request requestLLM()
            val stream = KoogStream()
            var received = false
            try {
                coroutineScope {
                    val grown = MutableStateFlow(0)
                    // The state is read when it is shown, never per piece: a long reply comes in thousands.
                    val showing = launch {
                        grown.collect { pieces ->
                            if (pieces == 0) return@collect
                            val (reasoning, text) = synchronized(stream) { stream.shown() }
                            reporter.writing(reasoning, text)
                            delay(LIVE_MILLIS)
                        }
                    }
                    try {
                        requestLLMStreaming().collect { frame ->
                            received = true
                            if (synchronized(stream) { stream.add(frame) }) grown.value += 1
                        }
                    } finally {
                        showing.cancel()
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException || received || !refusesStreams(failure)) throw failure
                // Whole: Koog adds it to the prompt and reports it as it does every whole reply.
                val reply = requestLLM()
                whole = true
                refused()
                return@request reply
            }
            val reply = stream.reply()
            // Koog's clients end a stream that closed early as if it had finished, and keep an upstream
            // failure's reason ("error", OpenRouter's), not its error (F10, F11).
            when {
                reply.finishReason == null && finishSaid -> throw IncompleteStreamException("The stream closed before the reply said how it ended")
                reply.finishReason == UPSTREAM_FAILED -> throw AssistantTurnException(KoogFailures.PROVIDER_FAILED)
            }
            reporter.completed(reply)
            stream.echo().also { appendPrompt { message(it) } }
        }
    }

    /**
     * [failure], before any frame came, is the server refusing to stream: an answer that is not a
     * stream, or not one Koog can read. A stream that broke after it began carries what broke, with
     * the stream's own 200 (F15): its provider's failure, or a connection that broke, is not a refusal.
     */
    private fun refusesStreams(failure: Throwable): Boolean {
        val chain = generateSequence(failure) { it.cause }.take(MAX_CAUSES).toList()
        if (chain.any { it is AssistantTurnException }) return false
        val broke = chain.any { it is SSEClientException && it.cause != null }
        return chain.any { cause ->
            val status = (cause as? KoogHttpClientException)?.statusCode
            cause is SerializationException || (status in REFUSED && !(status == OK && broke))
        }
    }

    /** Koog's events for [reporter]: each whole reply, and each call as it starts and ends. */
    fun GraphAIAgent.FeatureContext.report(reporter: Reporter) {
        handleEvents {
            // A streamed request never completes as a call: its node reports it ([streamed]).
            onLLMCallCompleted { event -> event.response?.let(reporter::completed) }
            onToolCallStarting { event -> reporter.calling(event.toolCallId) }
            onToolCallCompleted { event ->
                reporter.answered(event.toolCallId, (event.toolResult as? JSONPrimitive)?.contentOrNull.orEmpty(), failed = false)
            }
            onToolCallFailed { event -> reporter.answered(event.toolCallId, event.message, failed = true) }
            // Also arguments that are not JSON: Koog refuses those before the call starts.
            onToolValidationFailed { event -> reporter.answered(event.toolCallId, event.message, failed = true) }
        }
    }

    /**
     * Koog's events and streamed replies in iCode's words. Koog runs a reply's calls one at a time,
     * in order; each event names its call by ID, and one the provider gave no ID is the first still open.
     */
    class Reporter(private val tools: Map<String, AssistantTool>, private val events: AssistantEvents) {
        /** Whether the last reply ended at the output limit: then the turn says so under it. */
        @Volatile var cutShort = false
            private set

        /** Whether the last reply was stopped by a content filter: then too. */
        @Volatile var filtered = false
            private set
        private val open = ArrayDeque<AssistantCall>()

        fun writing(reasoning: String, text: String) = events.writing(reasoning, text)

        fun completed(response: Message.Assistant) {
            cutShort = KoogHistory.cutShort(response)
            filtered = KoogHistory.filtered(response)
            KoogHistory.tokens(response)?.let(events::used)
            val reply = KoogHistory.reply(response, tools) { "icode-" + UUID.randomUUID() }
            open.clear()
            open.addAll(reply.calls)
            if (reply.text.isNotEmpty() || reply.calls.isNotEmpty() || reply.reasoning.isNotEmpty()) events.replied(reply)
        }

        fun calling(id: String?) {
            callOf(id)?.let(events::calling)
        }

        fun answered(id: String?, output: String, failed: Boolean) {
            val call = callOf(id) ?: return
            open.remove(call)
            events.answered(AssistantResult(call.id, call.tool, output, failed))
        }

        private fun callOf(id: String?): AssistantCall? = open.firstOrNull { it.id == id } ?: open.firstOrNull()
    }

    /** A step's screen, as its picture is named in the prompt: never a name a user's photo has, which is its own file's. */
    private const val SCREEN_NAME = "icode-screen.jpg"

    /** A streamed reply is shown at most this often. */
    private const val LIVE_MILLIS = 100L

    /** The finish reason of a provider that failed after the reply began (OpenRouter's; relays pass it on). */
    private const val UPSTREAM_FAILED = "error"

    /** A stream refused: 400 and 422 for what a server does not take; 200 for a whole reply where a stream was asked. */
    private val REFUSED = setOf(OK, 400, 422)
    private const val MAX_CAUSES = 8
    private const val OK = 200
}
