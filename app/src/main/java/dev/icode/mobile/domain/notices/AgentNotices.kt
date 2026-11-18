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
