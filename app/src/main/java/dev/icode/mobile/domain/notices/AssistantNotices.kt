package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.countOf

/** What the assistant tells (docs/engineering-rules.md): of a chat, where it is — its workspace and title. */
object AssistantNotices {
    fun noApp() = Notice(NoticeTone.HeadsUp, NoticeSource.Assistant, "Install an app that can do that", detail = "No app on this phone opens it.")

    /** [left] files over the five a message takes. */
    fun tooManyFiles(left: Int, most: Int) = Notice(
        NoticeTone.HeadsUp,
        NoticeSource.Assistant,
        "Attach $most files at most",
        detail = "${countOf(left, "file")} not attached. Send them with the next message.",
    )

    fun openWorkspaceFirst() = Notice(NoticeTone.HeadsUp, NoticeSource.Assistant, "Open a workspace first", detail = "The assistant works in the open workspace.")

    // What a tap asked for, with nothing to do it on: the tap's answer, not kept.

    fun nothingToSendAgain() = Notice(NoticeTone.HeadsUp, NoticeSource.Assistant, "Nothing to send again", keep = NoticeKeep.Moment)

    fun nothingToCompact() = Notice(NoticeTone.HeadsUp, NoticeSource.Assistant, "Nothing to compact yet", keep = NoticeKeep.Moment)

    fun nothingToShare() = Notice(NoticeTone.HeadsUp, NoticeSource.Assistant, "Nothing to share yet", keep = NoticeKeep.Moment)

    fun stopFirst(chat: NoticePlace?) = Notice(NoticeTone.HeadsUp, NoticeSource.Assistant, "Stop the assistant first", chat, detail = "It is working in this chat. Then delete it.")

    fun busy(workspace: String) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Assistant, "Wait for the assistant to finish", NoticePlace(workspace),
        detail = "It is working in $workspace. Or stop it there.",
    )

    fun terminalNotOpened(words: String) = Notice(NoticeTone.Problem, NoticeSource.Assistant, "Couldn't open the chat's terminal", output = words)

    fun noPictures(model: String) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Assistant, "$model doesn't see pictures",
        detail = "It goes on with the screen's text, and is set to read no images from now on.",
    )

    /** A turn that failed in a chat not shown: why, in its words, and the chat to open. */
    fun stopped(failure: String, chat: NoticePlace) = Notice(
        NoticeTone.Problem, NoticeSource.Assistant, "The assistant stopped", chat,
        detail = failure, actions = listOf(NoticeAction.Show(assistantOf(chat))),
    )

    /** A turn that ran long and ended: kept, and posted should iCode be out of view by then. */
    fun finished(chat: NoticePlace) = Notice(
        NoticeTone.Done, NoticeSource.Assistant, "The assistant finished", chat,
        actions = listOf(NoticeAction.Show(assistantOf(chat))), tellAway = true,
    )

    /** Where [chat]'s page is: inside its workspace, or outside it for a chat in none. */
    private fun assistantOf(chat: NoticePlace) = if (chat.workspaceLocation == null) NoticeTarget.AssistantOutside else NoticeTarget.Assistant

    // Providers and settings.

    /** What the provider's form refuses, in the rules' words. */
    fun providerRefused(words: String) = Notice(NoticeTone.HeadsUp, NoticeSource.Assistant, words)

    fun providerSaved(name: String) = Notice(NoticeTone.Done, NoticeSource.Assistant, "Saved $name")

    fun providerNotSaved(name: String, words: String?) = Notice(NoticeTone.Problem, NoticeSource.Assistant, "Couldn't save $name", output = words)

    fun settingsNotSaved(words: String?) = Notice(NoticeTone.Problem, NoticeSource.Assistant, "Couldn't save the assistant's settings", output = words)
}
