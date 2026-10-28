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

    /** An empty folder taken away: one a merge moved everything out of, or one made and undone. */
    data class Removed(val location: String) : FileStep
}

/** What undoing a step does; doing it gives the step that undoes the undo. */
sealed interface Reversal {
    /** [from] goes back to [to]. */
    data class MoveBack(val from: String, val to: String) : Reversal

    /**
     * What was made goes to Trash, so undoing a copy can be undone too and nothing is lost — or, an
     * empty folder, which holds nothing to lose, is removed.
     */
    data class TrashMade(val location: String) : Reversal

    /** Trash's [id] goes back to [to]. */
    data class Restore(val id: String, val to: String) : Reversal

    /** The empty folder [location] made again. */
    data class MakeFolder(val location: String) : Reversal
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

    /**
     * Why its [reversals] cannot be done now, or null (§3.8, "checked first"): each is checked
     * against what is there — [exists] answers for a path, [inTrash] for Trash's items — as the ones
     * before it would leave things, so nothing is half undone because something changed since.
     */
    fun refusal(exists: (String) -> Boolean, inTrash: (String) -> Boolean): String? {
        val gone = mutableSetOf<String>()
        val made = mutableSetOf<String>()
        fun present(path: String) = path in made || (gone.none { path == it || path.startsWith("$it/") } && exists(path))
        fun went(path: String) = made.remove(path).also { gone += path }
        fun came(path: String) = gone.remove(path).also { made += path }
        for (reversal in reversals) {
            when (reversal) {
                is Reversal.MoveBack -> {
                    if (!present(reversal.from)) return changed(reversal.from)
                    if (present(reversal.to)) return taken(reversal.to)
                    went(reversal.from)
                    came(reversal.to)
                }
                is Reversal.TrashMade -> if (present(reversal.location)) went(reversal.location) else return changed(reversal.location)
                is Reversal.Restore -> {
                    if (!inTrash(reversal.id)) return "${nameOf(reversal.to)} is no longer in Trash"
                    if (present(reversal.to)) return taken(reversal.to)
                    came(reversal.to)
                }
                is Reversal.MakeFolder -> if (present(reversal.location)) return taken(reversal.location) else came(reversal.location)
            }
        }
        return null
    }

    companion object {
        fun reversal(step: FileStep): Reversal = when (step) {
            is FileStep.Moved -> Reversal.MoveBack(step.to, step.from)
            is FileStep.Made -> Reversal.TrashMade(step.location)
            is FileStep.Trashed -> Reversal.Restore(step.id, step.from)
            // Restored, it is made again where it was: undone, it goes back to Trash.
            is FileStep.Restored -> Reversal.TrashMade(step.to)
            is FileStep.Removed -> Reversal.MakeFolder(step.location)
        }

        /** The last steps kept: older ones are let go, Trash still holding what they deleted. */
        const val DEPTH = 50

        private fun nameOf(path: String) = path.substringAfterLast('/')

        private fun changed(path: String) = "${nameOf(path)} was moved or deleted since"

        private fun taken(path: String) = "“${nameOf(path)}” is already in ${nameOf(path.substringBeforeLast('/'))}"
    }
}

/** The paths this step touched. */
val FileStep.paths: List<String>
    get() = when (this) {
        is FileStep.Moved -> listOf(from, to)
        is FileStep.Made -> listOf(location)
        is FileStep.Trashed -> listOf(from)
        is FileStep.Restored -> listOf(to)
        is FileStep.Removed -> listOf(location)
    }

/** The paths this reversal would touch. */
val Reversal.paths: List<String>
    get() = when (this) {
        is Reversal.MoveBack -> listOf(from, to)
        is Reversal.TrashMade -> listOf(location)
        is Reversal.Restore -> listOf(to)
        is Reversal.MakeFolder -> listOf(location)
    }

/**
 * What the explorer shows of the history: what Undo and Redo would do, and the [news] the undo pill
 * tells — each new one by its own [FileNews.id].
 */
data class FileHistoryState(val undo: String? = null, val redo: String? = null, val news: FileNews? = null)

/** A change just made ([undone] false) or undone, for the undo pill (§3.8). */
data class FileNews(val id: Long, val line: String, val undone: Boolean) {
    companion object {
        /** The pill's line shows this long, as a bell's does; then the pill stays [HOLD_MILLIS], and goes. */
        const val LINE_MILLIS = 6_000L
        const val HOLD_MILLIS = 10_000L
    }
}
