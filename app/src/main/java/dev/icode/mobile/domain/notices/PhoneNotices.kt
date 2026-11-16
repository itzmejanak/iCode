package dev.icode.mobile.domain.notices

/** What phone control tells (docs/engineering-rules.md). */
object PhoneNotices {
    fun shizukuRefused() = Notice(
        NoticeTone.Problem, NoticeSource.Phone, "Couldn't turn phone control on with Shizuku",
        detail = "Turn it on in Android's Accessibility settings instead.",
    )
}
