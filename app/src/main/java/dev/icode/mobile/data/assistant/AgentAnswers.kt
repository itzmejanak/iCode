package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.acp.AgentChats
import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantOption
import dev.icode.mobile.domain.McpAnswer
import dev.icode.mobile.domain.McpQuestion

/**
 * How the assistant makes the agents in its chats (docs/assistant-and-mcp.md): from its page and chats, a chat's
 * place, the question card, a summary of older messages by the chat's model, and the tools an agent session is offered.
 */
internal typealias AgentChatsOf = (
    AssistantPage,
    AssistantChats,
    (String?) -> AssistantWorkspace,
    (suspend ((Long) -> McpQuestion) -> McpAnswer)?,
    suspend (List<AssistantMessage>) -> String?,
    suspend (Pair<String?, String>) -> List<AssistantTool>,
) -> AgentChats

/** The coding agents, as a chat's turn asks one to answer (docs/assistant-and-mcp.md). */
internal interface AgentAnswers {
    /** The agent [id]'s name, as the page says it. */
    fun name(id: String): String

    /**
     * [message], after [history], answered by the agent [id] in [chat], in [place]: reported to [events];
     * read to the user when [aloud], so the agent is told to answer as it would say it; with iCode's [tools],
     * gated for the chat, for the calls it makes to them. [approve] asks the user a call's permission with the
     * agent's own options and gives the one chosen.
     */
    suspend fun answer(
        id: String,
        chat: Pair<String?, String>,
        place: AssistantWorkspace,
        history: List<AssistantMessage>,
        message: AssistantMessage.User,
        events: AssistantEvents,
        aloud: Boolean,
        tools: List<AssistantTool>,
        approve: suspend (AssistantCall, List<AssistantOption>) -> String?,
    )

    /** [chat] starts afresh with every agent, from the chat as it stands. */
    fun forget(chat: Pair<String?, String>)
}
