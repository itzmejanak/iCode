package dev.icode.mobile.domain

/**
 * A coding agent as the ACP registry describes its newest release (docs/assistant-and-mcp.md), with
 * how that release installs on this phone: [source], or null when it has no build for it.
 */
data class CodingAgent(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    /** Where to read about it: its website, or its source. */
    val website: String?,
    val license: String,
    val licenseUrl: String,
    val source: AgentSource?,
)

/** How an agent's release installs in the runtime, and the arguments and variables it starts with. */
sealed interface AgentSource {
    val args: List<String>
    val env: Map<String, String>

    /** Its Linux ARM64 build: [archive], verified by its [sha256], holding [command] — or the program itself. */
    data class Binary(
        val archive: String,
        val sha256: String,
        val command: String,
        override val args: List<String>,
        override val env: Map<String, String>,
    ) : AgentSource

    /** The npm package [name] at exactly [version], installed with the runtime's Node.js. */
    data class Npm(val name: String, val version: String, override val args: List<String>, override val env: Map<String, String>) : AgentSource
}

/** An installed agent, as its receipt keeps it: its version, how it starts in the runtime, and the space it takes. */
data class InstalledAgent(val version: String, val command: List<String>, val env: Map<String, String>, val bytes: Long)

/** What an agent's work in hand is doing, and how far it is. */
data class AgentWork(val action: AgentAction, val progress: ToolSetupProgress)

enum class AgentAction(val doing: String) {
    Install("Installing"),
    Update("Updating"),
    Remove("Removing"),
}

/** Settings › Assistant › Coding agents (docs/assistant-and-mcp.md). */
data class CodingAgentsState(
    /** The agents iCode offers, as the registry last said them. */
    val catalogue: List<CodingAgent> = emptyList(),
    val installed: Map<String, InstalledAgent> = emptyMap(),
    val work: Map<String, AgentWork> = emptyMap(),
    /** Each release's download, by agent, where the registry makes it known: a build's archive. */
    val sizes: Map<String, Long> = emptyMap(),
    val checking: Boolean = false,
    /** Why the registry could not be read; what is shown is then the list last kept. */
    val checkFailure: String? = null,
) {
    /** The installed agents the registry has a newer release of. */
    val updates: List<CodingAgent> get() = CodingAgents.updates(catalogue, installed)
}

/** Settings › Assistant › Coding agents' requests, in the assistant's order. */
sealed interface AgentIntent : ICodeIntent.AssistantIntent {
    /** The page opened: the registry is read again. */
    data object Check : AgentIntent

    /** [id]'s newest release installed, or put in place of the one installed. */
    data class Install(val id: String) : AgentIntent

    data object UpdateAll : AgentIntent

    /** [id] removed; with [everything], its own folders in the runtime's home too: sign-in, settings and sessions. */
    data class Remove(val id: String, val everything: Boolean) : AgentIntent

    /** [id]'s work in hand stopped, where its step allows. */
    data class Cancel(val id: String) : AgentIntent
}

/** Which agents iCode offers, and what is theirs in the runtime (docs/assistant-and-mcp.md). */
object CodingAgents {
    /** The agents offered, by their registry IDs, in the order the page lists them (owner's choice, 2026-11-17). */
    val OFFERED = listOf("claude-acp", "codex-acp", "gemini", "opencode", "cline", "factory-droid")

    /** The agents verified on phones; every other is marked Untested. None is yet. */
    val VERIFIED = emptySet<String>()

    /**
     * Each agent's own folders and files in the runtime's home, relative to it, as its source or its
     * maker's documentation names them: removed with everything, they take its sign-in, settings and
     * sessions. A folder a variable moves elsewhere is not followed.
     */
    val OWN = mapOf(
        "claude-acp" to listOf(".claude", ".claude.json"),
        "codex-acp" to listOf(".codex"),
        "gemini" to listOf(".gemini"),
        "opencode" to listOf(".local/share/opencode", ".config/opencode", ".cache/opencode", ".local/state/opencode"),
        "cline" to listOf(".cline"),
        "factory-droid" to listOf(".factory"),
    )

    /** The installed agents [catalogue] has another release of: the registry's is the newest. */
    fun updates(catalogue: List<CodingAgent>, installed: Map<String, InstalledAgent>): List<CodingAgent> =
        catalogue.filter { agent -> installed[agent.id]?.let { it.version != agent.version } == true }

    /**
     * What [agent]'s row says it is now, with the space it takes, or what it downloads where that is known
     * ([download]): a build's archive, not an npm package's, whose dependencies npm finds as it installs.
     */
    fun status(agent: CodingAgent, installed: InstalledAgent?, download: Long?): String {
        val fetches = download?.let { " · ${ByteSize.format(it)} download" }.orEmpty()
        return when {
            installed == null -> if (agent.source == null) "No build for this phone" else "Not installed · ${agent.version}$fetches"
            installed.version == agent.version -> "Installed · ${installed.version} · ${ByteSize.format(installed.bytes)}"
            else -> "${installed.version} · update to ${agent.version}$fetches"
        }
    }
}
