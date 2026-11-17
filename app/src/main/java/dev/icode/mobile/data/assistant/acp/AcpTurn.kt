package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import dev.icode.mobile.data.assistant.AssistantChats
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantPage
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.domain.AgentChat
import dev.icode.mobile.domain.AgentCommand
import dev.icode.mobile.domain.AgentOption
import dev.icode.mobile.domain.AgentPlanItem
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import java.io.File
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** What a chat's agent offers the page as it changes — its settings, commands, plan and window — for [ChatKey]. */
internal fun interface AcpOffers {
    fun change(key: ChatKey, change: (AgentChat) -> AgentChat)
}

/**
 * One message of an agent's chat, sent as its prompt (ACP `session/prompt`; docs/assistant-and-mcp.md):
 * what the agent says and does reaches the turn's [events] as the built-in assistant's would, its settings
 * and plan the page. Stopping the turn sends `session/cancel`, and a question still asked is answered
 * Cancel. Why the agent stopped, when not at the end of its turn, is the turn's failure.
 */
@OptIn(UnstableApi::class)
internal class AcpTurn(
    private val entry: AgentEntry,
    private val session: ClientSession,
    private val operations: AcpOperations,
    private val chats: AssistantChats,
    private val page: AssistantPage,
    private val offers: AcpOffers,
    private val questions: AcpQuestions,
) {
    private val key get() = operations.chat

    /** Sends [message]; returns once the agent's turn ended. Throws [AssistantTurnException] with why it stopped early. */
    suspend fun run(message: AssistantMessage.User, events: AssistantEvents): Boolean {
        val transcript = AcpTranscript(operations.cwd, Live(events))
        operations.attach(transcript)
        var stopped: StopReason? = null
        try {
            session.prompt(blocks(message)).collect { event ->
                when (event) {
                    is Event.SessionUpdateEvent -> operations.deliver(event.update)
                    is Event.PromptResponseEvent -> stopped = event.response.stopReason
                }
            }
            operations.settle()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                runCatching { session.cancel() }
                questions.cancel()
            }
            throw cancelled
        } catch (failure: Exception) {
            throw AcpTurnFailure(failure)
        } finally {
            withContext(NonCancellable) {
                operations.finish(transcript)
                operations.attach(null)
            }
        }
        when (stopped) {
            StopReason.MAX_TOKENS -> throw AssistantTurnException("${entry.agent.name} stopped: its reply reached its output limit")
            StopReason.MAX_TURN_REQUESTS -> throw AssistantTurnException("${entry.agent.name} stopped at its limit of steps for one message: send a message to go on")
            StopReason.REFUSAL -> throw AssistantTurnException("${entry.agent.name} refused to go on")
            StopReason.CANCELLED, StopReason.END_TURN, null -> Unit
        }
        return transcript.changedFiles
    }

    /** [message] as the agent reads it: its words, each text file as text, each image as an image. */
    private fun blocks(message: AssistantMessage.User): List<ContentBlock> = buildList {
        if (message.text.isNotBlank()) add(ContentBlock.Text(message.text))
        message.attachments.forEach { attachment ->
            when (attachment.kind) {
                AssistantAttachmentKind.Text -> add(ContentBlock.Text("Attached file ${attachment.name}:\n\n${attachment.text}"))
                AssistantAttachmentKind.Image -> attachment.file?.let { add(ContentBlock.Image(Base64.getEncoder().encodeToString(File(it).readBytes()), attachment.mimeType)) }
                AssistantAttachmentKind.Document -> Unit // Refused before it was read: no agent here reads PDFs through iCode.
            }
        }
    }

    /** The transcript as the turn goes: the turn's events, the chat and the page. */
    private inner class Live(private val events: AssistantEvents) : AcpTranscript.Sink {
        override fun writing(reasoning: String, text: String) = events.writing(reasoning, text)
        override fun replied(reply: AssistantMessage.Reply) = events.replied(reply)
        override fun extended(call: AssistantCall) {
            chats.extend(key.workspace, key.session, call)
            page.refresh(key.workspace, key.session)
        }
        override fun calling(call: AssistantCall) = events.calling(call)
        override fun answered(result: AssistantResult) = events.answered(result)

        /** The agent saying back what the user said: it is in the chat already. */
        override fun said(message: AssistantMessage.User) = Unit

        override fun used(used: Long, size: Long) {
            events.used(used)
            offers.change(key) { it.copy(window = size) }
        }
        override fun plan(plan: List<AgentPlanItem>) = offers.change(key) { it.copy(plan = plan) }
        override fun commands(commands: List<AgentCommand>) = offers.change(key) { it.copy(commands = commands) }
        override fun options(options: List<AgentOption>) = offers.change(key) { it.copy(options = options) }
        override fun mode(current: String) = offers.change(key) { it.copy(options = AcpOffersRules.withMode(it.options, current)) }
    }
}

/** A prompt that failed for a reason of its own: the owner says it in the agent's words. */
internal class AcpTurnFailure(val failure: Exception) : Exception(failure.message, failure)

/** How what an agent offers a chat changes when it says so itself. */
@OptIn(UnstableApi::class)
internal object AcpOffersRules {
    /** [options] with the mode — iCode's own option for an agent's modes — now [current]. */
    fun withMode(options: List<AgentOption>, current: String): List<AgentOption> =
        options.map { if (it is AgentOption.Select && it.id == AcpMapping.MODE) it.copy(current = current) else it }

    /** [update] changes what the chat's agent offers: its commands, settings, plan or window. */
    fun offers(update: SessionUpdate): Boolean = update is SessionUpdate.AvailableCommandsUpdate || update is SessionUpdate.ConfigOptionUpdate ||
        update is SessionUpdate.CurrentModeUpdate || update is SessionUpdate.PlanUpdate || update is SessionUpdate.UsageUpdate

    /** [chat] as [update], arriving between turns, leaves it. */
    fun between(chat: AgentChat, update: SessionUpdate): AgentChat = when (update) {
        is SessionUpdate.AvailableCommandsUpdate -> chat.copy(commands = AcpMapping.commands(update.availableCommands))
        is SessionUpdate.ConfigOptionUpdate -> chat.copy(options = update.configOptions.map(AcpMapping::option))
        is SessionUpdate.CurrentModeUpdate -> chat.copy(options = withMode(chat.options, update.currentModeId.value))
        is SessionUpdate.PlanUpdate -> chat.copy(plan = AcpMapping.plan(update.entries))
        is SessionUpdate.UsageUpdate -> chat.copy(window = update.size)
        else -> chat
    }
}
