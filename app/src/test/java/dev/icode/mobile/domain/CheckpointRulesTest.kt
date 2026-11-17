package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointRulesTest {
    private fun file(hash: String, executable: Boolean = false) = CheckpointFile(hash, 1, 1, executable)

    private fun at(vararg files: Pair<String, CheckpointFile>) = Checkpoint(0, files.toMap())

    @Test fun `what tools make again is left out, and in the home hidden folders too`() {
        assertTrue(CheckpointRules.skips("node_modules", home = false))
        assertTrue(CheckpointRules.skips(".git", home = false))
        assertFalse(CheckpointRules.skips(".github", home = false))
        assertTrue(CheckpointRules.skips(".github", home = true))
        assertFalse(CheckpointRules.skips("src", home = true))
    }

    @Test fun `a change is new content, a file added or removed, or one that runs or no longer does`() {
        val from = at("same" to file("a"), "edited" to file("b"), "gone" to file("c"), "runs" to file("d"))
        val to = at("same" to file("a").copy(modified = 9), "edited" to file("x"), "added" to file("e"), "runs" to file("d", executable = true))
        assertEquals(setOf("added", "edited", "gone", "runs"), CheckpointRules.changed(from, to))
    }

    @Test fun `outside is what changed between a turn's end and what followed it, never during a turn`() {
        val ended = at("a.kt" to file("turn"))
        val next = at("a.kt" to file("turn"), "notes" to file("user"))
        val now = at("a.kt" to file("turn"), "notes" to file("user, later"))
        assertEquals(setOf("notes"), CheckpointRules.outside(listOf(ended to next, null to now)))
        assertEquals(setOf("notes"), CheckpointRules.outside(listOf(next to now)))
    }
}
