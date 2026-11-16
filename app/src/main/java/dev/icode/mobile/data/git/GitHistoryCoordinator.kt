package dev.icode.mobile.data.git

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.GitHistory
import dev.icode.mobile.domain.GitHistoryIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Source Control's History sheet (docs/source-control.md): HEAD's history, [PAGE] commits at a
 * time. Reading it changes nothing, so it never waits on the mutation lock; one read runs at a time.
 */
internal class GitHistoryCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val git: GitHistoryOperations,
    private val activeRoot: () -> String?,
) {
    private var job: Job? = null

    fun handle(intent: GitHistoryIntent) = when (intent) {
        GitHistoryIntent.Open -> open()
        GitHistoryIntent.More -> more()
    }

    /**
     * The first page, read again each time the sheet opens: a branch, a tag or a remote's branch may
     * have come to a commit since. The rows read before stay on screen until it lands, while HEAD is
     * where it was.
     */
    private fun open() {
        val workspace = activeRoot() ?: return
        val head = stateHolder.current.gitRepository?.lastCommit?.id
        job?.cancel()
        val first = GitHistory(workspace, head, complete = head == null)
        if (head == null) {
            stateHolder.update { it.copy(gitHistory = first) }
            return
        }
        val held = stateHolder.current.gitHistory?.takeIf { it.isFor(workspace, head) }
        read(shown = (held ?: first).copy(loading = true, failure = null), from = first)
    }

    /** The page after the rows the sheet holds; after a failure, the page that failed. */
    private fun more() {
        val history = stateHolder.current.gitHistory ?: return
        if (history.loading || history.complete) return
        val shown = history.copy(loading = true, failure = null)
        read(shown, from = shown)
    }

    /** Shows [shown] while the page after [from]'s rows is read, then [from] with that page, or why it was not read. */
    private fun read(shown: GitHistory, from: GitHistory) {
        stateHolder.update { it.copy(gitHistory = shown) }
        job = scope.launch {
            val next = try {
                from.withPage(git.history(from.workspace, from.rows.size, PAGE), PAGE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                from.copy(loading = false, failure = stateHolder.failureText("git", "Unable to read the history", error))
            }
            // Opened again meanwhile, the sheet waits on that read instead.
            stateHolder.update { if (it.gitHistory == shown) it.copy(gitHistory = next) else it }
        }
    }

    private companion object {
        /** A screen's worth many times over, and one PRoot start for each. */
        const val PAGE = 100
    }
}
