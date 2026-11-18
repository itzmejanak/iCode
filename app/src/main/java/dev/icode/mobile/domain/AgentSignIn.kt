package dev.icode.mobile.domain

/** One way an agent offers to sign in (docs/assistant-and-mcp.md), as it describes it. */
data class AgentSignIn(val id: String, val name: String, val description: String?, val way: AgentSignInWay)

sealed interface AgentSignInWay {
    /** The agent signs in by itself when asked: a browser it opens, a code it shows. */
    data object ByAgent : AgentSignInWay

    /** Its own sign-in, run in a terminal tab: [args] after its command, [env] over the login's variables. */
    data class InTerminal(val args: List<String>, val env: Map<String, String>) : AgentSignInWay

    /** Keys entered in iCode, kept encrypted and given only to its process, as these variables; [link] is where to get them. */
    data class Keys(val keys: List<AgentKey>, val link: String?) : AgentSignInWay
}

/** A variable an agent reads its key from: [label] as it names it; a [secret] one is masked as it is typed. */
data class AgentKey(val name: String, val label: String?, val secret: Boolean, val optional: Boolean)

/** An agent in the chat shown (docs/assistant-and-mcp.md), in the assistant's order. */
sealed interface AgentChatIntent : ICodeIntent.AssistantIntent {
    /** The chat shown answered by the agent [id] from now on. */
    data class Choose(val id: String) : AgentChatIntent

    /** [agent] asked to sign in the [method] way: by itself, or in a terminal tab. */
    data class SignIn(val agent: String, val method: String) : AgentChatIntent

    /** [agent]'s [values], by variable, kept encrypted for its process; it starts again with them. */
    data class SaveKeys(val agent: String, val values: Map<String, String>) : AgentChatIntent {
        /** Keys are never written out, should an intent ever be. */
        override fun toString(): String = "SaveKeys($agent, ${values.keys})"
    }

    /** Signed in outside iCode's asking — in a terminal tab: [agent] is asked again. */
    data class SignedIn(val agent: String) : AgentChatIntent

    /** The chat shown's [setting] of [agent] set to [value]: one of its values, or "true" or "false". */
    data class Set(val agent: String, val setting: String, val value: String) : AgentChatIntent
}
