package dev.icode.mobile.domain

/**
 * An agent's own program, opened in a chat's terminal tab (docs/assistant-and-mcp.md), as each agent's
 * published release has it: an adapter's program lies beside it and takes up the chat's session by its
 * ID — Claude Agent's archive holds `claude` beside `claude-agent-acp`, Codex's package links `codex`
 * beside `codex-acp`; an agent that is its own program is that program without the arguments that make
 * it speak ACP, opened afresh, as none of them takes up a session by its ID.
 */
object AgentPrograms {
    /** Agents whose ACP program is their own, opened afresh. */
    private val OWN = setOf("gemini", "cline", "opencode", "factory-droid")

    /** Whether [agent]'s program takes up a chat's session by its ID. */
    fun resumes(agent: String): Boolean = agent == CLAUDE || agent == CODEX

    /**
     * The command that opens [agent], installed to run [acp], in a terminal: taking up [session] where
     * it can; null for an agent whose program iCode does not know.
     */
    fun command(agent: String, acp: List<String>, session: String?): List<String>? {
        val program = acp.firstOrNull() ?: return null
        val beside = { name: String -> program.substringBeforeLast('/') + "/" + name }
        return when (agent) {
            CLAUDE -> listOf(beside("claude")) + session?.let { listOf("--resume", it) }.orEmpty()
            CODEX -> listOf(beside("codex")) + session?.let { listOf("resume", it) }.orEmpty()
            in OWN -> listOf(program)
            else -> null
        }
    }

    private const val CLAUDE = "claude-acp"
    private const val CODEX = "codex-acp"
}
