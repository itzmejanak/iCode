package dev.icode.mobile.domain

/** A call waiting for the user: a change, a command, or a reach outside the workspace. */
data class AssistantApproval(
    val call: AssistantCall,
    /** What it will do, in full enough to judge: the command, or the file it changes. */
    val detail: String,
    /** The text a change writes, coloured on its page as its file is (docs/editor-and-language-services.md). */ val code: List<AssistantCode> = emptyList(),
    /** What it reaches outside the chat's workspace; null when it asks as a change or a command only. */
    val reach: AssistantReach? = null,
    /** Asked each time, in every mode (docs/assistant-and-mcp.md): no "Allow in". */
    val always: Boolean = false,
    /** An agent's own answers, in its words, in place of iCode's (docs/assistant-and-mcp.md). */
    val options: List<AssistantOption> = emptyList(),
) {
    /**
     * The agent's own option a plain [answer] stands for, where only Allow and Deny are shown (the
     * bubble): this time only, rather than from now on; null when the agent offers none, which cancels.
     */
    fun optionFor(answer: AssistantAnswer): String? {
        val allows = answer != AssistantAnswer.Deny
        val matching = options.filter { it.allows == allows }
        return (matching.firstOrNull { !it.always } ?: matching.firstOrNull())?.id
    }
}

/** One of an agent's own answers to its permission request: [allows] or refuses, this time or [always]. */
data class AssistantOption(val id: String, val label: String, val allows: Boolean, val always: Boolean = false)

/**
 * A step only the user can take, on the phone (docs/voice-and-phone-control.md) — a password, a
 * payment: the turn waits between its steps until the user says [PROCEED]; Stop ends it.
 */
data class AssistantPause(/** What the user is to do, under the card's [QUESTION]. */ val detail: String) {
    companion object {
        const val QUESTION = "Your turn"
        const val PROCEED = "Done"
    }
}

/**
 * The user's answer to an approval. [AllowInChat] opens, for this chat until iCode closes, the
 * [AssistantReach.roots] of an outside call, or the chat's workspace (Auto in workspace) for a
 * change or a command (docs/assistant-and-mcp.md).
 */
enum class AssistantAnswer { Allow, AllowInChat, Deny }
