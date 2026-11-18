package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.CodingAgent

/** What Settings › Assistant › Coding agents tells (docs/engineering-rules.md), by the agent's name. */
object AgentNotices {
    /** Installed or updated: told in the shade too when it ends while iCode is out of view. */
    fun installed(name: String, version: String) =
        Notice(NoticeTone.Done, NoticeSource.Assistant, "$name $version installed", tellAway = true)

    /** Told with the failure's own words as its output. */
    fun notInstalled(name: String) = Notice(NoticeTone.Problem, NoticeSource.Assistant, "Couldn't install $name", tellAway = true)

    fun removed(name: String) = Notice(NoticeTone.Done, NoticeSource.Assistant, "$name removed", keep = NoticeKeep.Moment)

    fun notRemoved(name: String) = Notice(NoticeTone.Problem, NoticeSource.Assistant, "Couldn't remove $name")

    /** An agent that did not start, or did not answer iCode, with why. */
    fun notStarted(name: String, words: String) = Notice(NoticeTone.Problem, NoticeSource.Assistant, "$name didn't start", output = words)

    fun notSignedIn(name: String, words: String) = Notice(NoticeTone.Problem, NoticeSource.Assistant, "Couldn't sign in to $name", output = words)

    /** An agent that cannot stop to open in the terminal: it works in a chat. */
    fun working(name: String) = Notice(NoticeTone.HeadsUp, NoticeSource.Assistant, "$name is working in a chat", detail = "Open it in the terminal once it is done.")

    /** An agent's own program that did not open in the terminal, with why. */
    fun notOpened(name: String, words: String) = Notice(NoticeTone.Problem, NoticeSource.Assistant, "Couldn't open $name in the terminal", output = words)

    /** An agent that could not list its conversations, or take one up, with why. */
    fun notFound(name: String, words: String) = Notice(NoticeTone.Problem, NoticeSource.Assistant, "Couldn't read $name's chats", output = words)

    /** A setting the agent refused to change, with why. */
    fun notSet(name: String, words: String) = Notice(NoticeTone.Problem, NoticeSource.Assistant, "$name kept its setting", output = words)

    fun needsRuntime() = Notice(NoticeTone.HeadsUp, NoticeSource.Assistant, "Install the Linux runtime first", detail = "Coding agents run in it.")

    /**
     * Newer releases of installed agents, found by the daily check. Its ID names them, so the same
     * releases are told once.
     */
    fun updates(agents: List<CodingAgent>) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Assistant,
        agents.singleOrNull()?.let { "${it.name} ${it.version} is available" } ?: "Updates for ${agents.size} coding agents",
        detail = agents.takeIf { it.size > 1 }?.joinToString(", ") { "${it.name} ${it.version}" },
        actions = listOf(NoticeAction.Show(NoticeTarget.CodingAgents)),
        id = "agent-updates:" + agents.joinToString(",") { "${it.id}@${it.version}" },
    )
}
