package dev.icode.mobile.domain.notices

/** What Search tells (docs/engineering-rules.md). */
object SearchNotices {
    fun openWorkspaceFirst() = Notice(NoticeTone.HeadsUp, NoticeSource.Search, "Open a workspace first", detail = "Search looks in the open workspace.")
}
