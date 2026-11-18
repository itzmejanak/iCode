package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantOption

/** The coding agents, as a chat's turn asks one to answer (docs/assistant-and-mcp.md). */
internal interface AgentAnswers {
    /** The agent [id]'s name, as the page says it. */
    fun name(id: String): String

    /**
     * [message], after [history], answered by the agent [id] in [chat], in [place]: reported to [events];
     * [approve] asks the user a call's permission with the agent's own options and gives the one chosen.
     */
    suspend fun answer(
        id: String,
        chat: Pair<String?, String>,
        place: AssistantWorkspace,
        history: List<AssistantMessage>,
        message: AssistantMessage.User,
        events: AssistantEvents,
        approve: suspend (AssistantCall, List<AssistantOption>) -> String?,
    )

    /** [chat] starts afresh with every agent, from the chat as it stands. */
    fun forget(chat: Pair<String?, String>)
}
