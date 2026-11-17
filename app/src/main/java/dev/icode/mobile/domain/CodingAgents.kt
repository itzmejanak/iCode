package dev.icode.mobile.domain

/**
 * The coding agents iCode lists (docs/assistant-and-mcp.md), each a runtime tool: installed,
 * updated and removed as the others are (docs/runtime-and-terminal.md). How each is fetched and
 * started is its ACP registry entry, pinned in `assets/agents/registry.json`; this is what the
 * tools page lists and what opens each agent's own CLI.
 */
object CodingAgents {
    /** What `{session}` in [Listed.resume] stands for: the chat's session in the agent. */
    const val SESSION = "{session}"

    /** An agent iCode lists: its registry [id] and [version], and its own CLI. */
    data class Listed(
        val id: String,
        val name: String,
        val version: String,
        val summary: String,
        /** An npm package, run on Node.js; false for a binary. */
        val npm: Boolean,
        /** Its own CLI, by program name: in its install, beside the agent. */
        val cli: String,
        /** Its CLI's arguments that open a session again, [SESSION] in its place; null where none is known. */
        val resume: List<String>? = null,
    ) {
        val tool: String get() = TOOL_PREFIX + id
    }

    val listed: List<Listed> = listOf(
        Listed("claude-acp", "Claude Agent", "0.107.2", "Claude Code through Anthropic's Agent SDK", npm = false, cli = "claude", resume = listOf("--resume", SESSION)),
        Listed("codex-acp", "Codex", "2.7.0", "OpenAI's Codex, through its App Server", npm = true, cli = "codex", resume = listOf("resume", SESSION)),
        Listed("gemini", "Gemini CLI", "0.68.0", "Google's Gemini CLI", npm = true, cli = "gemini"),
        Listed("opencode", "OpenCode", "2.3.0", "The open-source OpenCode agent", npm = false, cli = "opencode"),
        Listed("cline", "Cline", "3.0.74", "The Cline CLI", npm = true, cli = "cline"),
        Listed("factory-droid", "Factory Droid", "1.12.1", "Factory's Droid", npm = true, cli = "droid"),
    )

    /** Their runtime tools, in the order listed. Node.js by its ID: [RuntimeTools] lists these, and is read after. */
    val tools: List<RuntimeTool> = listed.map { agent ->
        RuntimeTool(
            agent.tool, "${agent.name} ${agent.version}", agent.summary, USED_BY, emptyList(),
            setup = agent.tool, requires = if (agent.npm) listOf(NODE_JS) else emptyList(),
        )
    }

    fun byId(id: String?): Listed? = listed.firstOrNull { it.id == id }

    /** The agent a runtime tool installs; null for every other tool. */
    fun ofTool(tool: String): Listed? = listed.firstOrNull { it.tool == tool }

    const val TOOL_PREFIX = "agent-"
    private const val NODE_JS = "nodejs"
    private const val USED_BY = "The assistant's coding agents"
}
