package dev.icode.mobile.domain.files

/**
 * What one operation did, step by step, so it can be undone (docs/explorer-gaps-plan.md §3.8). Each
 * step names what is where now.
 */
sealed interface FileStep {
    /** Renamed or moved, in one place or between the two. */
    data class Moved(val from: String, val to: String) : FileStep

    /** Made: a new folder or file, a copy, an archive, what was extracted. */
    data class Made(val location: String) : FileStep

    /** Moved to Trash, as [id]: by Delete, or by Replace. */
    data class Trashed(val id: String, val from: String) : FileStep

    /** Back from Trash, where it was. */
    data class Restored(val id: String, val to: String) : FileStep
}

/** What undoing a step does; doing it gives the step that undoes the undo. */
sealed interface Reversal {
    /** [from] goes back to [to]. */
    data class MoveBack(val from: String, val to: String) : Reversal

    /** What was made goes to Trash, so undoing a copy can be undone too and nothing is lost. */
    data class TrashMade(val location: String) : Reversal

    /** Trash's [id] goes back to [to]. */
    data class Restore(val id: String, val to: String) : Reversal
}

/**
 * One operation as the history keeps it: what it [said] ("Moved 3 items to Trash"), what its undoing
 * says ([undoneSaid], "Restored 3 items"), and its [steps], in the order they were done.
 */
data class FileChange(val said: String, val undoneSaid: String, val steps: List<FileStep>) {
    /** Its steps undone, the last first, as the steps were done. */
    val reversals: List<Reversal> get() = steps.reversed().map(::reversal)

    /** What the undoing did, kept to be redone: its own words swapped with these. */
    fun undoneBy(steps: List<FileStep>): FileChange = FileChange(undoneSaid, said, steps)

    companion object {
        fun reversal(step: FileStep): Reversal = when (step) {
            is FileStep.Moved -> Reversal.MoveBack(step.to, step.from)
            is FileStep.Made -> Reversal.TrashMade(step.location)
            is FileStep.Trashed -> Reversal.Restore(step.id, step.from)
            // Restored, it is made again where it was: undone, it goes back to Trash.
            is FileStep.Restored -> Reversal.TrashMade(step.to)
        }

        /** The last steps kept: older ones are let go, Trash still holding what they deleted. */
        const val DEPTH = 50
    }
}

/**
 * What the explorer shows of the history: what Undo and Redo would do, and the [news] the undo pill
 * tells — each new one by its own [FileNews.id].
 */
data class FileHistoryState(val undo: String? = null, val redo: String? = null, val news: FileNews? = null)

/** A change just made ([undone] false) or undone, for the undo pill (§3.8). */
data class FileNews(val id: Long, val line: String, val undone: Boolean)
