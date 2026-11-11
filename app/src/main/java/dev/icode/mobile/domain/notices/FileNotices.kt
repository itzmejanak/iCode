package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.paths

/** What the explorers and Files tell (docs/notices-redesign-plan.md D8, D9). */
object FileNotices {
    /** Where a change was made: the folder of what its first step touched, where it was or what was made. */
    fun placeOf(snapshot: ICodeSnapshot, steps: List<FileStep>): NoticePlace? =
        steps.firstOrNull()?.paths?.firstOrNull()?.let { NoticePlaces.of(snapshot, it) }

    fun cannotShow(name: String) =
        Notice(NoticeTone.HeadsUp, NoticeSource.Files, "Couldn't show $name", detail = "iCode has no viewer for it, and it cannot be handed to another app.")

    fun cannotHandOver(name: String) =
        Notice(NoticeTone.HeadsUp, NoticeSource.Files, "Couldn't hand $name to another app", detail = "Android's file sharing does not reach where it is.")
}
