package dev.icode.mobile.domain

import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticeFilter

/** The Notifications page, the bell and Android's shade (docs/notices-redesign-plan.md). */
sealed interface NoticeIntent : ICodeIntent {
    /** The Notifications page was opened: everything on it has been seen. */
    data object MarkAllRead : NoticeIntent

    /** A notice's sheet was opened: that one has been seen. */
    data class Read(val id: String) : NoticeIntent

    data class Delete(val id: String) : NoticeIntent

    /** Clear all: what [filter] shows. */
    data class Clear(val filter: NoticeFilter = NoticeFilter.All) : NoticeIntent

    /** What a screen has to say, told as the backend's notices are. */
    data class Tell(val notice: Notice) : NoticeIntent
}
