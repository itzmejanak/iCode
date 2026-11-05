package dev.icode.mobile.domain

/** MCP servers' settings and their questions (docs/mcp-plan.md §5.2), in the assistant's order. */
sealed interface McpIntent : ICodeIntent.AssistantIntent {
    /** The MCP pages or the Assistant page opened: the servers that are on connect, if they are not. */
    data object Connect : McpIntent

    /** Adds a server ([McpServer.id] "") or replaces one; a secret value null keeps the kept one. */
    data class SaveServer(val server: McpServer) : McpIntent

    /** The servers a pasted configuration names, each added; [single] names one that has no name. */
    data class ImportServers(val json: String, val single: String = "Server") : McpIntent {
        /** A paste may hold keys: never written out. */
        override fun toString(): String = "ImportServers(${json.length} characters)"
    }

    /** Forgets a server, its secrets and its sign-in. */
    data class RemoveServer(val id: String) : McpIntent
    data class SetServerEnabled(val id: String, val enabled: Boolean) : McpIntent
    data class SetToolChoice(val id: String, val tool: String, val choice: McpToolChoice) : McpIntent

    /** Stops a server and starts it again, its tools read anew. */
    data class RestartServer(val id: String) : McpIntent

    /** Reads a server's log for its page; null when the page closes. */
    data class ReadLog(val id: String?) : McpIntent

    /** A remote server's sign-in (M5): the browser opens on its authorization page. */
    data class SignIn(val id: String) : McpIntent
    data class SignOut(val id: String) : McpIntent

    /** The browser came back to iCode with a sign-in's answer. */
    data class SignedIn(val uri: String) : McpIntent {
        /** It holds a one-time code: never written out. */
        override fun toString(): String = "SignedIn(…)"
    }

    /** A server's question answered (M4). */
    data class Answer(val answer: McpAnswer) : McpIntent

    /** A workspace's server trusted, so it may start, or not (M6). */
    data class Trust(val id: String, val trusted: Boolean) : McpIntent

    /** The MCP Registry searched (M7); null closes the search. A server found is added with [SaveServer]. */
    data class SearchRegistry(val query: String?) : McpIntent

    /** A server's prompt, filled with [arguments], into the composer's draft (M8). */
    data class UsePrompt(val id: String, val name: String, val arguments: Map<String, String>) : McpIntent

    /** A server's resource read into the composer's draft (M8). */
    data class UseResource(val id: String, val uri: String) : McpIntent

    /** The composer took the draft. */
    data object DraftTaken : McpIntent
}
