package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantToolKind
import org.json.JSONObject

/**
 * The chat in iCode's words ↔ Koog's messages (§4.2). Tool-call IDs pass through exactly: a provider
 * matches a result to its call by ID. Reasoning stays within the turn it was given in.
 */
internal object KoogHistory {
    fun prompt(instructions: String, history: List<AssistantMessage>): Prompt = prompt(PROMPT_ID) {
        system(instructions)
        history.forEach { message ->
            when (message) {
                is AssistantMessage.User -> user(message.text)
                is AssistantMessage.Reply -> message(
                    Message.Assistant(
                        parts = buildList {
                            if (message.text.isNotBlank()) add(MessagePart.Text(message.text))
                            message.calls.forEach { add(MessagePart.Tool.Call(id = it.id, tool = it.tool, args = it.arguments)) }
                        },
                        metaInfo = ResponseMetaInfo.Empty,
                    ),
                )
                is AssistantMessage.Results -> user(
                    message.results.map { MessagePart.Tool.Result(id = it.callId, tool = it.tool, output = it.output, isError = it.failed) },
                )
            }
        }
    }

    /**
     * A streamed reply, whole: Koog's complete frames, its text from the deltas if a client sent no
     * complete one, and an ID for each call that came without one. A call's arguments are never
     * parsed here — a malformed one reaches the tool runner, which answers the model about it.
     */
    fun reply(frames: List<StreamFrame>, nextId: () -> String): Message.Assistant {
        var end: StreamFrame.End? = null
        val parts = frames.mapNotNull { frame ->
            when (frame) {
                is StreamFrame.ReasoningComplete -> MessagePart.Reasoning(
                    content = frame.content,
                    summary = frame.summary,
                    encrypted = frame.encrypted,
                    id = frame.id,
                )
                is StreamFrame.TextComplete -> MessagePart.Text(frame.text)
                is StreamFrame.ToolCallComplete -> MessagePart.Tool.Call(
                    id = frame.id?.takeIf { it.isNotBlank() } ?: nextId(),
                    tool = frame.name,
                    args = frame.content.ifBlank { "{}" },
                )
                is StreamFrame.End -> {
                    end = frame
                    null
                }
                else -> null
            }
        }
        val streamedOnly = frames.filterIsInstance<StreamFrame.TextDelta>().joinToString("") { it.text }
        val withText = if (parts.none { it is MessagePart.Text } && streamedOnly.isNotBlank()) listOf(MessagePart.Text(streamedOnly)) + parts else parts
        return Message.Assistant(withText, end?.metaInfo ?: ResponseMetaInfo.Empty, end?.finishReason)
    }

    /** [reply] as iCode keeps it: its text, and each call described by the tool it calls. */
    fun reply(reply: Message.Assistant, tools: Map<String, AssistantTool>): AssistantMessage.Reply = AssistantMessage.Reply(
        text = reply.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }.trim(),
        calls = reply.parts.filterIsInstance<MessagePart.Tool.Call>().map { call ->
            val tool = tools[call.tool]
            AssistantCall(
                id = call.id.orEmpty(),
                tool = call.tool,
                arguments = call.args,
                summary = tool?.let { runCatching { it.summary(JSONObject(call.args)) }.getOrNull() } ?: call.tool,
                // A tool that does not exist is answered by the runner without running anything.
                kind = tool?.kind ?: AssistantToolKind.Read,
            )
        },
    )

    fun results(calls: List<AssistantCall>, received: List<ReceivedToolResult>): AssistantMessage.Results =
        AssistantMessage.Results(
            calls.zip(received) { call, result ->
                AssistantResult(call.id, call.tool, result.output, failed = result.resultKind !is ToolResultKind.Success)
            },
        )

    private const val PROMPT_ID = "icode-assistant"
}
