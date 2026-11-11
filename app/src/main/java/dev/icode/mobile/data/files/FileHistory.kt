package dev.icode.mobile.data.files

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.files.FileChange
import dev.icode.mobile.domain.files.FileHistoryState
import dev.icode.mobile.domain.files.FileNews
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.notices.FileNotices

/**
 * Undo and redo (docs/explorer-gaps-plan.md §3.8): the last [FileChange.DEPTH] changes made in the
 * explorers, in memory while iCode runs. A change made clears what could be redone. An undo or redo
 * stopped short keeps each side exact: what it reversed moves across, what it did not stays. Read
 * and changed under the mutation lock; the snapshot says what Undo and Redo would do, and the news
 * the undo pill tells.
 */
internal class FileHistory(
    private val stateHolder: ICodeStateHolder,
    /** Every change's steps as they are kept — done, undone or redone: what follows them, Manage storage's scan. */
    private val changed: (List<FileStep>) -> Unit = {},
) {
    private val undo = ArrayDeque<FileChange>()
    private val redo = ArrayDeque<FileChange>()
    private var newsId = 0L

    /** What Undo would reverse now, or null. */
    val nextUndo: FileChange? get() = undo.lastOrNull()

    /** What Redo would reverse now, or null. */
    val nextRedo: FileChange? get() = redo.lastOrNull()

    /** [change] made: kept to undo, unless it did nothing. */
    fun record(change: FileChange) {
        if (change.steps.isEmpty()) return
        changed(change.steps)
        push(undo, change)
        redo.clear()
        publish(change.said, change.steps, undone = false)
    }

    /** [change], taken from Undo, reversed as far as [steps] go. */
    fun undone(change: FileChange, steps: List<FileStep>) = cross(change, steps, from = undo, to = redo, undone = true)

    /** [change], taken from Redo, reversed as far as [steps] go. */
    fun redone(change: FileChange, steps: List<FileStep>) = cross(change, steps, from = redo, to = undo, undone = false)

    /** What [steps] reversed of [change] goes to the other side; what they did not reach stays where it was. */
    private fun cross(change: FileChange, steps: List<FileStep>, from: ArrayDeque<FileChange>, to: ArrayDeque<FileChange>, undone: Boolean) {
        val at = from.indexOfLast { it === change }
        if (at < 0) return
        from.removeAt(at)
        val left = change.steps.dropLast(steps.size)
        if (left.isNotEmpty()) from.add(at, change.copy(steps = left))
        if (steps.isEmpty()) return publish(null, steps, undone)
        changed(steps)
        val reversed = change.undoneBy(steps)
        push(to, reversed)
        publish(reversed.said, steps, undone)
    }

    private fun push(side: ArrayDeque<FileChange>, change: FileChange) {
        side.addLast(change)
        if (side.size > FileChange.DEPTH) side.removeFirst()
    }

    /** What Undo and Redo would do now, and — when there is one — the news of [line], where [steps] were taken. */
    private fun publish(line: String?, steps: List<FileStep>, undone: Boolean) {
        val news = line?.let { FileNews(++newsId, it, undone, FileNotices.placeOf(stateHolder.current, steps)) }
        stateHolder.update { state ->
            val history = FileHistoryState(undo.lastOrNull()?.said, redo.lastOrNull()?.undoneSaid, news ?: state.explorer.history.news)
            state.copy(explorer = state.explorer.copy(history = history))
        }
    }
}
