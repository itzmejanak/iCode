package dev.icode.mobile.domain.notices

/** What settings, and iCode's own controls, tell (docs/engineering-rules.md). */
object SettingsNotices {
    /** A copy confirmed where Android does not confirm it itself ([what]: "Path copied"). */
    fun copied(what: String) = Notice(NoticeTone.Done, NoticeSource.ICode, what, keep = NoticeKeep.Moment)

    /** A floating pill's place that could not be kept. */
    fun pillNotKept(words: String) =
        Notice(NoticeTone.HeadsUp, NoticeSource.Settings, "Couldn't keep where you put the pill", detail = "It goes back where it was the next time iCode starts.", output = words)
}
