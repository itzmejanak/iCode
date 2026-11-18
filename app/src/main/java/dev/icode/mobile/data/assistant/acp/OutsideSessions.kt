package dev.icode.mobile.data.assistant.acp

import dev.icode.mobile.data.assistant.AssistantChats
import dev.icode.mobile.data.assistant.AssistantPage
import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.runtime.shellLine
import dev.icode.mobile.domain.AgentFinds
import dev.icode.mobile.domain.AgentFound
import dev.icode.mobile.domain.AgentPrograms
import dev.icode.mobile.domain.AssistantAgent
import dev.icode.mobile.domain.InstalledAgent
import dev.icode.mobile.domain.notices.AgentNotices
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * An agent's sessions outside iCode's chats (docs/assistant-and-mcp.md): its own program in a chat's
 * terminal tab, and the conversations it keeps in a chat's place that no chat has — looked for only when
 * the user asks, in the chats' list by date, and taken up as a new chat with all that was said
 * (owner's choices, 2026-11-18).
 */
internal class OutsideSessions(
    private val scope: CoroutineScope,
    private val page: AssistantPage,
    private val chats: AssistantChats,
    private val agents: AcpAgents,
    private val placeOf: (String?) -> AssistantWorkspace,
    private val installed: () -> Map<String, InstalledAgent>,
    /** [command] run in a chat's own terminal tab, opened if it has none. */
    private val runInTab: suspend (workspace: String?, chat: String, command: String) -> Unit,
    private val name: (String) -> String,
) {
    /**
     * [agent]'s own program in the chat shown's terminal tab ([AgentPrograms]), with the variables its
     * receipt records — not the keys kept in iCode, which the tab would show. One that takes up the chat's
     * session stops the agent first, so the session has one writer; the chat's next message takes it up
     * again with what was done in the terminal. An agent at work in a chat is not stopped.
     */
    fun openProgram(agent: String) {
        val shown = page.current
        val session = shown.session ?: return
        val install = installed()[agent] ?: return
        val kept = chats.of(shown.workspace, session).agentSessions[agent]?.takeIf { AgentPrograms.resumes(agent) }
        val command = AgentPrograms.command(agent, install.command, kept) ?: return
        if (kept != null && shown.turns.any { it.by == AssistantAgent(agent) }) return page.tell(AgentNotices.working(name(agent)))
        scope.launch {
            if (kept != null) agents.restart(agent)
            runCatching { runInTab(shown.workspace, session, shellLine(command, install.env)) }
                .onFailure { page.tell(AgentNotices.notOpened(name(agent), it.message ?: it.javaClass.simpleName)) }
        }
    }

    /** Every installed agent asked for its conversations in the place shown that no chat there keeps; each listed as it answers. */
    fun find() {
        val workspace = page.current.workspace
        val place = placeOf(workspace)
        page.state { it.copy(agentFinds = AgentFinds(workspace)) }
        scope.launch {
            val kept = chats.agentSessions(workspace)
            installed().keys.map { agent ->
                async {
                    runCatching { agents.listed(agent, place.guestPath) }
                        .onSuccess { listed ->
                            val found = listed.filterNot { it.sessionId.value in kept }.map { AgentFound(agent, it.sessionId.value, it.title?.takeIf(String::isNotBlank) ?: "${name(agent)} chat", timeOf(it.updatedAt)) }
                            finds(workspace) { it.copy(found = it.found + found) }
                        }
                        .onFailure { page.tell(AgentNotices.notFound(name(agent), it.message ?: it.javaClass.simpleName)) }
                }
            }.awaitAll()
            finds(workspace) { it.copy(looking = false) }
        }
    }

    /**
     * [found] taken up as a new chat in the place shown, answered by its agent: loaded, never made anew,
     * its conversation replayed into the chat's messages, and the chat shown.
     */
    fun takeUp(found: AgentFound) {
        val workspace = page.current.workspace
        val place = placeOf(workspace)
        val chat = chats.newSession()
        chats.choose(workspace, chat, AssistantAgent(found.agent))
        chats.agentSession(workspace, chat, found.agent, found.session)
        val replay = AcpReplay(found.agent) { path -> path.removePrefix(place.guestPath + "/").takeIf { it != path } }
        finds(workspace) { it.copy(opening = found.session) }
        scope.launch {
            runCatching { agents.use(found.agent) { agents.prepare(it, workspace to chat, place.guestPath, replay::on) } }
                .onSuccess {
                    chats.begin(workspace, chat, replay.messages())
                    finds(workspace) { finds -> finds.copy(found = finds.found.filterNot { it.session == found.session }, opening = null) }
                    page.display(workspace, chat)
                }
                .onFailure {
                    finds(workspace) { it.copy(opening = null) }
                    page.tell(AgentNotices.notFound(name(found.agent), it.message ?: it.javaClass.simpleName))
                }
        }
    }

    /** The conversations found in [workspace]'s place changed, newest first, while they are the ones listed. */
    private fun finds(workspace: String?, change: (AgentFinds) -> AgentFinds) = page.state { state ->
        val finds = state.agentFinds?.takeIf { it.workspace == workspace } ?: return@state state
        state.copy(agentFinds = change(finds).let { it.copy(found = it.found.sortedByDescending(AgentFound::updatedAt)) })
    }

    /** An agent's ISO 8601 time, as the list sorts by; 0 for none or one it cannot read. */
    private fun timeOf(time: String?): Long = time?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
}
