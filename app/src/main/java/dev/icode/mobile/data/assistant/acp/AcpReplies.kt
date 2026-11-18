package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.Cost
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.model.ToolKind
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.LineChanges
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * One prompt's updates from an agent, as the chat keeps a turn (docs/assistant-and-mcp.md): its
 * message and thought chunks are written as they come and kept as a reply when a tool call begins or
 * the prompt ends; each tool call is a step whose result is kept when the agent says it completed or
 * failed — a diff by the lines it changed, a command's output as printed. [relative] gives a path the
 * agent names relative to the chat's place, or null outside it.
 */
@OptIn(UnstableApi::class)
internal class AcpReplies(private val events: AssistantEvents, private val relative: (String) -> String?) {
    private val text = StringBuilder()
    private val thought = StringBuilder()

    /** The calls asked and not yet answered, by ID, with what the agent last said of each. */
    private val open = LinkedHashMap<String, SessionUpdate.ToolCall>()

    /** The session's cost when the prompt began, and as last reported: the reply's is what it added. */
    private var costAtStart: Cost? = null
    private var cost: Cost? = null

    /** What the session cost before this prompt, as its last report said. */
    fun began(cost: Cost?) {
        costAtStart = cost
        this.cost = cost
    }

    /** The session's cost as last reported, for the next prompt to start from. */
    val lastCost: Cost? get() = cost

    fun on(update: SessionUpdate) {
        when (update) {
            is SessionUpdate.AgentMessageChunk -> {
                text.append(textOf(update.content))
                events.writing(thought.toString(), text.toString())
            }
            is SessionUpdate.AgentThoughtChunk -> {
                thought.append(textOf(update.content))
                events.writing(thought.toString(), text.toString())
            }
            is SessionUpdate.ToolCall -> begin(update)
            is SessionUpdate.ToolCallUpdate -> open[update.toolCallId.value]?.let { asked -> settle(merged(asked, update)) }
            is SessionUpdate.PlanUpdate -> events.planned(AcpSettings.planOf(update.entries))
            is SessionUpdate.UsageUpdate -> {
                events.used(update.used)
                update.cost?.let { cost = it }
            }
            else -> Unit
        }
    }

    /** The prompt ended: what was written since the last step is kept, with what this prompt cost. */
    fun end(): AssistantMessage.Reply? {
        val spent = spent()
        if (text.isEmpty() && thought.isEmpty() && spent == null) return null
        return reply(emptyList(), spent).also(events::replied)
    }

    private fun begin(call: SessionUpdate.ToolCall) {
        val id = call.toolCallId.value
        if (id in open) return settle(call)
        open[id] = call
        val asked = callOf(call)
        events.replied(reply(listOf(asked), cost = null))
        events.calling(asked)
        settle(call)
    }

    /** A call the agent says is done: its result kept, once. */
    private fun settle(call: SessionUpdate.ToolCall) {
        val id = call.toolCallId.value
        open[id] = call
        if (call.status != ToolCallStatus.COMPLETED && call.status != ToolCallStatus.FAILED) return
        open.remove(id)
        val kind = kindOf(call.kind)
        val output = outputOf(call)
        events.answered(
            AssistantResult(
                id, toolOf(call.kind), output, failed = call.status == ToolCallStatus.FAILED,
                changes = changesOf(call.content), printed = output.takeIf { kind == AssistantToolKind.Command && it.isNotEmpty() },
            ),
        )
    }

    private fun reply(calls: List<AssistantCall>, cost: String?): AssistantMessage.Reply =
        AssistantMessage.Reply(text.toString(), calls, thought.toString(), cost = cost).also {
            text.clear()
            thought.clear()
        }

    /** What this prompt added to the session's cost, in its currency: "0.04 USD", "<0.01 USD"; null when unknown or nothing. */
    private fun spent(): String? {
        val now = cost ?: return null
        val before = costAtStart?.takeIf { it.currency == now.currency }?.amount ?: 0.0
        val added = now.amount - before
        return when {
            added <= 0.0 -> null
            added < CENT / 2 -> "<0.01 ${now.currency}"
            else -> String.format(Locale.ROOT, "%.2f %s", added, now.currency)
        }
    }

    private fun callOf(call: SessionUpdate.ToolCall) = AssistantCall(
        id = call.toolCallId.value,
        tool = toolOf(call.kind),
        arguments = call.rawInput?.toString() ?: "{}",
        summary = call.title,
        kind = kindOf(call.kind),
        file = call.locations.firstNotNullOfOrNull { relative(it.path) },
        command = (call.rawInput as? JsonObject)?.get("command")?.let(::commandOf).takeIf { kindOf(call.kind) == AssistantToolKind.Command },
    )

    private fun merged(call: SessionUpdate.ToolCall, update: SessionUpdate.ToolCallUpdate) = call.copy(
        title = update.title ?: call.title,
        kind = update.kind ?: call.kind,
        status = update.status ?: call.status,
        content = update.content ?: call.content,
        locations = update.locations ?: call.locations,
        rawInput = update.rawInput ?: call.rawInput,
        rawOutput = update.rawOutput ?: call.rawOutput,
    )

    companion object {
        private const val CENT = 0.01

        /** Reads, searches, fetches and thinking change nothing; edits, deletes and moves change files; execute runs a command. */
        fun kindOf(kind: ToolKind?): AssistantToolKind = when (kind) {
            ToolKind.EDIT, ToolKind.DELETE, ToolKind.MOVE -> AssistantToolKind.Change
            ToolKind.EXECUTE -> AssistantToolKind.Command
            else -> AssistantToolKind.Read
        }

        /** The tool, as the protocol names its kind: "edit", "execute". */
        fun toolOf(kind: ToolKind?): String = kind?.name?.lowercase(Locale.ROOT) ?: "other"

        fun textOf(content: ContentBlock): String = when (content) {
            is ContentBlock.Text -> content.text
            is ContentBlock.ResourceLink -> content.uri
            else -> ""
        }

        /** What a call gave back: its text, each file it changed, or what it reported raw. */
        fun outputOf(call: SessionUpdate.ToolCall): String {
            val parts = call.content.mapNotNull { content ->
                when (content) {
                    is ToolCallContent.Content -> textOf(content.content).ifEmpty { null }
                    is ToolCallContent.Diff -> "${content.path}: ${LineChanges.between(content.oldText, content.newText).label}"
                    is ToolCallContent.Terminal -> null
                }
            }
            return parts.joinToString("\n").ifEmpty { call.rawOutput?.let(::plain).orEmpty() }
        }

        /** The lines its diffs added and removed, together; null for a call that changed no file. */
        fun changesOf(content: List<ToolCallContent>): LineChanges? = content.filterIsInstance<ToolCallContent.Diff>()
            .map { LineChanges.between(it.oldText, it.newText) }
            .takeIf { it.isNotEmpty() }
            ?.let { all -> LineChanges(all.sumOf { it.added }, all.sumOf { it.removed }) }

        private fun commandOf(value: kotlinx.serialization.json.JsonElement): String? = when (value) {
            is JsonArray -> value.joinToString(" ") { it.jsonPrimitive.contentOrNull.orEmpty() }
            is JsonPrimitive -> value.contentOrNull
            else -> null
        }

        /** Raw output as text: a string as it is, anything else as its JSON. */
        private fun plain(value: kotlinx.serialization.json.JsonElement): String = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: value.toString()
    }
}
