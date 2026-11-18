package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.Cost
import com.agentclientprotocol.model.CreateElicitationRequest
import com.agentclientprotocol.model.CreateElicitationResponse
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantInstructions
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.domain.AgentCommand
import dev.icode.mobile.domain.AssistantAgent
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

/**
 * A message answered by a coding agent (docs/assistant-and-mcp.md), in the chat's session with it: a
 * session made for it carries the conversation so far before the message. The agent's updates are kept
 * as the chat's replies and steps ([AcpReplies]); a permission request is asked with the agent's own
 * options, never answered for the user; Stop is sent to it as a cancel.
 */
@OptIn(UnstableApi::class)
internal class AcpTurn(
    private val agents: AcpAgents,
    /** Each agent's context window, as it last reported it, kept between its sessions. */
    private val windows: Windows = Windows.None,
    /** Older messages summarized by the chat's model, for an agent the whole chat would not fit; null with none to write it. */
    private val summarise: suspend (List<AssistantMessage>) -> String? = { null },
) {
    /** Where each agent's window is kept. */
    interface Windows {
        fun of(agent: String): Long?

        fun keep(agent: String, tokens: Long)

        object None : Windows {
            override fun of(agent: String): Long? = null

            override fun keep(agent: String, tokens: Long) = Unit
        }
    }

    /** Each chat's session cost as last reported: a reply shows what it added. */
    private val costs = ConcurrentHashMap<Pair<String?, String>, Cost>()

    /**
     * [message], after [history], answered by [agent] in [chat], in [place]; read to the user when [aloud].
     * [approve] asks the user a call's permission with the agent's options and gives the one chosen, or
     * null when none is.
     */
    suspend fun answer(
        agent: String,
        chat: Pair<String?, String>,
        place: AssistantWorkspace,
        history: List<AssistantMessage>,
        message: AssistantMessage.User,
        events: AssistantEvents,
        aloud: Boolean = false,
        approve: suspend (AssistantCall, List<AssistantOption>) -> String?,
    ) = agents.use(agent) { running ->
        // Another answerer wrote since this agent last did: its session missed that, so it starts afresh.
        if ((history.lastOrNull { it is AssistantMessage.Reply } as? AssistantMessage.Reply)?.let { it.by != AssistantAgent(agent) } == true) forget(chat)
        val (session, first) = agents.session(running, chat, place.guestPath)
        if (first) costs.remove(chat)
        val replies = AcpReplies(events) { path -> path.removePrefix(place.guestPath + "/").takeIf { it != path } }
        replies.began(costs[chat])
        val operations = session.operations as ChatOperations
        operations.asking = { update, options -> ask(update, options, approve) }
        var stopped: StopReason? = null
        try {
            val conversation = if (first && history.isNotEmpty()) AcpHandover.of(history, windows.of(agent), summarise) else null
            session.prompt(listOf(ContentBlock.Text(prompt(conversation, message, aloud)))).collect { event ->
                when (event) {
                    is Event.SessionUpdateEvent -> {
                        replies.on(event.update)
                        operations.heard(event.update)
                    }
                    is Event.PromptResponseEvent -> stopped = event.response.stopReason
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { runCatching { session.cancel() } }
            throw cancelled
        } finally {
            operations.asking = null
            withContext(NonCancellable) {
                replies.end()
                replies.lastCost?.let { costs[chat] = it }
                replies.window?.let { runCatching { windows.keep(agent, it) } }
            }
        }
        when (stopped) {
            StopReason.REFUSAL -> throw AssistantTurnException("The agent refused to go on with this")
            StopReason.MAX_TOKENS -> throw AssistantTurnException("The agent's reply reached its length limit")
            StopReason.MAX_TURN_REQUESTS -> throw AssistantTurnException("The agent reached its own limit of steps")
            else -> Unit
        }
    }

    /** [chat] starts afresh with every agent: Retry and Edit send it the chat as it stands. */
    fun forget(chat: Pair<String?, String>) {
        costs.remove(chat)
        agents.forget(chat)
    }

    private suspend fun ask(
        update: SessionUpdate.ToolCallUpdate,
        options: List<PermissionOption>,
        approve: suspend (AssistantCall, List<AssistantOption>) -> String?,
    ): RequestPermissionResponse {
        val call = AssistantCall(
            id = update.toolCallId.value,
            tool = AcpReplies.toolOf(update.kind),
            arguments = update.rawInput?.toString() ?: "{}",
            summary = update.title ?: "The agent asks to go on",
            kind = AcpReplies.kindOf(update.kind),
        )
        val choices = options.map { option ->
            val allows = option.kind == PermissionOptionKind.ALLOW_ONCE || option.kind == PermissionOptionKind.ALLOW_ALWAYS
            AssistantOption(option.optionId.value, option.name, allows, always = option.kind == PermissionOptionKind.ALLOW_ALWAYS || option.kind == PermissionOptionKind.REJECT_ALWAYS)
        }
        val chosen = approve(call, choices) ?: return RequestPermissionResponse(RequestPermissionOutcome.Cancelled)
        return RequestPermissionResponse(RequestPermissionOutcome.Selected(PermissionOptionId(chosen)))
    }

    companion object {
        /**
         * What the agent is sent: in a new session, the [conversation] so far first ([AcpHandover]); then the
         * message, with its text files — and, for an answer read aloud, how to say it, as the built-in
         * assistant is told. A picture or a document is named, not sent.
         */
        fun prompt(conversation: String?, message: AssistantMessage.User, aloud: Boolean = false): String = buildString {
            if (conversation != null) {
                appendLine("The conversation so far in this chat, for context:")
                appendLine()
                appendLine(conversation)
                appendLine("The message to answer now:")
                appendLine()
            }
            append(message.text)
            if (aloud) {
                appendLine()
                appendLine()
                append(AssistantInstructions.READ_ALOUD)
            }
            message.attachments.forEach { file ->
                appendLine()
                appendLine()
                if (file.kind == AssistantAttachmentKind.Text) {
                    appendLine("Attached file ${file.name}:")
                    appendLine("```")
                    appendLine(file.text)
                    append("```")
                } else {
                    append("Attached ${file.name}: pictures and documents are not sent to agents.")
                }
            }
        }
    }
}

/**
 * What a chat's session does for the agent: its permission requests go to the turn in hand, and none is
 * answered without one; its commands are kept as it says them, and [changed] is told of them and of its
 * settings changing, in a turn or between turns.
 */
internal class ChatOperations(
    val agent: String,
    /** The chat it serves: a new chat's until its first message names it ([AcpAgents.NEW_CHAT]). */
    @Volatile var chat: Pair<String?, String>,
    private val changed: (Pair<String?, String>) -> Unit,
    /** The agent's question, put to the user ([AcpQuestions]). */
    private val asked: suspend (CreateElicitationRequest) -> CreateElicitationResponse = { AcpQuestions.CANCELLED },
) : ClientSessionOperations {
    @Volatile var asking: (suspend (SessionUpdate.ToolCallUpdate, List<PermissionOption>) -> RequestPermissionResponse)? = null

    /** Told every update while the agent replays the session as it loads it, for a chat to take it up with all that was said. */
    @Volatile var replaying: ((SessionUpdate) -> Unit)? = null

    /**
     * The mode iCode set, until the agent says another: the protocol's answer to setting one carries none,
     * and the SDK records a mode only as the agent reports it.
     */
    @Volatile var mode: String? = null

    /** The commands the agent offers in this session, as it last said them. */
    @Volatile var commands: List<AgentCommand> = emptyList()
        private set

    override suspend fun requestPermissions(toolCall: SessionUpdate.ToolCallUpdate, permissions: List<PermissionOption>, _meta: JsonElement?) =
        asking?.invoke(toolCall, permissions) ?: RequestPermissionResponse(RequestPermissionOutcome.Cancelled)

    override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) {
        replaying?.invoke(notification)
        heard(notification)
    }

    override suspend fun createElicitation(request: CreateElicitationRequest): CreateElicitationResponse = asked(request)

    fun heard(update: SessionUpdate) {
        when (update) {
            is SessionUpdate.AvailableCommandsUpdate -> commands = AcpSettings.commandsOf(update.availableCommands)
            is SessionUpdate.CurrentModeUpdate -> mode = null
            is SessionUpdate.ConfigOptionUpdate -> Unit
            else -> return
        }
        changed(chat)
    }
}
