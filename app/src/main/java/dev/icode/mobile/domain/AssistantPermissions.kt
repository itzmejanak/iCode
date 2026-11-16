package dev.icode.mobile.domain

/**
 * How much the assistant does without asking (docs/assistant-and-mcp.md). The chat's
 * workspace is the boundary in every mode but [FullAccess], as Codex's presets draw it.
 */
enum class AssistantPermission(val label: String) {
    /** Reads inside the workspace run; changes, commands and anything outside ask. */
    AskFirst("Ask first"),

    /** Reads, changes and commands inside the workspace run; anything outside asks. */
    AutoInWorkspace("Auto in workspace"),

    /** Nothing asks. */
    FullAccess("Full access"),
}

/** What a call waits on before it runs: one card each, never two. */
enum class AssistantAsk { Change, Command, Outside }

/**
 * What a call reaches outside its chat's workspace: [paths] as they resolve in the runtime,
 * and the folders "Allow in this chat" opens ([AssistantPermissions.rootOf]); none where nothing
 * smaller than `/` would do.
 */
data class AssistantReach(
    val paths: List<String>,
    val roots: List<String>,
    /** The place outside the files a call acts in — an app, an MCP server — when it is one; [roots] then hold its root. */
    val place: AssistantElsewhere? = null,
)

/**
 * A place outside the files a call acts in, asked about as a folder outside is
 * (docs/voice-and-phone-control.md, docs/assistant-and-mcp.md): an app on the phone, an MCP server, or a
 * terminal tab of the user's. "Allow in" opens its [root].
 */
data class AssistantElsewhere(val name: String, val root: String, val kind: Kind) {
    enum class Kind { App, Server, Terminal }
}

object AssistantPermissions {
    /**
     * What a call of [kind] asks under [permission], [outside] when it reaches beyond the
     * workspace and this chat has not opened where, [always] for one asked in every mode
     * (docs/assistant-and-mcp.md): null when it runs.
     */
    fun ask(permission: AssistantPermission, kind: AssistantToolKind, outside: Boolean, always: Boolean = false): AssistantAsk? = when {
        always -> if (kind == AssistantToolKind.Command) AssistantAsk.Command else AssistantAsk.Change
        permission == AssistantPermission.FullAccess -> null
        outside -> AssistantAsk.Outside
        permission == AssistantPermission.AutoInWorkspace || kind == AssistantToolKind.Read -> null
        kind == AssistantToolKind.Command -> AssistantAsk.Command
        else -> AssistantAsk.Change
    }

    /** What the model is told of a call the user denied, as it [asked], [outside] what it reached there, or the [place] it acted in. */
    fun denied(asked: AssistantAsk, outside: List<String>, place: AssistantElsewhere? = null): String = when (asked) {
        AssistantAsk.Outside -> when (place?.kind) {
            AssistantElsewhere.Kind.App -> "The user did not allow acting in ${place.name}"
            AssistantElsewhere.Kind.Server -> "The user did not allow using ${place.name}"
            AssistantElsewhere.Kind.Terminal -> "The user did not allow using their terminal ${place.name}"
            null -> "The user did not allow reaching ${outside.joinToString()}"
        }
        AssistantAsk.Command -> "The user did not allow this command"
        AssistantAsk.Change -> "The user did not allow this change"
    }

    /** What [approval]'s card asks — in the chat and in the bubble — [bound] the chat's place as the page says it. */
    fun question(approval: AssistantApproval, bound: String): String {
        val reach = approval.reach
        val kind = approval.call.kind
        return when {
            reach?.place?.kind == AssistantElsewhere.Kind.App -> "Act in ${reach.place.name}?"
            reach?.place?.kind == AssistantElsewhere.Kind.Server -> "Use ${reach.place.name}?"
            reach?.place?.kind == AssistantElsewhere.Kind.Terminal -> "Use your terminal ${reach.place.name}?"
            reach == null && kind == AssistantToolKind.Command -> "Run this command?"
            reach == null -> "Make this change?"
            kind == AssistantToolKind.Command -> "Run this command outside $bound?"
            kind == AssistantToolKind.Change -> "Make this change outside $bound?"
            else -> "Read outside $bound?"
        }
    }

    /**
     * [approval]'s "Allow in" answer, for this chat: in its workspace [bound], its app, or the folders
     * it reaches; null where none would do — a change or a command in a chat in no workspace ([bound]
     * null) has none to be allowed in.
     */
    fun allowIn(approval: AssistantApproval, bound: String?): String? {
        val reach = approval.reach
        return when {
            approval.always -> null
            reach == null -> bound?.let { "Allow in $it" }
            reach.place != null -> "Allow in ${reach.place.name}"
            reach.roots.isNotEmpty() -> "Allow in Chat"
            else -> null
        }
    }

    /** [approval]'s answer for this once. */
    fun allowOnce(approval: AssistantApproval): String = if (approval.reach == null) "Allow" else "Allow Once"

    /**
     * The chat's mode: the [setting], or Auto in workspace once the chat answered "Allow in" its
     * workspace. A chat in no workspace ([inWorkspace] false) has none to run in without asking: Auto
     * in workspace asks there as Ask first does, and only Full access asks nothing (docs/design-and-navigation.md).
     */
    fun effective(setting: AssistantPermission, allowedInWorkspace: Boolean, inWorkspace: Boolean = true): AssistantPermission = when {
        !inWorkspace -> if (setting == AssistantPermission.FullAccess) setting else AssistantPermission.AskFirst
        allowedInWorkspace && setting == AssistantPermission.AskFirst -> AssistantPermission.AutoInWorkspace
        else -> setting
    }

    /**
     * The folder "Allow in this chat" opens for [path], a runtime path outside the chat's workspace:
     * another workspace whole; else the Git repository holding it, its top the nearest
     * folder [isRepository] says so of; else its folder ([path] itself when [isFolder]). Never `/`:
     * null then.
     */
    fun rootOf(path: String, isFolder: (String) -> Boolean, isRepository: (String) -> Boolean): String? {
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.size >= 2 && segments[0] == WORKSPACES) return "/$WORKSPACES/${segments[1]}"
        val folder = if (isFolder(path)) segments else segments.dropLast(1)
        val top = (folder.size downTo 1).map { "/" + folder.take(it).joinToString("/") }.firstOrNull(isRepository)
        return top ?: folder.takeIf { it.isNotEmpty() }?.let { "/" + it.joinToString("/") }
    }

    /**
     * What "Allow in *app* for this chat" opens for an app on the phone (docs/voice-and-phone-control.md),
     * kept with the folders a chat opened: never a runtime path, which starts with `/`.
     */
    fun appRoot(packageName: String): String = "app:$packageName"

    /** What "Allow in *server*" opens for an MCP server (docs/assistant-and-mcp.md): never a runtime path either. */
    fun serverRoot(id: String): String = "mcp:$id"

    /** What "Allow in *tab*" opens for a terminal tab of the user's (docs/assistant-and-mcp.md), while it runs. */
    fun terminalRoot(sessionId: String): String = "terminal:$sessionId"

    /** [path] is [root] or under it, both runtime paths. */
    fun within(path: String, root: String): Boolean = path == root || path.startsWith("${root.trimEnd('/')}/")

    /** The folder every workspace is in, in the runtime ([WorkspaceRuntimePath.ROOT]). */
    private val WORKSPACES = WorkspaceRuntimePath.ROOT.trim('/')
}
