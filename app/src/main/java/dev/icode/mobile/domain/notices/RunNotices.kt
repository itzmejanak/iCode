package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.ICodeSnapshot

/** What Run's saved commands tell (docs/notices-redesign-plan.md D8). */
object RunNotices {
    fun commandNotSaved(at: ICodeSnapshot, name: String) =
        Notice(NoticeTone.Problem, NoticeSource.Run, "Couldn't save the command ${name.trim()}", NoticePlaces.workspace(at))

    fun commandNotDeleted(at: ICodeSnapshot) = Notice(NoticeTone.Problem, NoticeSource.Run, "Couldn't delete the command", NoticePlaces.workspace(at))
}
