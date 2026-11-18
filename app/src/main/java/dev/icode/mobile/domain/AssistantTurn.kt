package dev.icode.mobile.domain

/**
 * A chat's turn in hand (docs/assistant-and-mcp.md): one per chat, in the chat it started in, and as
 * many chats at work at once as the user starts. What it waits on — an approval, a pause, the
 * model's questions — is its own.
 */
/** Continue, on a chat iCode was closed while it worked: the work is taken up again (docs/assistant-and-mcp.md). */
data object ResumeAssistantChat : ICodeIntent.AssistantIntent

data class AssistantTurn(
    /** Its workspace's location; null for a chat in none, which works in `$HOME` (docs/design-and-navigation.md). */
    val workspace: String?,
    val workspaceName: String,
    val session: String,
    /** "Thinking…", "Read src/App.kt", "Waiting for your answer", "Compacting the chat…". */
    val step: String,
    /** The reply being written, as far as it has come; null between replies, and where replies come whole. */
    val live: AssistantLive? = null,
    /** It has acted on the phone: the screen's edges glow while it works (docs/voice-and-phone-control.md). */
    val onPhone: Boolean = false,
    /** Read aloud, as Spoken replies said when it was sent: its instructions ask for a short spoken answer. */
    val aloud: Boolean = false,
    /** The terminal tab the running call's command runs in: its step shows it live while it runs (docs/assistant-and-mcp.md). */
    val terminal: AssistantTerminal? = null,
    /** One of its tools has changed a file: said when it ends ([AssistantTurnEnd]). */
    val changedFiles: Boolean = false,
    /** Who answers it: named above its reply while that is written. */
    val by: AssistantAnswerer? = null,
    /** A call waiting for the user's answer (docs/assistant-and-mcp.md). */
    val approval: AssistantApproval? = null,
    /** It waits for a step the user takes on the phone (docs/voice-and-phone-control.md). */
    val pause: AssistantPause? = null,
    /** The model's questions, waiting for the user's reply (docs/assistant-and-mcp.md). */
    val clarification: AssistantClarification? = null,
    /** An agent's to-do list as it last said it: its progress shows on the jump-to-latest button. */
    val plan: List<AgentPlanStep> = emptyList(),
) {
    /** It waits for the user: an approval, a pause, or the model's questions. */
    val asking: Boolean get() = approval != null || pause != null || clarification != null

    /** It is the turn of [session] in [workspace]. */
    fun of(workspace: String?, session: String?): Boolean = this.workspace == workspace && this.session == session
}

/** A streamed reply as far as it has come: what the model thought, and its answer so far. */
data class AssistantLive(val reasoning: String, val text: String)

/** A turn that ended (docs/design-and-navigation.md): its workspace, how many have ended so far, whether its tools changed a file. */
data class AssistantTurnEnd(val workspace: String?, val number: Int, val changedFiles: Boolean)
