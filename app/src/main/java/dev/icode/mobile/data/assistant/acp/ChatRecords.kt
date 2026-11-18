package dev.icode.mobile.data.assistant.acp

import dev.icode.mobile.data.assistant.AssistantChats

/** Each agent's session for a chat, kept with the chat in its list (docs/assistant-and-mcp.md). */
internal class ChatRecords(private val chats: AssistantChats) : AcpAgents.Records {
    override fun of(agent: String, chat: Pair<String?, String>) = chats.of(chat.first, chat.second).agentSessions[agent]

    override fun keep(agent: String, chat: Pair<String?, String>, id: String) = chats.agentSession(chat.first, chat.second, agent, id)

    override fun drop(chat: Pair<String?, String>) = chats.dropAgentSessions(chat.first, chat.second)
}
