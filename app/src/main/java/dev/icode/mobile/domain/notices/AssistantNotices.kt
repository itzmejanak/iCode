package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.countOf

/** What the assistant tells (docs/notices-redesign-plan.md D8, D9). */
object AssistantNotices {
    fun noApp() = Notice(NoticeTone.HeadsUp, NoticeSource.Assistant, "Install an app that can do that", detail = "No app on this phone opens it.")

    /** [left] files over the five a message takes. */
    fun tooManyFiles(left: Int, most: Int) = Notice(
        NoticeTone.HeadsUp,
        NoticeSource.Assistant,
        "Attach $most files at most",
        detail = "${countOf(left, "file")} not attached. Send them with the next message.",
    )
}
