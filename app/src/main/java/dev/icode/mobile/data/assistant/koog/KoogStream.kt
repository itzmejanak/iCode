package dev.icode.mobile.data.assistant.koog

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame

/**
 * A streamed reply put together (docs/runtime-foundation-plan.md §3.8): its reasoning and its text as
 * their deltas wrote them, each call as Koog's stream completed it, and how it ended. Koog's own
 * `toMessageResponse()` throws on a call whose arguments are not JSON; here they reach Koog's tool
 * runner as sent, which answers the model about them, and blank ones are `{}`.
 */
internal class KoogStream {
    private val reasoning = StringBuilder()
    private val text = StringBuilder()
    private val calls = mutableListOf<MessagePart.Tool.Call>()
    private var end: StreamFrame.End? = null

    /** Takes [frame]; true when what the page shows of the reply grew. */
    fun add(frame: StreamFrame): Boolean = when (frame) {
        is StreamFrame.TextDelta -> {
            text.append(frame.text)
            frame.text.isNotEmpty()
        }
        is StreamFrame.ReasoningDelta -> {
            val piece = frame.text ?: frame.summary.orEmpty()
            reasoning.append(piece)
            piece.isNotEmpty()
        }
        is StreamFrame.ToolCallComplete -> {
            calls += MessagePart.Tool.Call(id = frame.id, tool = frame.name, args = frame.content.ifBlank { EMPTY_ARGUMENTS })
            false
        }
        is StreamFrame.End -> {
            end = frame
            false
        }
        // The completed text and reasoning repeat what their deltas wrote; a call's deltas are Koog's to join.
        is StreamFrame.TextComplete, is StreamFrame.ReasoningComplete, is StreamFrame.ToolCallDelta -> false
    }

    /**
     * The reasoning and the answer so far, as the page shows them: a leading `<think>` block in the
     * text is reasoning, as in a whole reply ([KoogHistory.thinking]).
     */
    fun shown(): Pair<String, String> {
        val (thought, answer) = KoogHistory.thinking(text.toString().trim())
        return listOf(reasoning.toString().trim(), thought).filter(String::isNotEmpty).joinToString("\n\n") to answer
    }

    /** The whole reply, its reasoning with it: what [KoogHistory.reply] reads. */
    fun reply(): Message.Assistant = message(withReasoning = true)

    /** The reply as the prompt goes on with it: its reasoning stays within the turn it was given in. */
    fun echo(): Message.Assistant = message(withReasoning = false)

    private fun message(withReasoning: Boolean) = Message.Assistant(
        parts = buildList {
            if (withReasoning && reasoning.isNotEmpty()) add(MessagePart.Reasoning(content = listOf(reasoning.toString())))
            if (text.isNotEmpty()) add(MessagePart.Text(text.toString()))
            addAll(calls)
        },
        metaInfo = end?.metaInfo ?: ResponseMetaInfo.Empty,
        finishReason = end?.finishReason,
    )

    private companion object {
        const val EMPTY_ARGUMENTS = "{}"
    }
}
