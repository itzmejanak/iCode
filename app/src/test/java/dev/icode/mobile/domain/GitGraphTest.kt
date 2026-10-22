package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitGraphTest {
    private fun commit(id: String, vararg parents: String) =
        GitHistoryCommit(id, parents.toList(), "Ada <ada@example.com>", 0L, emptyList(), id, "")

    // The repository `git log --graph` draws as
    //   *   merge
    //   |\
    //   | * feature
    //   * | main3
    //   |/
    //   * main2
    //   * main1
    // listed as `git log --topo-order` lists it.
    private val merged = listOf(
        commit("merge", "main3", "feature"),
        commit("feature", "main2"),
        commit("main3", "main2"),
        commit("main2", "main1"),
        commit("main1"),
    )

    @Test fun `a line of commits stays in one lane, and the root ends it`() {
        val rows = GitGraph.layout(listOf(commit("c", "b"), commit("b", "a"), commit("a")))
        assertEquals(listOf(0, 0, 0), rows.map { it.graph.lane })
        assertEquals(listOf(listOf("b"), listOf("a"), emptyList()), rows.map { it.graph.below })
        assertEquals(listOf(1, 1, 1), rows.map { it.graph.width })
    }

    @Test fun `a merge opens a lane for its second parent, and the lanes meet again at the commit both wait for`() {
        val rows = GitGraph.layout(merged).map { it.graph }
        assertEquals(GitGraphRow(0, emptyList(), listOf("main3", "feature"), listOf(0, 1)), rows[0])
        assertEquals(GitGraphRow(1, listOf("main3", "feature"), listOf("main3", "main2"), listOf(1)), rows[1])
        assertEquals(GitGraphRow(0, listOf("main3", "main2"), listOf("main2", "main2"), listOf(0)), rows[2])
        // Both lanes wait for main2: they meet in its lane, and one lane goes on.
        assertEquals(GitGraphRow(0, listOf("main2", "main2"), listOf("main1"), listOf(0)), rows[3])
        assertEquals(GitGraphRow(0, listOf("main1"), emptyList(), emptyList()), rows[4])
        assertEquals(2, rows.maxOf { it.width })
    }

    @Test fun `a merge whose second parent a lane already waits for joins that lane`() {
        // top merges b in; x, below it, merges b in again: no third lane opens for it.
        val rows = GitGraph.layout(listOf(commit("top", "x", "b"), commit("x", "y", "b"), commit("y", "b"), commit("b")))
        assertEquals(GitGraphRow(0, listOf("x", "b"), listOf("y", "b"), listOf(0, 1)), rows[1].graph)
        assertEquals(2, rows.maxOf { it.graph.width })
        assertTrue(rows.last().graph.below.isEmpty())
    }

    @Test fun `a page laid out after the last page is the history laid out whole`() {
        val whole = GitGraph.layout(merged)
        val first = GitGraph.layout(merged.take(2))
        val rest = GitGraph.layout(merged.drop(2), first.last().graph.below)
        assertEquals(whole, first + rest)
    }

    @Test fun `the history reads page after page, complete at the first short one`() {
        val opened = GitHistory("/w", "merge", loading = true)
        val first = opened.withPage(merged.take(2), pageSize = 2)
        assertEquals(2, first.rows.size)
        assertEquals(false, first.complete)
        assertEquals(false, first.loading)
        val last = first.copy(loading = true).withPage(merged.drop(2), pageSize = 100)
        assertEquals(GitGraph.layout(merged), last.rows)
        assertEquals(true, last.complete)
    }
}
