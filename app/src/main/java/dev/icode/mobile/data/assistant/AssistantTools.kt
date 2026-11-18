package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.mcp.McpServers
import dev.icode.mobile.data.assistant.mcp.mcpServerTools
import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.McpRules

/**
 * What a turn's tools are made from (docs/assistant-and-mcp.md): iCode's own — the MCP servers' own management
 * among them (docs/assistant-and-mcp.md) — then its MCP servers'.
 */
internal interface AssistantTools {
    /** The tools a chat in [place] is offered on [step] in [mode], and what their servers tell the model. */
    suspend fun of(place: AssistantWorkspace, chat: String, step: AssistantStep, mode: AssistantMode): Offered

    /**
     * The tools a coding agent answering a chat in [place] is offered on [step] (docs/assistant-and-mcp.md):
     * iCode's own that it has none of, then the user's servers', each as the user chose.
     */
    suspend fun forAgent(place: AssistantWorkspace, chat: String, step: AssistantStep): List<AssistantTool>

    /** The turn that asked ended: a server's question still waiting goes, and its servers idle from now. */
    fun done()

    /** [leftOut]: the servers' tools the mode takes that did not fit under [McpRules.MAX_TOOLS]. */
    class Offered(val tools: List<AssistantTool>, val instructions: String = "", val leftOut: Int = 0)

    companion object {
        /** A reading mode is offered only what reads. */
        fun offers(mode: AssistantMode, tool: AssistantTool): Boolean =
            mode == AssistantMode.Code || mode == AssistantMode.Orchestrator || tool.kind == AssistantToolKind.Read

        /** [own], iCode's tools, first — [agents'][forAgents] for an agent — then [mcp]'s servers', in what room is left ([McpRules.MAX_TOOLS]). */
        fun of(
            own: (AssistantWorkspace, String, AssistantStep) -> List<AssistantTool>,
            mcp: McpServers?,
            forAgents: (AssistantWorkspace, String, AssistantStep) -> List<AssistantTool> = { _, _, _ -> emptyList() },
        ): AssistantTools = object : AssistantTools {
            override suspend fun of(place: AssistantWorkspace, chat: String, step: AssistantStep, mode: AssistantMode): Offered {
                val mine = (own(place, chat, step) + mcp?.let { mcpServerTools(place.place, it) }.orEmpty()).filter { offers(mode, it) }
                val theirs = mcp?.offer(place.place, step, mine.map { it.name }.toSet(), McpRules.MAX_TOOLS - mine.size) { offers(mode, it) }
                return Offered(mine + theirs?.tools.orEmpty(), theirs?.instructions.orEmpty())
            }

            override suspend fun forAgent(place: AssistantWorkspace, chat: String, step: AssistantStep): List<AssistantTool> {
                val mine = forAgents(place, chat, step)
                return mine + mcp?.offer(place.place, step, mine.map { it.name }.toSet(), McpRules.MAX_TOOLS - mine.size) { true }?.tools.orEmpty()
            }

            override fun done() {
                mcp?.idle()
            }
        }
    }
}
