package dev.icode.mobile.data.assistant.koog

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import dev.icode.mobile.data.assistant.AssistantInstructions
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantToolKind
import org.json.JSONObject

/**
 * The chat in iCode's words ↔ Koog's messages (§4.2). Tool-call IDs pass through exactly: a provider
 * matches a result to its call by ID. Reasoning stays within the turn it was given in.
 */
internal object KoogHistory {
    /** The prompt a turn starts from: the system instructions, then [history] (every call answered). */
    fun prompt(instructions: String, history: List<AssistantMessage>, params: LLMParams): Prompt = prompt(PROMPT_ID, params) {
        system(instructions)
        history.forEach { message ->
            when (message) {
                is AssistantMessage.User -> user(AssistantInstructions.message(message))
                is AssistantMessage.Reply -> if (message.text.isNotBlank() || message.calls.isNotEmpty()) {
                    message(
                        Message.Assistant(
                            parts = buildList {
                                if (message.text.isNotBlank()) add(MessagePart.Text(message.text))
                                message.calls.forEach { add(MessagePart.Tool.Call(id = it.id, tool = it.tool, args = it.arguments)) }
                            },
                            metaInfo = ResponseMetaInfo.Empty,
                        ),
                    )
                }
                is AssistantMessage.Results -> user(
                    message.results.map { MessagePart.Tool.Result(id = it.callId, tool = it.tool, output = it.output, isError = it.failed) },
                )
                // What a summary covers is never sent: the summary is in the instructions.
                is AssistantMessage.Compacted -> Unit
            }
        }
    }

    /**
     * [reply] as iCode keeps it: its text, and each call described by the tool it calls, with an ID
     * from [newId] where the provider gave none. A call's arguments are not checked here — a
     * malformed one reaches Koog's tool runner, which answers the model about it.
     */
    fun reply(reply: Message.Assistant, tools: Map<String, AssistantTool>, newId: () -> String): AssistantMessage.Reply =
        AssistantMessage.Reply(
            text = reply.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }.trim(),
            calls = reply.parts.filterIsInstance<MessagePart.Tool.Call>().map { call ->
                val tool = tools[call.tool]
                AssistantCall(
                    id = call.id?.takeIf { it.isNotBlank() } ?: newId(),
                    tool = call.tool,
                    arguments = call.args,
                    summary = tool?.let { runCatching { it.summary(JSONObject(call.args)) }.getOrNull() } ?: call.tool,
                    // A tool that does not exist is answered by the runner without running anything.
                    kind = tool?.kind ?: AssistantToolKind.Read,
                )
            },
        )

    /** The tokens [reply]'s request read and wrote — the context it filled — when the provider said. */
    fun tokens(reply: Message.Assistant): Long? = with(reply.metaInfo) {
        inputTokensCount?.let { it.toLong() + (outputTokensCount ?: 0) } ?: totalTokensCount?.toLong()
    }

    private const val PROMPT_ID = "icode-assistant"
}
