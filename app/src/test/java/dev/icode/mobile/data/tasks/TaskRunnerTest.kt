package dev.icode.mobile.data.tasks

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.WorkTask
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticeSource
import dev.icode.mobile.domain.notices.NoticeTone
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskRunnerTest {
    private val originalWriter = BackendLog.writer
    private val stateHolder = ICodeStateHolder()
    private var now = 0L
    private val runner = TaskRunner(stateHolder, CoroutineScope(SupervisorJob() + Dispatchers.Default)) { now }

    @Before fun quietLog() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
    }

    private fun task(): WorkTask? = stateHolder.current.tasks.singleOrNull()

    private fun failed(verb: String) = Notice(NoticeTone.Problem, NoticeSource.Files, "Couldn't $verb it")

    /** Waits, briefly, for [condition]: the work runs on its own thread. */
    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out; tasks: ${stateHolder.current.tasks}" }
            Thread.sleep(5)
        }
    }

    @Test fun `a task shows while it runs, counts, reports, and goes when it is done`() = runBlocking {
        val release = CountDownLatch(1)
        val finished = CompletableDeferred<Pair<Throwable?, Boolean>>()
        runner.start(
            "Deleting…", "node_modules", listOf("/w/node_modules"), cancellable = false, failed = failed("delete"),
            work = { progress ->
                progress.counted(3)
                now += 1_000
                progress.item("node_modules/a")
                release.await(5, TimeUnit.SECONDS)
            },
            finished = { outcome -> finished.complete(outcome to stateHolder.mutations.isLocked) },
        )

        until { task()?.total == 3L && task()?.done == 1L }
        assertEquals("node_modules/a", task()?.currentItem)
        assertEquals("Deleting…", task()?.title)
        release.countDown()

        val (outcome, locked) = withTimeout(5_000) { finished.await() }
        assertNull(outcome)
        assertTrue("its result is applied under the mutation lock", locked)
        assertTrue(stateHolder.current.tasks.isEmpty())
    }

    @Test fun `progress reaches the screen a few times a second, not at every item`() {
        val release = CountDownLatch(1)
        runner.start(
            "Deleting…", "big", listOf("/w/big"), cancellable = false, failed = failed("delete"),
            work = { progress ->
                progress.counted(1_000)
                repeat(999) { progress.item("big/$it") }
                release.await(5, TimeUnit.SECONDS)
            },
            finished = {},
        )
        until { task()?.total == 1_000L }
        // The clock never moved: after the count, no item is published.
        Thread.sleep(50)
        assertEquals(0L, task()?.done)
        release.countDown()
    }

    @Test fun `a failure is told, stays, comes back from the background, and goes when dismissed`() = runBlocking {
        val told = async { stateHolder.effects.first { it is ICodeEffect.Told } as ICodeEffect.Told }
        yield()
        val id = runner.start(
            "Copying…", "src", listOf("/w/src"), cancellable = true, failed = failed("copy"),
            work = { throw IOException("No space left on device") },
            finished = {},
        )
        runner.handle(ICodeIntent.HideTask(id))

        until { task()?.failure != null }
        assertEquals("No space left on device", task()?.failure)
        val notice = withTimeout(5_000) { told.await() }.notice
        assertEquals("Couldn't copy it" to "No space left on device", notice.title to notice.output)
        assertFalse(task()!!.hidden)
        assertNull("its paths are free again", runner.claimedBy("/w/src"))

        runner.handle(ICodeIntent.HideTask(id))
        assertFalse("a Hide that comes after the failure does not hide it", task()!!.hidden)

        runner.handle(ICodeIntent.DismissTask(id))
        assertTrue(stateHolder.current.tasks.isEmpty())
    }

    @Test fun `a running task holds its paths, and the folders around them`() {
        val release = CountDownLatch(1)
        runner.start(
            "Deleting…", "lib", listOf("/w/src/lib"), cancellable = false, failed = failed("delete"),
            work = { release.await(5, TimeUnit.SECONDS) },
            finished = {},
        )
        until { task() != null }
        assertEquals("lib", runner.claimedBy("/w/src/lib/a.kt")?.subject)
        assertEquals("lib", runner.claimedBy("/w/src")?.subject)
        assertNull(runner.claimedBy("/w/src/library"))
        assertNull(runner.claimedBy("/w/docs"))
        release.countDown()
        until { task() == null }
        assertNull(runner.claimedBy("/w/src/lib"))
    }

    @Test fun `a Cancel on the task's first frame stops it and frees its paths, whenever it lands`() {
        repeat(50) {
            val id = runner.start(
                "Copying…", "big", listOf("/w/big"), cancellable = true, failed = failed("copy"),
                work = { progress -> while (true) progress.item("big/x") },
                finished = {},
            )
            runner.handle(ICodeIntent.CancelTask(id))
            until { stateHolder.current.tasks.isEmpty() }
            assertNull(runner.claimedBy("/w/big"))
        }
    }

    @Test fun `running into a hidden task brings its dialog back, with the reason to wait`() {
        val release = CountDownLatch(1)
        val id = runner.start(
            "Deleting…", "lib", listOf("/w/src/lib"), cancellable = false, failed = failed("delete"),
            work = { release.await(5, TimeUnit.SECONDS) },
            finished = {},
        )
        runner.handle(ICodeIntent.HideTask(id))
        until { task()?.hidden == true }
        assertNull(runner.waitReason("/w/docs"))
        assertTrue(task()!!.hidden)
        assertEquals("Deleting lib — wait for it to finish", runner.waitReason("/w/src"))
        assertFalse(task()!!.hidden)
        release.countDown()
        until { task() == null }
    }

    @Test fun `only a cancellable task stops when cancelled`() = runBlocking {
        val deleting = CountDownLatch(1)
        val deleteId = runner.start(
            "Deleting…", "a", listOf("/w/a"), cancellable = false, failed = failed("delete"),
            work = { progress -> deleting.await(5, TimeUnit.SECONDS); progress.item("a/x") },
            finished = {},
        )
        until { task() != null }
        runner.handle(ICodeIntent.CancelTask(deleteId))
        deleting.countDown()
        until { task() == null }

        val stopped = CompletableDeferred<Throwable?>()
        val copyId = runner.start(
            "Copying…", "b", listOf("/w/b"), cancellable = true, failed = failed("copy"),
            work = { progress -> while (true) { progress.item("b/x"); Thread.sleep(5) } },
            finished = { outcome -> stopped.complete(outcome) },
        )
        until { task() != null }
        runner.handle(ICodeIntent.CancelTask(copyId))
        assertTrue(withTimeout(5_000) { stopped.await() } is kotlin.coroutines.cancellation.CancellationException)
        assertTrue("a cancelled task is not a failure", stateHolder.current.tasks.isEmpty())
    }

    @Test fun `hide and show move a task between its dialog and the background`() {
        val release = CountDownLatch(1)
        val id = runner.start(
            "Deleting…", "a", listOf("/w/a"), cancellable = false, failed = failed("delete"),
            work = { release.await(5, TimeUnit.SECONDS) },
            finished = {},
        )
        until { task() != null }
        runner.handle(ICodeIntent.HideTask(id))
        assertTrue(task()!!.hidden)
        runner.handle(ICodeIntent.ShowTask(id))
        assertFalse(task()!!.hidden)
        release.countDown()
    }
}
