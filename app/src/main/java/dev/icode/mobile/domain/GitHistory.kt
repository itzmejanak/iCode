package dev.icode.mobile.domain

// The branch's history, as Source Control's History sheet shows it (docs/source-control.md): a line
// per commit, newest first, beside a graph of where each one branched and merged.

/** A commit as `git log` reads it. */
data class GitHistoryCommit(
    val id: String,
    /** Its parents' ids: none for a root commit, two or more for a merge. */
    val parents: List<String>,
    /** "Name <email>", as Last Commit names its author ([GitCommitSummary.author]). */
    val author: String,
    val authoredAtEpochMillis: Long,
    /** The names it carries: HEAD's branch, other branches, remotes' branches, tags. */
    val refs: List<GitRef>,
    /** Its message's first paragraph, on one line. */
    val subject: String,
    /** The rest of its message, after the subject's paragraph; empty when it has none. */
    val body: String,
)

data class GitRef(val name: String, val kind: GitRefKind)

/** [Head] is HEAD's branch, or HEAD itself where it is detached. */
enum class GitRefKind { Head, Branch, Remote, Tag }

/**
 * Where a row's commit stands in the graph. Each lane carries the commit it waits for, a null being a
 * lane left open: [above] as the row above leaves them, [below] as this row does. The commit is on
 * [lane]; the lanes waiting for it meet there, and it leaves by [parentLanes], one per parent.
 */
data class GitGraphRow(val lane: Int, val above: List<String?>, val below: List<String?>, val parentLanes: List<Int>) {
    /** How many lanes the row draws. */
    val width: Int get() = maxOf(above.size, below.size, lane + 1)
}

data class GitHistoryRow(val commit: GitHistoryCommit, val graph: GitGraphRow)

object GitGraph {
    /**
     * [commits], newest first and each before its parents (`git log --topo-order`), in lanes going on
     * from [lanes], what the rows above them left: a page laid out after the last page is the history
     * laid out whole. A commit takes the lane waiting for it, or the first one open; its first parent
     * goes on in that lane, and a merge's other parents join a lane already waiting for them or open one.
     */
    fun layout(commits: List<GitHistoryCommit>, lanes: List<String?> = emptyList()): List<GitHistoryRow> {
        val open = lanes.toMutableList()
        return commits.map { commit ->
            val above = open.toList()
            val lane = open.indexOf(commit.id).takeIf { it >= 0 } ?: free(open)
            for (index in open.indices) if (open[index] == commit.id) open[index] = null
            val parentLanes = commit.parents.mapIndexed { order, parent ->
                val waiting = open.indexOf(parent)
                when {
                    order == 0 -> lane.also { open[it] = parent }
                    waiting >= 0 -> waiting
                    else -> free(open).also { open[it] = parent }
                }
            }
            while (open.isNotEmpty() && open.last() == null) open.removeAt(open.lastIndex)
            GitHistoryRow(commit, GitGraphRow(lane, above, open.toList(), parentLanes))
        }
    }

    /** The first lane left open, or a new one at the right. */
    private fun free(open: MutableList<String?>): Int =
        open.indexOf(null).takeIf { it >= 0 } ?: open.size.also { open.add(null) }
}

/**
 * The History sheet's history: [workspace]'s from HEAD, read when HEAD was [head]
 * ([GitCommitSummary.id]), a page at a time.
 */
data class GitHistory(
    val workspace: String,
    val head: String?,
    val rows: List<GitHistoryRow> = emptyList(),
    val loading: Boolean = false,
    /** Every commit has been read. */
    val complete: Boolean = false,
    /** Why the last page could not be read, in Git's words. */
    val failure: String? = null,
) {
    /** Whether this is the history of [workspace]'s HEAD as it stands at [head]: what the sheet shows, else it waits. */
    fun isFor(workspace: String?, head: String?): Boolean = this.workspace == workspace && this.head == head

    /** With [commits], the page after [rows], laid out below them; the last page is one shorter than [pageSize]. */
    fun withPage(commits: List<GitHistoryCommit>, pageSize: Int): GitHistory = copy(
        rows = rows + GitGraph.layout(commits, rows.lastOrNull()?.graph?.below.orEmpty()),
        loading = false,
        complete = commits.size < pageSize,
        failure = null,
    )
}

/** The History sheet: HEAD's history, then each further page as the list reaches its end. */
sealed interface GitHistoryIntent : ICodeIntent {
    data object Open : GitHistoryIntent
    data object More : GitHistoryIntent
}
