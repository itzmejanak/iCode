package dev.icode.mobile.data.assistant.koog

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import dev.icode.mobile.data.assistant.AssistantInstructions
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.tools.bounded
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantToolKind
import java.io.File
import org.json.JSONObject

/**
 * The chat in iCode's words ↔ Koog's messages (§4.2). Tool-call IDs pass through exactly: a provider
 * matches a result to its call by ID. Reasoning stays within the turn it was given in.
 */
internal object KoogHistory {
    /**
     * The prompt a request starts from: the system instructions, then [messages] — every call
     * answered, the new message last — for [model]: an image or a PDF goes as itself to a model
     * that reads it, and as a line of text to one that does not (§13 P8).
     */
    fun prompt(instructions: String, messages: List<AssistantMessage>, params: LLMParams, model: LLModel): Prompt = prompt(PROMPT_ID, params) {
        system(instructions)
        messages.forEach { message ->
            when (message) {
                is AssistantMessage.User -> user(listOf(MessagePart.Text(AssistantInstructions.message(message))) + message.attachments.mapNotNull { attachment(it, model) })
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
     * [attachment] as a part of its message: an image or a PDF as itself where [model] reads it,
     * else as a line saying it is there; a text file is already in the message's text.
     */
    private fun attachment(attachment: AssistantAttachment, model: LLModel): MessagePart.RequestPart? {
        val kept = attachment.file?.let(::File)?.takeIf { it.isFile }
        return when (attachment.kind) {
            AssistantAttachmentKind.Text -> null
            AssistantAttachmentKind.Image -> when {
                !model.supports(LLMCapability.Vision.Image) -> note(attachment, "this model cannot see images")
                kept == null -> note(attachment, "it is no longer kept")
                else -> MessagePart.Attachment(
                    AttachmentSource.Image(AttachmentContent.Binary.Bytes(kept.readBytes()), FORMAT_JPEG, attachment.mimeType, attachment.name),
                )
            }
            AssistantAttachmentKind.Document -> when {
                !model.supports(LLMCapability.Document) -> note(attachment, "this model cannot read PDFs")
                kept == null -> note(attachment, "it is no longer kept")
                else -> MessagePart.Attachment(
                    AttachmentSource.File(AttachmentContent.Binary.Bytes(kept.readBytes()), FORMAT_PDF, attachment.mimeType, attachment.name),
                )
            }
        }
    }

    private fun note(attachment: AssistantAttachment, why: String) =
        MessagePart.Text("[The user attached ${attachment.name}, not sent: $why.]")

    /**
     * [reply] as iCode keeps it: its text, each call described by the tool it calls, with an ID from
     * [newId] where the provider gave none, and its reasoning. A call's arguments are not checked
     * here — a malformed one reaches Koog's tool runner, which answers the model about it.
     *
     * Reasoning is what the provider sent as such — Anthropic's thinking, Gemini's thoughts,
     * Ollama's thinking, an OpenAI-compatible server's `reasoning_content` — or a leading
     * `<think>` block some models write into their text instead ([thinking]). Encrypted reasoning
     * has no text to show and is left out.
     */
    fun reply(reply: Message.Assistant, tools: Map<String, AssistantTool>, newId: () -> String): AssistantMessage.Reply {
        val (thought, text) = thinking(reply.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }.trim())
        val reasoning = reply.parts.filterIsInstance<MessagePart.Reasoning>()
            .map { part -> part.content.ifEmpty { part.summary.orEmpty() }.joinToString("\n") }
            .plus(thought)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n\n")
        return AssistantMessage.Reply(
            text = text,
            reasoning = bounded(reasoning, MAX_REASONING),
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
    }

    /**
     * [text]'s leading `<think>…</think>` — how some models (DeepSeek's R1, QwQ, MiniMax) write
     * their reasoning when the server does not take it apart — and the answer after it. A block
     * never closed, as in a reply cut short, is all reasoning.
     */
    fun thinking(text: String): Pair<String, String> {
        if (!text.startsWith(THINK_OPEN)) return "" to text
        val end = text.indexOf(THINK_CLOSE)
        if (end < 0) return text.removePrefix(THINK_OPEN).trim() to ""
        return text.substring(THINK_OPEN.length, end).trim() to text.substring(end + THINK_CLOSE.length).trim()
    }

    /** The tokens [reply]'s request read and wrote — the context it filled — when the provider said. */
    fun tokens(reply: Message.Assistant): Long? = with(reply.metaInfo) {
        inputTokensCount?.let { it.toLong() + (outputTokensCount ?: 0) } ?: totalTokensCount?.toLong()
    }

    /**
     * [reply] ended at its request's output limit, as its provider said: `length` for OpenAI,
     * OpenRouter and OpenAI-compatible servers, `max_tokens` for Anthropic, `MAX_TOKENS` for Gemini.
     * Koog's Ollama client gives no reason, so a cut-short Ollama reply is not recognized.
     */
    fun cutShort(reply: Message.Assistant): Boolean = reply.finishReason?.lowercase() in OUTPUT_LIMIT_REASONS

    private const val PROMPT_ID = "icode-assistant"
    private const val THINK_OPEN = "<think>"
    private const val THINK_CLOSE = "</think>"

    /** Reasoning kept for the page: its start and its end past this many characters. */
    private const val MAX_REASONING = 32_000
    private val OUTPUT_LIMIT_REASONS = setOf("length", "max_tokens")

    /** Every image is sent as JPEG ([dev.icode.mobile.data.assistant.AssistantImages]). */
    private const val FORMAT_JPEG = "jpeg"
    private const val FORMAT_PDF = "pdf"
}
