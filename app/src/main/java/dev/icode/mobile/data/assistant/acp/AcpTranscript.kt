package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import dev.icode.mobile.domain.AgentCommand
import dev.icode.mobile.domain.AgentOption
import dev.icode.mobile.domain.AgentPlanItem
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantToolKind

/**
 * What an agent's session updates make of its chat (docs/assistant-and-mcp.md), as a turn goes or as a
 * session is read back: its words and thoughts become a reply, each tool call a step of it and its end
 * the step's result, what the user said a message; its settings, commands, plan and usage go to the page.
 *
 * Each result stays right after its reply, as the chat keeps them: a call started while another of the
 * reply waits joins that reply, and words said meanwhile wait for the calls to end. Not thread-safe: its
 * owner feeds it one update at a time.
 */
@OptIn(UnstableApi::class)
internal class AcpTranscript(private val cwd: String, private val sink: Sink) {
    /** Where the transcript goes: a turn's events and its chat, or a chat being read back. */
    interface Sink {
        fun writing(reasoning: String, text: String) = Unit
        fun replied(reply: AssistantMessage.Reply)

        /** [call] joins the last reply's calls. */
        fun extended(call: AssistantCall)

        fun calling(call: AssistantCall) = Unit
        fun answered(result: AssistantResult)
        fun said(message: AssistantMessage.User)
        fun used(used: Long, size: Long) = Unit
        fun plan(plan: List<AgentPlanItem>) = Unit
        fun commands(commands: List<AgentCommand>) = Unit
        fun options(options: List<AgentOption>) = Unit

        /** The agent changed its mode itself, as ACP had modes before config options. */
        fun mode(current: String) = Unit
    }

    private val text = StringBuilder()
    private val reasoning = StringBuilder()
    private val user = StringBuilder()
    private val userFiles = mutableListOf<AssistantAttachment>()
    private val calls = linkedMapOf<String, AcpToolCall>()

    /** The calls of the last reply recorded here; a call may join it. */
    private val lastReply = mutableListOf<AcpToolCall>()

    fun update(update: SessionUpdate) {
        when (update) {
            is SessionUpdate.UserMessageChunk -> said(update.content)
            is SessionUpdate.AgentMessageChunk -> spoke(text, update.content)
            is SessionUpdate.AgentThoughtChunk -> spoke(reasoning, update.content)
            is SessionUpdate.ToolCall -> {
                flushUser()
                val call = calls.getOrPut(update.toolCallId.value) { AcpToolCall(update.toolCallId.value) }
                call.merge(update.title, update.kind, update.status, update.content, update.locations.map { it.path }, update.rawInput, update.rawOutput)
                record(call)
            }
            is SessionUpdate.ToolCallUpdate -> {
                flushUser()
                val call = calls.getOrPut(update.toolCallId.value) { AcpToolCall(update.toolCallId.value) }
                call.merge(update.title, update.kind, update.status, update.content, update.locations?.map { it.path }, update.rawInput, update.rawOutput)
                record(call)
            }
            is SessionUpdate.PlanUpdate -> sink.plan(AcpMapping.plan(update.entries))
            is SessionUpdate.AvailableCommandsUpdate -> sink.commands(AcpMapping.commands(update.availableCommands))
            is SessionUpdate.ConfigOptionUpdate -> sink.options(update.configOptions.map(AcpMapping::option))
            is SessionUpdate.CurrentModeUpdate -> sink.mode(update.currentModeId.value)
            is SessionUpdate.UsageUpdate -> sink.used(update.used, update.size)
            else -> Unit // A session's title, a plan of another shape, and what iCode does not show yet.
        }
    }

    /**
     * The turn, or the reading back, ended: what the user said last, and what the agent said after its
     * last call, recorded. A call it never ended stays open: the chat closes it as stopped.
     */
    fun finish() {
        flushUser()
        if (text.isNotBlank() || reasoning.isNotBlank()) {
            sink.replied(AssistantMessage.Reply(text.toString().trim(), emptyList(), reasoning.toString().trim()))
            lastReply.clear()
        }
        text.clear()
        reasoning.clear()
    }

    /** The calls this transcript recorded that changed files and ended well: the workspace is read again. */
    val changedFiles: Boolean get() = calls.values.any { it.answered && it.recorded?.kind == AssistantToolKind.Change }

    /** The call of [id] as recorded, for an approval asked of it before its update arrived. */
    fun recorded(id: String): AssistantCall? = calls[id]?.recorded

    private fun spoke(into: StringBuilder, content: ContentBlock) {
        flushUser()
        into.append(AcpMapping.text(content))
        sink.writing(reasoning.toString(), text.toString())
    }

    private fun said(content: ContentBlock) {
        when (content) {
            is ContentBlock.ResourceLink -> userFiles += AssistantAttachment(content.title ?: content.name, inWorkspace = true)
            is ContentBlock.Image -> userFiles += AssistantAttachment("Image", AssistantAttachmentKind.Image, mimeType = content.mimeType)
            else -> {
                // What the agent says, then the user: the agent's reply ends where the user starts.
                if (text.isNotBlank() || reasoning.isNotBlank()) finishReply()
                user.append(AcpMapping.text(content))
            }
        }
    }

    private fun flushUser() {
        if (user.isBlank() && userFiles.isEmpty()) return
        sink.said(AssistantMessage.User(user.toString().trim(), userFiles.toList()))
        user.clear()
        userFiles.clear()
        lastReply.clear()
    }

    private fun finishReply() {
        if (lastReply.any { !it.answered }) return
        sink.replied(AssistantMessage.Reply(text.toString().trim(), emptyList(), reasoning.toString().trim()))
        text.clear()
        reasoning.clear()
        lastReply.clear()
    }

    /** [call] in the chat once: as the next step of its reply, then its result once it ends. */
    private fun record(call: AcpToolCall) {
        val row = call.recorded ?: AcpMapping.call(call, cwd).also { row ->
            call.recorded = row
            val words = text.isNotBlank() || reasoning.isNotBlank()
            val waiting = lastReply.any { !it.answered }
            if (lastReply.isNotEmpty() && (!words || waiting)) {
                sink.extended(row)
            } else {
                sink.replied(AssistantMessage.Reply(text.toString().trim(), listOf(row), reasoning.toString().trim()))
                text.clear()
                reasoning.clear()
                lastReply.clear()
            }
            lastReply += call
        }
        sink.calling(row)
        if (call.done && !call.answered) {
            call.answered = true
            sink.answered(AcpMapping.result(call, row))
        }
    }
}
