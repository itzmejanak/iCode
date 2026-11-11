package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.GitTransferLabel

/** What Git tells (docs/notices-redesign-plan.md D8, D9). */
object GitNotices {
    /** A transfer still running when iCode's process ended, at [target]: the workspace, or the folder a clone was writing. */
    fun cutShort(label: String, target: String): Notice {
        val clone = label == GitTransferLabel.CLONING
        return Notice(
            NoticeTone.HeadsUp,
            NoticeSource.Git,
            "The ${nounOf(label)} didn't finish",
            NoticePlaces.named(target),
            detail = if (clone) {
                "iCode closed while Git was cloning, and nothing of it was kept. Clone it again."
            } else {
                "iCode closed while Git was working. Run it again: Git goes on from what it has."
            },
            actions = listOfNotNull(commandOf(label)?.let { NoticeAction.Run(it, "${verbOf(label)} Again") }),
        )
    }

    /** "pull", "push", "fetch", "clone": a transfer as a thing. */
    fun nounOf(label: String): String = when (label) {
        GitTransferLabel.CLONING -> "clone"
        GitTransferLabel.FETCHING -> "fetch"
        GitTransferLabel.PULLING -> "pull"
        GitTransferLabel.PUSHING -> "push"
        GitTransferLabel.COMMITTING -> "commit"
        else -> label.lowercase()
    }

    /** "Pull", "Push": what a button runs again. */
    private fun verbOf(label: String): String = nounOf(label).replaceFirstChar(Char::uppercaseChar)

    private fun commandOf(label: String): NoticeCommand? = when (label) {
        GitTransferLabel.FETCHING -> NoticeCommand.Fetch
        GitTransferLabel.PULLING -> NoticeCommand.Pull
        GitTransferLabel.PUSHING -> NoticeCommand.Push
        else -> null
    }
}
