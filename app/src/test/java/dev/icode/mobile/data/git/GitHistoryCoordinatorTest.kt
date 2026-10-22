package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.GitCommitSummary
import dev.icode.mobile.domain.GitHistory
import dev.icode.mobile.domain.GitHistoryCommit
import dev.icode.mobile.domain.GitHistoryIntent
import dev.icode.mobile.domain.GitRepositoryState
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHistoryCoordinatorTest {
    private val root = "/workspaces/app"
    // 250 commits in a line, newest first.
    private val line = (250 downTo 1).map { GitHistoryCommit("c$it", if (it > 1) listOf("c${it - 1}") else emptyList(), "Ada <ada@example.com>", 0L, emptyList(), "s$it", "") }
    private val git = FakeHistory(line)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateHolder = ICodeStateHolder().apply { update { it.copy(activeWorkspaceLocation = root, gitRepository = repository("c250")) } }
    private val coordinator = GitHistoryCoordinator(stateHolder, scope, git) { root }

    private val originalWriter = BackendLog.writer
    private val logged = mutableListOf<String>()

    @Before fun captureLog() {
        BackendLog.writer = { _, tag, message -> logged += "$tag ${message.lineSequence().first()}" }
    }

    @After fun stop() {
        scope.cancel()
        BackendLog.writer = originalWriter
    }

    private fun repository(head: String) = GitRepositoryState(
        workTreePath = root, branch = "main", hasCommits = true, remoteName = null, authorName = null, authorEmail = null,
        branches = listOf("main"), lastCommit = GitCommitSummary(head, "s", "Ada", 0L),
    )

    private suspend fun settled(): GitHistory = withTimeout(2_000) { stateHolder.snapshot.first { it.gitHistory?.loading == false } }.gitHistory!!

    @Test fun `the sheet reads a page, the next as the list reaches its end, and knows the last`() = runBlocking {
        coordinator.handle(GitHistoryIntent.Open)
        assertEquals(100, settled().rows.size)
        coordinator.handle(GitHistoryIntent.More)
        assertEquals(200, settled().rows.size)
        coordinator.handle(GitHistoryIntent.More)
        val all = settled()
        assertEquals(line.map { it.id }, all.rows.map { it.commit.id })
        assertTrue(all.complete)
        assertEquals(listOf(0, 100, 200), git.skips)
        coordinator.handle(GitHistoryIntent.More)
        assertEquals(listOf(0, 100, 200), git.skips)
    }

    @Test fun `a page that failed says Git's words, and More reads that page again`() = runBlocking {
        coordinator.handle(GitHistoryIntent.Open)
        settled()
        git.failure = IOException("fatal: bad object")
        coordinator.handle(GitHistoryIntent.More)
        val failed = settled()
        assertEquals("fatal: bad object", failed.failure)
        assertEquals(listOf("iCode/git Unable to read the history"), logged)
        assertEquals(100, failed.rows.size)
        git.failure = null
        coordinator.handle(GitHistoryIntent.More)
        val read = settled()
        assertNull(read.failure)
        assertEquals(200, read.rows.size)
        assertEquals(listOf(0, 100, 100), git.skips)
    }

    @Test fun `opened again, it shows the rows it holds while the first page is read afresh`() = runBlocking {
        coordinator.handle(GitHistoryIntent.Open)
        settled()
        coordinator.handle(GitHistoryIntent.More)
        settled()
        git.gate = CompletableDeferred()
        coordinator.handle(GitHistoryIntent.Open)
        val reading = stateHolder.current.gitHistory!!
        assertTrue(reading.loading)
        assertEquals(200, reading.rows.size)
        git.gate!!.complete(Unit)
        assertEquals(100, settled().rows.size)
    }

    @Test fun `HEAD moved, the rows of the old HEAD are not shown while the new history is read`() = runBlocking {
        coordinator.handle(GitHistoryIntent.Open)
        settled()
        stateHolder.update { it.copy(gitRepository = repository("c249")) }
        git.gate = CompletableDeferred()
        coordinator.handle(GitHistoryIntent.Open)
        val reading = stateHolder.current.gitHistory!!
        assertEquals("c249", reading.head)
        assertTrue(reading.rows.isEmpty())
    }

    private class FakeHistory(val commits: List<GitHistoryCommit>) : GitHistoryOperations {
        val skips = mutableListOf<Int>()
        @Volatile var failure: IOException? = null
        @Volatile var gate: CompletableDeferred<Unit>? = null

        override suspend fun history(workspacePath: String, skip: Int, count: Int): List<GitHistoryCommit> {
            skips += skip
            gate?.await()
            failure?.let { throw it }
            return commits.drop(skip).take(count)
        }
    }
}
