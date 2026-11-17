package dev.icode.mobile.domain

// External coding agents through the Agent Client Protocol (docs/assistant-and-mcp.md): the agents
// iCode lists, what each is doing, and what the agent answering the shown chat offers the page. Only
// data/assistant/acp/ speaks the protocol; nothing here names it.

/** A coding agent iCode lists, as its ACP registry entry describes it (docs/assistant-and-mcp.md). */
data class CodingAgent(
    /** The registry's ID: what a chat records as answering it. */
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    /** The licence its registry entry records: "MIT", "Apache-2.0", "proprietary". */
    val licence: String,
    /** Its licence or terms of service. */
    val terms: String?,
    val website: String?,
    /** Its runtime tool, by ID: what installs, updates and removes it (docs/runtime-and-terminal.md). */
    val tool: String,
    /** An npm package: it runs on the Node.js runtime tool. False for an ARM64 Linux binary. */
    val npm: Boolean,
)

/** One agent's process, as Settings › Coding Agents shows it. */
data class AgentStatus(
    val state: State = State.Off,
    val failure: String? = null,
    /** What the agent said it is when it started: its name and version. */
    val identity: String? = null,
    /** How it signs in, as it said when it last started; empty until then. */
    val signIn: List<AgentSignIn> = emptyList(),
) {
    enum class State { Off, Starting, Ready, Failed }
}

/** A way an agent signs in, as it offers it (ACP `authMethods`). */
data class AgentSignIn(
    val id: String,
    val name: String,
    val description: String? = null,
    val kind: Kind,
    /** For [Kind.Keys]: the variables it reads, entered in iCode and kept encrypted. */
    val variables: List<String> = emptyList(),
    /** Where its keys come from, for [Kind.Keys]. */
    val link: String? = null,
) {
    enum class Kind {
        /** The agent signs in itself, often in the browser. */
        Agent,

        /** The agent's own login, in a terminal tab. */
        Terminal,

        /** Keys in its environment. */
        Keys,
    }
}

/** A setting the agent offers for a chat: its mode, its model, its effort, or one of its own. */
sealed interface AgentOption {
    val id: String
    val name: String
    val description: String?

    /** "mode", "model", "thought_level", or the agent's own; null when it gives none. */
    val category: String?

    data class Select(
        override val id: String,
        override val name: String,
        override val description: String? = null,
        override val category: String? = null,
        val current: String,
        val choices: List<Choice>,
    ) : AgentOption {
        data class Choice(val value: String, val name: String, val description: String? = null, val group: String? = null)

        /** The current choice's name, or its value when the agent did not list it. */
        val currentName: String get() = choices.firstOrNull { it.value == current }?.name ?: current
    }

    data class Toggle(
        override val id: String,
        override val name: String,
        override val description: String? = null,
        override val category: String? = null,
        val on: Boolean,
    ) : AgentOption
}

/** A slash command the agent offers: sent as the message `/name`. */
data class AgentCommand(val name: String, val description: String, val hint: String? = null)

/** One step of the agent's plan, as it last sent it. */
data class AgentPlanItem(val text: String, val status: Status, val priority: Priority = Priority.Medium) {
    enum class Status { Pending, InProgress, Completed }
    enum class Priority { High, Medium, Low }
}

/** The agent answering the shown chat, and what it offers there. */
data class AgentChat(
    val workspace: String?,
    /** iCode's ID for the chat; null for a new chat, which the chosen agent will answer. */
    val session: String?,
    /** The agent's ID ([CodingAgent.id]). */
    val agent: String,
    /** The chat is being read back from the agent (ACP `session/load`). */
    val loading: Boolean = false,
    val failure: String? = null,
    val options: List<AgentOption> = emptyList(),
    val commands: List<AgentCommand> = emptyList(),
    val plan: List<AgentPlanItem> = emptyList(),
    /** The context window the agent last reported, in tokens. */
    val window: Long? = null,
    /** The agent asks to sign in before it goes on: how it can. */
    val signIn: List<AgentSignIn> = emptyList(),
    /** The sign-in method in hand, by ID. */
    val signingIn: String? = null,
) {
    fun shows(workspace: String?, session: String?): Boolean = this.workspace == workspace && this.session == session
}

/** A link an agent asked to open — a sign-in page, as a rule: shown, and opened only when the user taps it. */
data class AgentLink(val agent: String, val url: String) {
    /** Its host, as the card names it. */
    val host: String get() = url.substringAfter("://").substringBefore('/').substringBefore('?')
}

/** The last lines an agent wrote to its log (its standard error), as its settings page shows them. */
data class AgentLog(val agent: String, val lines: List<String>)

/** What an agent's approval offers: its own options, by its ID for each. */
data class AssistantApprovalChoice(val id: String, val label: String, val kind: Kind) {
    enum class Kind(val allows: Boolean) { AllowOnce(true), AllowAlways(true), RejectOnce(false), RejectAlways(false) }
}

/** The coding agents, as the page and their settings see them. */
data class AgentsState(
    /** The agents iCode lists, verified on its runtime (docs/assistant-and-mcp.md). */
    val agents: List<CodingAgent> = emptyList(),
    /** What answers new chats: an agent's ID; null for the built-in assistant. */
    val chosen: String? = null,
    /** Each agent's process, by ID; absent when it has not started since iCode did. */
    val status: Map<String, AgentStatus> = emptyMap(),
    /** The shown chat's agent; null when the built-in assistant answers it. */
    val chat: AgentChat? = null,
    /** The agent whose settings page is open. */
    val editing: String? = null,
    val log: AgentLog? = null,
    /** A question an agent asks, on the card MCP servers' questions use. */
    val question: McpQuestion? = null,
    /** The last link an agent asked to open, until it is opened or set aside. */
    val link: AgentLink? = null,
    /** Each agent's keys, by ID: names and whether one is saved, never the values. */
    val keys: Map<String, List<McpValue>> = emptyMap(),
) {
    fun agent(id: String?): CodingAgent? = agents.firstOrNull { it.id == id }

    /** The agent a new chat would go to, when one is chosen and listed. */
    val chosenAgent: CodingAgent? get() = agent(chosen)

    /** [workspace]'s shown chat [session] is an agent's: its controls replace the built-in assistant's. */
    fun chatOf(workspace: String?, session: String?): AgentChat? = chat?.takeIf { it.shows(workspace, session) }
}
