package dev.icode.mobile.data.git

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.DocumentLineChanges
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.StageLineChange
import dev.icode.mobile.domain.TextEncoding
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GitLineChangesTest {
    private val top = "/workspaces/app"
    private val location = "$top/src/a.txt"
    private val git = FakeIndex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateHolder = ICodeStateHolder()
    private val lineChanges = GitLineChanges(stateHolder, scope, git)

    @After fun stop() = scope.cancel()

    private fun open(text: String) = stateHolder.update {
        it.copy(openDocuments = listOf(OpenDocument("a.txt", location, text, TextEncoding.Utf8, LineEnding.Lf, dirty = true, readOnly = false)))
    }

    private fun edit(text: String) = stateHolder.update { it.copy(openDocuments = it.openDocuments.map { document -> document.copy(text = text) }) }

    private suspend fun changesWhen(predicate: (DocumentLineChanges?) -> Boolean): DocumentLineChanges? =
        withTimeout(TIMEOUT) { stateHolder.snapshot.first { predicate(it.lineChanges[location]) } }.lineChanges[location]

    @Test fun `an open file's changes against its index version are published, and follow its edits`() = runBlocking {
        git.tracked("src/a.txt", "one\ntwo\n")
        open("one\nTWO\n")
        val changes = changesWhen { it != null }!!
        assertEquals("one\ntwo\n", changes.base)
        assertEquals(listOf("@@ -2 +2 @@"), changes.changes.map { it.header })
        assertEquals(listOf(listOf("src/a.txt")), git.reads)
        edit("one\ntwo\nthree\n")
        assertEquals(listOf("@@ -2,0 +3 @@"), changesWhen { it?.changes?.singleOrNull()?.header == "@@ -2,0 +3 @@" }!!.changes.map { it.header })
        edit("one\ntwo\n")
        assertTrue(changesWhen { it?.changes?.isEmpty() == true }!!.changes.isEmpty())
        assertEquals("The file is read once", 1, git.reads.size)
    }

    @Test fun `an untracked file, or one outside any repository, has none`() = runBlocking {
        open("new\n")
        withTimeout(TIMEOUT) { while (git.reads.isEmpty()) delay(10) }
        delay(SETTLE)
        assertNull(stateHolder.current.lineChanges[location])
        git.tops.clear()
        stateHolder.update { it.copy(openDocuments = emptyList()) }
        delay(SETTLE)
        open("new\n")
        delay(SETTLE)
        assertEquals("Outside a repository nothing is asked of Git", 1, git.reads.size)
        assertNull(stateHolder.current.lineChanges[location])
    }

    @Test fun `the index is read again only when it changed`() = runBlocking {
        git.tracked("src/a.txt", "one\n")
        open("one\nmore\n")
        changesWhen { it?.changes?.size == 1 }
        lineChanges.repositoryRead()
        delay(SETTLE)
        assertEquals(1, git.reads.size)
        // `git add` in the terminal: the index file changed, and holds the file as it is now.
        git.tracked("src/a.txt", "one\nmore\n")
        git.stamp = "2"
        lineChanges.repositoryRead()
        assertTrue(changesWhen { it?.changes?.isEmpty() == true }!!.changes.isEmpty())
        assertEquals(2, git.reads.size)
    }

    @Test fun `with the markers off nothing is read and the markers go`() = runBlocking {
        val markers = MutableStateFlow(true)
        lineChanges.follow(markers)
        git.tracked("src/a.txt", "one\n")
        open("two\n")
        changesWhen { it != null }
        markers.value = false
        changesWhen { it == null }
        git.stamp = "2"
        lineChanges.repositoryRead()
        delay(SETTLE)
        assertEquals(1, git.reads.size)
        markers.value = true
        changesWhen { it != null }
        assertEquals(2, git.reads.size)
    }

    @Test fun `staging writes the index version with that change alone, encoded as it was`() = runBlocking {
        // UTF-8 with a byte order mark, CRLF: what is staged keeps both.
        git.tracked("src/a.txt", "\uFEFFone\r\ntwo\r\nthree\r\n")
        open("one\r\nTWO\r\nthree\r\nfour\r\n")
        val changes = changesWhen { it?.changes?.size == 2 }!!
        lineChanges.stage(StageLineChange(location, changes.changes.first()))
        val staged = git.staged.single()
        assertEquals(listOf(top, "src/a.txt", "100644"), listOf(staged.top, staged.path, staged.mode))
        assertEquals("\uFEFFone\r\nTWO\r\nthree\r\n", staged.bytes.decodeToString())
    }

    @Test fun `a change the file no longer holds is not staged`() = runBlocking {
        git.tracked("src/a.txt", "one\n")
        open("ONE\n")
        val change = changesWhen { it?.changes?.size == 1 }!!.changes.single()
        edit("uno\n")
        try {
            lineChanges.stage(StageLineChange(location, change))
            fail("A change no longer in the file was staged")
        } catch (refused: IOException) {
            assertEquals("The file changed since this change was shown; look at it again", refused.message)
        }
        assertTrue(git.staged.isEmpty())
    }

    private inner class FakeIndex : GitIndexOperations {
        val tops = mutableMapOf(location to top)
        var stamp = "1"
        val versions = mutableMapOf<String, IndexVersion>()
        val reads = mutableListOf<List<String>>()
        val staged = mutableListOf<Staged>()

        fun tracked(path: String, text: String) {
            versions[path] = IndexVersion("100644", text.toByteArray())
        }

        override suspend fun isAvailable() = true
        override fun repositoryTopOf(location: String) = tops[location]
        override fun indexStampOf(top: String) = stamp
        override suspend fun indexVersions(top: String, paths: List<String>): Map<String, IndexVersion> {
            reads += paths
            return paths.mapNotNull { path -> versions[path]?.let { path to it } }.toMap()
        }
        override suspend fun stageVersion(top: String, path: String, mode: String, bytes: ByteArray) {
            staged += Staged(top, path, mode, bytes)
        }
    }

    private class Staged(val top: String, val path: String, val mode: String, val bytes: ByteArray)

    private companion object {
        const val TIMEOUT = 5_000L
        /** Longer than the 200 ms the changes wait for, with room to spare. */
        const val SETTLE = 600L
    }
}
