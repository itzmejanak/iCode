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
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.IncompleteStreamException
import ai.koog.serialization.JSONPrimitive
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantResult
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
    fun strategy(stream: KoogRequest?): AIAgentGraphStrategy<String, String> = strategy("icode-turn") {
        val request by node<String, Message.Assistant> { llm.writeSession { stream?.invoke(this) ?: requestLLM() } }
        val runTools by nodeExecuteTools()
        // Koog's nodeLLMSendToolResults: the results as one user message, then the next request.
        val sendResults by node<ReceivedToolResults, Message.Assistant> { results ->
            llm.writeSession {
                appendPrompt { user { results.toolResults.forEach { toolResult(it.toMessagePart()) } } }
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

    /**
     * An OpenRouter request that streams: what is written shown as it comes, at most ten times a
     * second, then the whole reply reported as a whole one is, and added to the prompt without its
     * reasoning.
     */
    fun streamed(reporter: Reporter): KoogRequest = {
        val stream = KoogStream()
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
                requestLLMStreaming().collect { frame -> if (synchronized(stream) { stream.add(frame) }) grown.value += 1 }
            } finally {
                showing.cancel()
            }
        }
        val reply = stream.reply()
        // OpenRouter ends every reply with one of five reasons; Koog's client ends a stream that
        // closed early as if it had finished, and keeps an upstream failure's reason, not its error.
        when (reply.finishReason) {
            null -> throw IncompleteStreamException("The stream closed before the reply said how it ended")
            OPENROUTER_FAILED -> throw AssistantTurnException(KoogFailures.PROVIDER_FAILED)
        }
        reporter.completed(reply)
        stream.echo().also { appendPrompt { message(it) } }
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

    /** A streamed reply is shown at most this often. */
    private const val LIVE_MILLIS = 100L

    /** OpenRouter's finish reason for a provider that failed after the reply began. */
    private const val OPENROUTER_FAILED = "error"
}
