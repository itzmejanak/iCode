package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.isAtOrUnder
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.OpenDocument
import java.io.File

/**
 * One answer to "may this change now?" (docs/workspaces-page-plan.md §2.8), asked before anything
 * renames, moves or deletes a workspace or an item in one — the Workspaces page, the Explorer and
 * the clipboard alike. What holds it: a task (whose sheet then shows), a file at or under it with
 * unsaved edits or an unanswered recovery question, work waiting in Document Recovery under it,
 * and — for a whole workspace — a terminal session, Git or the assistant working in it.
 */
internal class WorkspaceUsage(
    private val stateHolder: ICodeStateHolder,
    private val tasks: TaskRunner,
    private val terminalRunsIn: suspend (location: String) -> Boolean,
    private val processesRunIn: (location: String) -> Boolean,
    private val blockingEdits: (location: String) -> OpenDocument?,
    private val activeRoot: () -> String?,
) {
    /** Why [location] cannot change now, or null; [whole] when it is a workspace itself. */
    suspend fun holding(location: String, whole: Boolean): String? {
        val name = File(location).name
        if (whole && terminalRunsIn(location)) return "A terminal session is running in $name. End it first"
        if (whole && processesRunIn(location)) return "A process is running in $name. Stop it first"
        tasks.waitReason(location)?.let { return it }
        val state = stateHolder.current
        if (whole && state.gitOperation is GitOperationState.Running && activeRoot() == location) {
            return "Git is working in $name — wait for it to finish"
        }
        if (whole && state.assistant.turn?.workspace == location) return "The assistant is working in $name. Stop it first"
        return edited(location)
    }

    /**
     * Why what is at or under [location] cannot be replaced or moved now — unsaved edits, or work
     * waiting in Document Recovery — or null. An operation already running asks it of what it meets.
     */
    fun edited(location: String): String? {
        blockingEdits(location)?.let { return "${it.name} has unsaved edits. Save or close it first" }
        if (stateHolder.current.recoverableDrafts.any { it.location.isAtOrUnder(location) }) {
            return "${File(location).name} has unsaved work waiting in Document Recovery. Restore or discard it first"
        }
        return null
    }
}
