package dev.icode.mobile.domain.notices

/**
 * What the coding agents tell (docs/engineering-rules.md): by the agent's name, in the agent's
 * words where it gave them. They are the assistant's: an agent answers its chats.
 */
object AgentNotices {
    /** [agent] is not installed: Runtime Tools installs it. */
    fun notInstalled(agent: String) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Assistant, "Install $agent first", NoticePlace(subject = agent),
        detail = "Install it from Settings › Assistant › Coding Agents, or in Runtime Tools.",
        actions = listOf(NoticeAction.Show(NoticeTarget.RuntimeTools)),
    )

    /** The agent's own CLI could not be opened in a terminal tab. */
    fun cliNotOpened(agent: String, words: String) =
        Notice(NoticeTone.Problem, NoticeSource.Assistant, "Couldn't open $agent in a terminal", NoticePlace(subject = agent), detail = words)

    /** A setting the agent refused, in its words. */
    fun notSet(agent: String, words: String) =
        Notice(NoticeTone.Problem, NoticeSource.Assistant, "$agent didn't change the setting", NoticePlace(subject = agent), detail = words)

    fun notSignedIn(agent: String, words: String) =
        Notice(NoticeTone.Problem, NoticeSource.Assistant, "Couldn't sign in to $agent", NoticePlace(subject = agent), detail = words)

    fun signedIn(agent: String) =
        Notice(NoticeTone.Done, NoticeSource.Assistant, "Signed in to $agent", NoticePlace(subject = agent), detail = "Send your message again.")

    fun keysSaved(agent: String) =
        Notice(NoticeTone.Done, NoticeSource.Assistant, "Saved $agent's keys", NoticePlace(subject = agent), detail = "They are kept encrypted and given only to $agent.")

    /** A chat answered by an agent cannot be compacted by iCode: the agent keeps its own context. */
    fun compactsItself(agent: String) = Notice(
        NoticeTone.Info, NoticeSource.Assistant, "$agent keeps its own context",
        NoticePlace(subject = agent), detail = "Use its own command for this, if it offers one: type / in the message box.",
    )

    /** A message of an agent's chat cannot be taken back: the agent keeps the conversation. */
    fun keepsItsHistory(agent: String) = Notice(
        NoticeTone.Info, NoticeSource.Assistant, "$agent keeps this chat's history",
        NoticePlace(subject = agent), detail = "Send a new message instead.",
    )
}
