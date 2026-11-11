package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ChangedOnDiskException
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceDocument
import dev.icode.mobile.domain.GitConflictChoice
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.notices.NoticeTone
import dev.icode.mobile.domain.Said
import dev.icode.mobile.domain.TextEncoding
import dev.icode.mobile.domain.said
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitConflictCoordinatorTest {
    private val root = "/storage/emulated/0/app"
    private val location = "$root/src/f.txt"
    private val originalWriter = BackendLog.writer
    private val twoConflicts = listOf(
        "top",
        "<<<<<<< HEAD", "a1", "=======", "b1", ">>>>>>> other",
        "middle",
        "<<<<<<< HEAD", "a2", "=======", "b2", ">>>>>>> other",
        "bottom",
    ).joinToString("\n")

    @Before fun quietLog() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
    }

    /** A disk of one file, with the fingerprint check the real source makes. */
    private class FakeFiles(var disk: String) : ConflictFiles {
        var version = 1
        var unsaved = false
        val written = mutableListOf<String>()

        private fun document(location: String) = WorkspaceDocument(
            name = location.substringAfterLast('/'),
            location = location,
            text = disk,
            encoding = TextEncoding.Utf8,
            lineEnding = LineEnding.Lf,
            hasByteOrderMark = false,
            readOnly = false,
            fingerprint = "v$version",
        )

        override fun read(location: String) = document(location)
        override fun write(document: WorkspaceDocument, text: String): WorkspaceDocument {
            if (document.fingerprint != "v$version") throw ChangedOnDiskException()
            disk = text
            version += 1
            return document(document.location)
        }
        override fun hasUnsavedEdits(location: String) = unsaved
        override suspend fun written(location: String) {
            written += location
        }
    }

    private inner class Harness(val files: FakeFiles) {
        val stateHolder = ICodeStateHolder()
        val events = mutableListOf<String>()
        val coordinator = GitConflictCoordinator(
            stateHolder,
            files,
            activeRoot = { root },
            act = { _, operation, onSuccess ->
                operation(root)
                onSuccess()
            },
            stage = { workspace, path -> events += "stage $workspace $path" },
            refresh = { events += "refresh" },
        )
    }

    @Test fun `one block is resolved and every other keeps its markers`() = runBlocking {
        val harness = Harness(FakeFiles(twoConflicts))
        harness.coordinator.open("src/f.txt")
        val second = harness.stateHolder.current.activeConflict!!.sections[1]

        harness.coordinator.resolve("src/f.txt", second.startLine, GitConflictChoice.Incoming)

        assertEquals(
            "top\n<<<<<<< HEAD\na1\n=======\nb1\n>>>>>>> other\nmiddle\nb2\nbottom",
            harness.files.disk,
        )
        // Read again from disk: one block left to decide.
        assertEquals(1, harness.stateHolder.current.activeConflict!!.sections.size)
        assertEquals(listOf(location), harness.files.written)
        assertEquals(listOf("refresh"), harness.events)
    }

    @Test fun `a windows file keeps its line endings`() = runBlocking {
        val harness = Harness(FakeFiles("a\r\n<<<<<<< HEAD\r\nx\r\n=======\r\ny\r\n>>>>>>> b\r\nz\r\n"))
        harness.coordinator.open("src/f.txt")
        val section = harness.stateHolder.current.activeConflict!!.sections.single()

        harness.coordinator.resolve("src/f.txt", section.startLine, GitConflictChoice.Current)

        assertEquals("a\r\nx\r\nz\r\n", harness.files.disk)
    }

    @Test fun `an open tab with unsaved edits is never written under`() = runBlocking {
        val harness = Harness(FakeFiles(twoConflicts).apply { unsaved = true })
        harness.coordinator.open("src/f.txt")
        val first = harness.stateHolder.current.activeConflict!!.sections[0]
        val message = async { harness.stateHolder.effects.first() }
        yield()

        harness.coordinator.resolve("src/f.txt", first.startLine, GitConflictChoice.Current)

        assertEquals(Said(NoticeTone.HeadsUp, "Save or close f.txt first", "It has unsaved edits in the editor."), withTimeout(2_000) { message.await() }.said())
        assertEquals(twoConflicts, harness.files.disk)
    }

    @Test fun `a file changed on disk is read again instead of overwritten`() = runBlocking {
        val harness = Harness(FakeFiles(twoConflicts))
        harness.coordinator.open("src/f.txt")
        val first = harness.stateHolder.current.activeConflict!!.sections[0]
        // Someone fixes the second block in the terminal meanwhile.
        harness.files.disk = twoConflicts.replace("<<<<<<< HEAD\na2\n=======\nb2\n>>>>>>> other", "a2")
        harness.files.version += 1
        val message = async { harness.stateHolder.effects.first() }
        yield()

        harness.coordinator.resolve("src/f.txt", first.startLine, GitConflictChoice.Current)

        assertEquals(Said(NoticeTone.HeadsUp, "f.txt changed on disk", "It was read again. Choose again."), withTimeout(2_000) { message.await() }.said())
        assertTrue("a2" in harness.files.disk && "b1" in harness.files.disk)
        assertEquals(1, harness.stateHolder.current.activeConflict!!.sections.size)
        assertTrue(harness.events.isEmpty())
    }

    @Test fun `marking resolved stages the file`() = runBlocking {
        val harness = Harness(FakeFiles("done\n"))

        harness.coordinator.markResolved("src/f.txt")

        assertEquals(listOf("stage $root src/f.txt"), harness.events)
    }

    @Test fun `closing forgets the file`() = runBlocking {
        val harness = Harness(FakeFiles(twoConflicts))
        harness.coordinator.open("src/f.txt")

        harness.coordinator.close()

        assertNull(harness.stateHolder.current.activeConflict)
    }
}
