package dev.icode.mobile.domain

/**
 * An MCP server the assistant uses (docs/mcp-plan.md §5.1): one the user added to iCode, or one a
 * workspace's `.mcp.json` declares ([workspace], M6). Its secret values are never here: only whether
 * one is saved.
 */
data class McpServer(
    /** iCode's own: a UUID for one the user added, `<workspace>#<name>` for a workspace's. */
    val id: String,
    /** "GitHub": what the user named it, and how cards and steps say it. */
    val name: String,
    /** "github": its tools' prefix for the model ([McpRules.key]), unique among the servers. */
    val key: String,
    val transport: McpTransport,
    val enabled: Boolean = true,
    /** The user's choice per tool, by the server's own name for it; a tool not named is on and asks first. */
    val tools: Map<String, McpToolChoice> = emptyMap(),
    val toolTimeoutSeconds: Int = McpRules.TOOL_TIMEOUT_SECONDS,
    /** The workspace whose `.mcp.json` declares it; null for one the user added. */
    val workspace: String? = null,
) {
    fun choice(tool: String): McpToolChoice = tools[tool] ?: McpToolChoice.AsksFirst
}

/** How iCode reaches a server. */
sealed interface McpTransport {
    /** A program in the Linux runtime, spoken to over its stdin and stdout. */
    data class Local(
        val command: String,
        val args: List<String> = emptyList(),
        /** Its working folder in the runtime; null: `/root`. */
        val folder: String? = null,
        val env: List<McpValue> = emptyList(),
    ) : McpTransport

    /** An address over HTTP: Streamable HTTP, or the older HTTP+SSE it falls back to. */
    data class Remote(
        val url: String,
        val headers: List<McpValue> = emptyList(),
        /** An OAuth client ID the user registered with the server's sign-in, when it asks for one (M5). */
        val clientId: String? = null,
    ) : McpTransport
}

/**
 * An environment variable or a header. A secret's [value] is never in the snapshot: there it is null,
 * and [saved] says whether one is kept. Sent to save, a secret's [value] replaces the kept one, and
 * null keeps it.
 */
data class McpValue(
    val name: String,
    val value: String? = null,
    val secret: Boolean = true,
    val saved: Boolean = false,
    /** What a pasted configuration wrote for it, such as `Bearer ${GITHUB_TOKEN}`: the form the value takes. */
    val hint: String? = null,
) {
    /** A secret is never written out, should a value ever be. */
    override fun toString(): String = "McpValue($name, ${if (secret) "secret" else value}, saved=$saved)"
}

/** What the user chose for one tool: never offered, asking first, or running without asking. */
enum class McpToolChoice(val label: String) {
    Off("Off"),
    AsksFirst("Asks first"),
    Runs("Runs"),
}

/** Where a server's connection is. */
enum class McpStatus { Off, Starting, Ready, SignIn, Trust, Failed }

/** One of a server's tools, as its page lists it. */
data class McpToolInfo(
    val name: String,
    /** The server's display name for it: its `title`, else its annotations' title. */
    val title: String?,
    val description: String,
    /** What the server says of it: hints, never trusted to skip a question (§2.1). */
    val readOnly: Boolean = false,
    val destructive: Boolean = false,
    /** Why it is left out of chats: a schema iCode cannot read. */
    val problem: String? = null,
) {
    val shown: String get() = title ?: name
}

/** A prompt a server offers the user (M8): its name and what it asks for. */
data class McpPromptInfo(val name: String, val title: String?, val description: String, val arguments: List<McpPromptArgument>)

data class McpPromptArgument(val name: String, val description: String, val required: Boolean)

/** A resource a server offers (M8), to attach to a message. */
data class McpResourceInfo(val uri: String, val name: String, val title: String?, val description: String, val mimeType: String?)

/** A server's connection now, as its rows and its page show it. */
data class McpServerState(
    val status: McpStatus = McpStatus.Off,
    /** Why it failed, in [McpRules]' and the failures' words. */
    val failure: String? = null,
    /** "server-everything 2.2.1": the server's own name and version. */
    val serverInfo: String? = null,
    val tools: List<McpToolInfo> = emptyList(),
    val instructions: String? = null,
    val prompts: List<McpPromptInfo> = emptyList(),
    val resources: List<McpResourceInfo> = emptyList(),
)

/** A local server's log, as its page shows it: the last lines its stderr wrote. */
data class McpLog(val server: String, val lines: List<String>)

/**
 * A question a server asks the user mid-call (M4, elicitation): a form of [fields], or a page of its
 * own at [url]. Answered with [McpAnswer].
 */
data class McpQuestion(
    val id: Long,
    val server: String,
    val message: String,
    val fields: List<McpField> = emptyList(),
    /** A URL question: the page the server sends the user to, opened only when they choose to. */
    val url: String? = null,
) {
    /** The address's host, shown before anything opens. */
    val host: String? get() = url?.substringAfter("://")?.substringBefore('/')?.substringBefore('?')
}

/** One field of a form question: the restricted schema elicitation allows (flat, primitive). */
data class McpField(
    val name: String,
    val title: String,
    val description: String,
    val kind: Kind,
    val required: Boolean,
    /** For a choice: each value and what it is called. */
    val options: List<Pair<String, String>> = emptyList(),
    val default: String? = null,
) {
    enum class Kind { Text, Email, Uri, Date, DateTime, Integer, Number, Flag, Choice, Choices }
}

/** The user's answer to a [McpQuestion]: Submit with the form's values, Decline, or Cancel. */
data class McpAnswer(val id: Long, val action: Action, val values: Map<String, String> = emptyMap()) {
    enum class Action { Accept, Decline, Cancel }
}

/** One server found in the MCP Registry (M7), with what adding it needs. */
data class McpRegistryEntry(
    val name: String,
    val title: String?,
    val description: String,
    val version: String,
    /** The server as it would be added: a local command or a remote address, values to fill. */
    val server: McpServer,
)

/** A search of the MCP Registry, as the add sheet shows it. */
data class McpRegistrySearch(
    val query: String,
    val loading: Boolean = true,
    val entries: List<McpRegistryEntry> = emptyList(),
    val failure: String? = null,
)

/** Everything MCP the pages and the chat show. */
data class McpState(
    val servers: List<McpServer> = emptyList(),
    val states: Map<String, McpServerState> = emptyMap(),
    /** The remote servers signed in (M5), by ID. */
    val signedIn: Set<String> = emptySet(),
    /** The server whose page is open: one saved, or one being added ([McpServer.id] ""). */
    val editing: McpServer? = null,
    /** That server's log. */
    val log: McpLog? = null,
    val question: McpQuestion? = null,
    val registry: McpRegistrySearch? = null,
    /** A prompt's or a resource's text (M8), for the composer to take into its draft. */
    val draft: String? = null,
    /** The servers' tools the last chat left out, over [McpRules.MAX_TOOLS] a request with iCode's own. */
    val leftOut: Int = 0,
) {
    fun state(id: String): McpServerState = states[id] ?: McpServerState()

    /** The tools on across the servers that are on, as the pages count them. */
    val toolsOn: Int
        get() = servers.filter { it.enabled }.sumOf { McpRules.toolsOn(it, state(it.id)) }
}
