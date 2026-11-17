package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AgentIntent
import kotlinx.coroutines.CoroutineScope

/**
 * The coding agents a chat can be answered by instead of the built-in assistant (docs/assistant-and-mcp.md),
 * as [AssistantTurns] and [AssistantCoordinator] see them: data/assistant/acp/ implements it. A turn in such a
 * chat is the same one-turn job, page, notices and clean-up as any other; only what answers it differs.
 */
internal interface AssistantAgents {
    /** The agents listed, and the one new chats go to, on the page. */
    fun start() = Unit

    fun handle(intent: AgentIntent) = Unit

    /** [session] of [workspace] is answered by a coding agent. */
    fun answers(workspace: String?, session: String): Boolean

    /** [session], a new chat of [workspace], goes to the agent new chats go to, if one is chosen: false when none is. */
    fun takes(workspace: String?, session: String): Boolean = false

    /**
     * [input], with the files at [attachments], added to the chat and sent to its agent: what the agent
     * does is told to [events] as it happens, and where it is to [step]. Cancelling the job cancels the prompt.
     */
    suspend fun converse(workspace: String?, session: String, input: String, attachments: List<String>, events: AssistantEvents, step: (String) -> Unit)

    /** The chat's last message, unanswered, sent to its agent again. */
    suspend fun retry(workspace: String?, session: String, events: AssistantEvents, step: (String) -> Unit)

    /** The page shows [session] of [workspace] — null for a new chat: what its agent offers is shown with it. */
    fun shown(workspace: String?, session: String?) = Unit

    /** A provider's model was chosen: new chats go to the built-in assistant. */
    fun chooseBuiltIn() = Unit

    /** A workspace was renamed or moved, or deleted: its chats' sessions follow it, or go. */
    fun moved(from: String, to: String) = Unit
    fun forgotten(workspace: String) = Unit

    /** No agents: every chat is the built-in assistant's (tests that need none). */
    object None : AssistantAgents {
        override fun answers(workspace: String?, session: String) = false
        override suspend fun converse(workspace: String?, session: String, input: String, attachments: List<String>, events: AssistantEvents, step: (String) -> Unit) =
            throw AssistantTurnException("No coding agent answers this chat")
        override suspend fun retry(workspace: String?, session: String, events: AssistantEvents, step: (String) -> Unit) =
            throw AssistantTurnException("No coding agent answers this chat")
    }
}

/** What answers a chat when a coding agent does: the agent, and its own session once it gave one. */
internal data class AgentBinding(val agent: String, val session: String? = null)

/** What the assistant gives its coding agents (docs/assistant-and-mcp.md): its page, chats and questions, and its places and files. */
internal class AssistantAgentsContext(
    val stateHolder: ICodeStateHolder,
    val scope: CoroutineScope,
    val page: AssistantPage,
    val chats: AssistantChats,
    val questions: AssistantQuestions,
    /** Where a chat of a workspace — null for none — works, as the tools see it. Throws when the workspace is gone. */
    val workspaceOf: (String?) -> AssistantWorkspace,
    val attachments: AssistantAttachments,
)
