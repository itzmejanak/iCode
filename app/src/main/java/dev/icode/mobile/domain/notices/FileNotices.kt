package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.FileTask
import dev.icode.mobile.domain.files.FileWords
import dev.icode.mobile.domain.files.paths

/**
 * What the explorers and Files tell (docs/notices-redesign-plan.md D8, D9): each change made, undone
 * or redone, in the undo pill's words, and what stopped short or was refused, where it happened.
 */
object FileNotices {
    /** Where a change was made: the folder of what its first step touched, where it was or what was made. */
    fun placeOf(snapshot: ICodeSnapshot, steps: List<FileStep>): NoticePlace? =
        steps.firstOrNull()?.paths?.firstOrNull()?.let { NoticePlaces.of(snapshot, it) }

    /** A change, as its [line] says it — "Moved notes.md to Trash" — with Undo while it is still the one Undo reverses. */
    fun changed(line: String, place: NoticePlace?) =
        Notice(NoticeTone.Done, NoticeSource.Files, line, place, actions = listOf(NoticeAction.Undo(line)))

    /** Out of Trash for good: not kept to undo. */
    fun deletedForGood(names: List<String>, place: NoticePlace?) =
        Notice(NoticeTone.Done, NoticeSource.Files, "Deleted ${FileWords.subject(names)} for good", place)

    /**
     * A [task] over [subject] — into the folder [into], for a move or a copy — that stopped short:
     * the error's words are its output, and its sheet stays until dismissed.
     */
    fun stopped(task: FileTask, subject: String, into: String?, place: NoticePlace?): Notice {
        val to = into?.let { " to $it" }.orEmpty()
        val (what, detail) = when (task) {
            FileTask.Move -> "move $subject$to" to null
            FileTask.Copy -> "copy $subject$to" to null
            FileTask.Duplicate -> "duplicate $subject" to null
            FileTask.Rename -> "rename $subject" to null
            FileTask.Trash -> "move $subject to Trash" to null
            FileTask.Restore -> "restore $subject" to null
            // What is undone or redone is a change, said as one: "Moved notes.md to Trash".
            FileTask.Undo -> "undo the last change" to "$subject. What was undone before it stopped can be redone."
            FileTask.Redo -> "redo the last change" to "$subject. What was redone before it stopped can be undone."
            FileTask.Compress -> "compress $subject" to null
            FileTask.Extract -> "extract $subject" to null
            FileTask.DeleteForGood -> "delete $subject for good" to null
        }
        return Notice(NoticeTone.Problem, NoticeSource.Files, "Couldn't $what", place, detail)
    }

    /** What to do first, or what was not done, in the explorer's own words ([FileWords], the refusals). */
    fun headsUp(words: String, place: NoticePlace?) = Notice(NoticeTone.HeadsUp, NoticeSource.Files, words, place)

    /** A new file or folder [name] that could not be made. */
    fun notMade(name: String, place: NoticePlace?) = Notice(NoticeTone.Problem, NoticeSource.Files, "Couldn't create $name", place)

    fun notRenamed(name: String, place: NoticePlace?) = Notice(NoticeTone.Problem, NoticeSource.Files, "Couldn't rename $name", place)

    fun recentNotKept() = Notice(NoticeTone.Problem, NoticeSource.Files, "Couldn't save Recent files")

    fun favoritesNotKept() = Notice(NoticeTone.Problem, NoticeSource.Files, "Couldn't save Favorites")

    fun cannotShow(name: String) =
        Notice(NoticeTone.HeadsUp, NoticeSource.Files, "Couldn't show $name", detail = "iCode has no viewer for it, and it cannot be handed to another app.")

    fun cannotHandOver(name: String) =
        Notice(NoticeTone.HeadsUp, NoticeSource.Files, "Couldn't hand $name to another app", detail = "Android's file sharing does not reach where it is.")
}
