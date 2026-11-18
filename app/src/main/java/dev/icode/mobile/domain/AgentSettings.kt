package dev.icode.mobile.domain

/** One of an agent's settings for a chat (docs/assistant-and-mcp.md): one of its [values], or on and off when it has none. */
data class AgentSetting(
    val id: String,
    val name: String,
    /** "mode", "model" or "thought_level": a chip under the message box; any other is in the / list. */
    val category: String?,
    /** The value chosen: one of [values]' IDs, or "true" or "false" for a switch. */
    val current: String,
    val values: List<AgentValue> = emptyList(),
) {
    val switch: Boolean get() = values.isEmpty()
    val chip: Boolean get() = !switch && category in AgentSettings.CHIPS
}

data class AgentValue(val id: String, val name: String, val description: String? = null)

/** A command an agent takes as the message — "/review" — with what it does and what to write after it. */
data class AgentCommand(val name: String, val description: String, val hint: String? = null)

enum class AgentPlanStatus { Pending, Working, Done }

/** One entry of the to-do list an agent keeps while it works. */
data class AgentPlanStep(val text: String, val status: AgentPlanStatus)

/** What the agent answering the chat shown offers it, once it has started for it: its settings and commands. */
data class AgentChat(val agent: String, val settings: List<AgentSetting> = emptyList(), val commands: List<AgentCommand> = emptyList())

/** The agent chat's settings: where each shows, what every chat starts with, and the values said to act unasked. */
object AgentSettings {
    /** The categories that are chips, in their order; an agent names them so in the protocol. */
    val CHIPS = listOf("mode", "model", "thought_level")

    /** The ID iCode gives an agent's modes when it offers them as modes rather than as a setting. */
    const val MODE = "mode"

    /**
     * What every chat with an agent starts with, whatever the agent would pick itself: the values that ask
     * before changes, as each agent's published package or source names them (docs/roadmap.md). An agent
     * not listed starts as it starts.
     */
    val ASKING: Map<String, Map<String, String>> = mapOf(
        "claude-acp" to mapOf(MODE to "default"),
        "codex-acp" to mapOf(MODE to "read-only"),
        "gemini" to mapOf(MODE to "default"),
        "cline" to mapOf("auto_approve" to "false"),
    )

    private const val EDITS = "Changes files without asking you"
    private const val DECIDES = "Decides itself what is safe, and may act without asking you"
    private const val EVERYTHING = "Changes files and runs commands without asking you"

    /** The values that let an agent act without asking, by agent, setting and value, and the line said with each. */
    private val WARNED: Map<String, Map<Pair<String, String>, String>> = mapOf(
        "claude-acp" to mapOf((MODE to "acceptEdits") to EDITS, (MODE to "auto") to DECIDES, (MODE to "bypassPermissions") to EVERYTHING),
        "codex-acp" to mapOf((MODE to "workspace-write") to EDITS, (MODE to "agent") to DECIDES, (MODE to "agent-full-access") to EVERYTHING),
        "gemini" to mapOf((MODE to "autoEdit") to EDITS, (MODE to "yolo") to EVERYTHING),
        "cline" to mapOf(("auto_approve" to "true") to EVERYTHING),
    )

    /** The line said with [value] of [setting] for [agent]; null for one that asks. */
    fun warning(agent: String, setting: String, value: String): String? = WARNED[agent]?.get(setting to value)

    /** How far the to-do list has come: "2/5"; null for no list. */
    fun progress(plan: List<AgentPlanStep>): String? =
        plan.takeIf { it.isNotEmpty() }?.let { steps -> "${steps.count { it.status == AgentPlanStatus.Done }}/${steps.size}" }

    /** The commands whose names begin with what follows "/" in [draft]; none once a space ends the name, or without "/". */
    fun commandsFor(draft: String, commands: List<AgentCommand>): List<AgentCommand> {
        if (!draft.startsWith("/") || draft.contains(' ')) return emptyList()
        val typed = draft.drop(1)
        return commands.filter { it.name.startsWith(typed, ignoreCase = true) }
    }
}
