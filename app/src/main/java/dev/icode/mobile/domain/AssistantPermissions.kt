package dev.icode.mobile.domain

/**
 * How much the assistant does without asking (docs/assistant-permissions-plan.md §4.1). The chat's
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
 * What a call reaches outside its chat's workspace (§4.3): [paths] as they resolve in the runtime,
 * and the folders "Allow in this chat" opens ([AssistantPermissions.rootOf]); none where nothing
 * smaller than `/` would do.
 */
data class AssistantReach(val paths: List<String>, val roots: List<String>)

object AssistantPermissions {
    /**
     * What a call of [kind] asks under [permission] (§4.2), [outside] when it reaches beyond the
     * workspace and this chat has not opened where: null when it runs.
     */
    fun ask(permission: AssistantPermission, kind: AssistantToolKind, outside: Boolean): AssistantAsk? = when {
        permission == AssistantPermission.FullAccess -> null
        outside -> AssistantAsk.Outside
        permission == AssistantPermission.AutoInWorkspace || kind == AssistantToolKind.Read -> null
        kind == AssistantToolKind.Command -> AssistantAsk.Command
        else -> AssistantAsk.Change
    }

    /** What the model is told of a call the user denied, as it [asked], [outside] what it reached there. */
    fun denied(asked: AssistantAsk, outside: List<String>): String = when (asked) {
        AssistantAsk.Outside -> "The user did not allow reaching ${outside.joinToString()}"
        AssistantAsk.Command -> "The user did not allow this command"
        AssistantAsk.Change -> "The user did not allow this change"
    }

    /** The chat's mode: the [setting], or Auto in workspace once the chat answered "Allow in" its workspace. */
    fun effective(setting: AssistantPermission, allowedInWorkspace: Boolean): AssistantPermission =
        if (allowedInWorkspace && setting == AssistantPermission.AskFirst) AssistantPermission.AutoInWorkspace else setting

    /**
     * The folder "Allow in this chat" opens for [path], a runtime path outside the chat's workspace
     * (§4.4): another workspace whole; else the Git repository holding it, its top the nearest
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

    /** [path] is [root] or under it, both runtime paths. */
    fun within(path: String, root: String): Boolean = path == root || path.startsWith("${root.trimEnd('/')}/")

    /** The folder every workspace is in, in the runtime ([WorkspaceRuntimePath.ROOT]). */
    private val WORKSPACES = WorkspaceRuntimePath.ROOT.trim('/')
}
