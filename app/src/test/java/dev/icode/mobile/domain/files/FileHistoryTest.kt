package dev.icode.mobile.domain.files

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Undo and redo's model, the question's answers, Trash's days (docs/explorer-gaps-plan.md §3.6–§3.8). */
class FileHistoryTest {
    @Test fun `a change is undone from its last step to its first, each step reversed`() {
        val change = FileChange(
            "Moved 2 items to src", "Moved 2 items back",
            listOf(FileStep.Moved("/w/a", "/w/src/a"), FileStep.Trashed("t1", "/w/src/b"), FileStep.Moved("/w/b", "/w/src/b")),
        )
        assertEquals(
            listOf(Reversal.MoveBack("/w/src/b", "/w/b"), Reversal.Restore("t1", "/w/src/b"), Reversal.MoveBack("/w/src/a", "/w/a")),
            change.reversals,
        )
    }

    @Test fun `what was made goes to Trash, and what was restored goes back there`() {
        assertEquals(Reversal.TrashMade("/w/a copy"), FileChange.reversal(FileStep.Made("/w/a copy")))
        assertEquals(Reversal.TrashMade("/w/a"), FileChange.reversal(FileStep.Restored("t1", "/w/a")))
    }

    @Test fun `undone, a change keeps what the undoing did, its words swapped, to be redone`() {
        val trashed = FileWords.trashed(listOf("a", "b", "c")).copy(steps = listOf(FileStep.Trashed("t1", "/w/a")))
        val undone = trashed.undoneBy(listOf(FileStep.Restored("t1", "/w/a")))
        assertEquals("Restored 3 items", undone.said)
        assertEquals("Moved 3 items to Trash", undone.undoneSaid)
        assertEquals(listOf(Reversal.TrashMade("/w/a")), undone.reversals)
    }

    @Test fun `an empty folder taken away is made again, and one made is taken away again`() {
        assertEquals(Reversal.MakeFolder("/w/src"), FileChange.reversal(FileStep.Removed("/w/src")))
        assertEquals(listOf("/w/a", "/w/b"), FileStep.Moved("/w/a", "/w/b").paths)
        assertEquals(listOf("/w/b"), Reversal.Restore("t1", "/w/b").paths)
    }

    @Test fun `undo is checked against what is there, as the reversals before it leave things`() {
        val change = FileChange(
            "Moved 2 items to src", "Moved 2 items back",
            listOf(FileStep.Trashed("t1", "/w/src/a"), FileStep.Moved("/w/a", "/w/src/a"), FileStep.Moved("/w/b", "/w/src/b")),
        )
        val there = mutableSetOf("/w/src/a", "/w/src/b")
        val trashed = mutableSetOf("t1")
        assertNull(change.refusal({ it in there }) { it in trashed })
        there -= "/w/src/b"
        assertEquals("b was moved or deleted since", change.refusal({ it in there }) { it in trashed })
        there += setOf("/w/src/b", "/w/a")
        assertEquals("“a” is already in w", change.refusal({ it in there }) { it in trashed })
        there -= "/w/a"
        trashed.clear()
        assertEquals("a is no longer in Trash", change.refusal({ it in there }) { it in trashed })
    }

    @Test fun `one item is said by its name, more by how many`() {
        assertEquals("Moved README.md to Trash", FileWords.trashed(listOf("README.md")).said)
        assertEquals("Copied 2 items to src", FileWords.copied(listOf("a", "b"), "src").said)
        assertEquals("Renamed b.txt back to a.txt", FileWords.renamed("a.txt", "b.txt").undoneSaid)
    }

    @Test fun `two folders merge, anything else is replaced`() {
        val folder = ConflictSide(folder = true, bytes = null, changedAt = 0)
        val file = ConflictSide(folder = false, bytes = 10, changedAt = 0)
        assertEquals(
            listOf(ConflictAnswer.Merge, ConflictAnswer.KeepBoth, ConflictAnswer.Skip),
            FileConflict(1, "src", "/workspace/app", folder, folder, others = 0).answers,
        )
        assertEquals(ConflictAnswer.Replace, FileConflict(1, "src", "/workspace/app", file, folder, others = 2).answers.first())
    }

    @Test fun `Trash keeps an item 30 days, and says how much it holds`() {
        val now = TimeUnit.DAYS.toMillis(100)
        val item = TrashedItem("t1", "a", "/w/a", StoragePlace.Private, now - TimeUnit.DAYS.toMillis(29), false, 10, false)
        assertFalse(TrashRules.expired(item, now))
        assertTrue(TrashRules.expired(item.copy(deletedAt = now - TimeUnit.DAYS.toMillis(30)), now))
        assertEquals(TrashSummary(2, 10), TrashRules.summary(listOf(item, item.copy(id = "t2", bytes = null))))
    }
}
