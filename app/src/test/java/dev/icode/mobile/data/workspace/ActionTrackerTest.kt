package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.Busy
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.BusyRules
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.dispatchAndWait
import dev.icode.mobile.domain.isBusy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionTrackerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateHolder = ICodeStateHolder()
    private val tracker = ActionTracker(stateHolder, scope)

    @After
    fun stop() = scope.cancel()

    @Test
    fun `an action is busy from the moment it is dispatched until it ends, however it ends`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val job = tracker.launch(ICodeIntent.OpenWorkspace("/w/a")) { gate.await(); error("failed") }!!
        // Published on the dispatching thread: before the work has had any chance to run.
        assertTrue(stateHolder.current.isBusy(BusyAction.OpenWorkspace, "/w/a"))
        assertFalse(stateHolder.current.isBusy(BusyAction.OpenWorkspace, "/w/b"))
        assertTrue(stateHolder.current.isBusy(BusyAction.OpenWorkspace))
        gate.complete(Unit)
        job.join()
        assertTrue(stateHolder.current.busy.isEmpty())
    }

    @Test
    fun `a repeated tap does nothing while the first runs, and works again after`() = runBlocking {
        val runs = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val first = tracker.launch(ICodeIntent.CommitStaged("one")) { runs.incrementAndGet(); gate.await() }!!
        assertNull(tracker.launch(ICodeIntent.CommitStaged("two")) { runs.incrementAndGet() })
        gate.complete(Unit)
        first.join()
        tracker.launch(ICodeIntent.CommitStaged("three")) { runs.incrementAndGet() }!!.join()
        assertEquals(2, runs.get())
    }

    @Test
    fun `a save queues behind the one running, and stays busy until the last ends`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val first = tracker.launch(ICodeIntent.SaveDocument("/w/a.kt")) { gate.await() }!!
        val second = tracker.launch(ICodeIntent.SaveDocument("/w/a.kt")) { gate.await() }!!
        assertTrue(stateHolder.current.isBusy(BusyAction.SaveDocument, "/w/a.kt"))
        gate.complete(Unit)
        first.join()
        second.join()
        assertTrue(stateHolder.current.busy.isEmpty())
    }

    @Test
    fun `an intent with no busy runs as before, and instant ones run on the spot`() = runBlocking {
        assertNull(BusyRules.of(ICodeIntent.SaveDocument("/w/a.kt", automatic = true)))
        val ran = CompletableDeferred<Unit>()
        tracker.launch(ICodeIntent.SelectDocument("/w/a.kt")) { ran.complete(Unit) }!!.join()
        assertTrue(ran.isCompleted)
        var instant = false
        tracker.route(ICodeIntent.SelectDocument("/w/a.kt"), instant = { instant = true }) { error("not launched") }
        assertTrue(instant)
        assertEquals(Busy(BusyAction.Stage, BusyRules.ALL), BusyRules.of(ICodeIntent.StageAllFiles))
    }

    @Test
    fun `an answer held open waits for its work to end, and one with no work does not wait`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val gateway = object : ICodeGateway {
            override val snapshot = stateHolder.snapshot
            override fun dispatch(intent: ICodeIntent) {
                tracker.launch(intent) { if (intent is ICodeIntent.ReloadFromDisk) gate.await() }
            }
        }
        val reload = async { gateway.dispatchAndWait(ICodeIntent.ReloadFromDisk("/w/a.kt")) }
        delay(50)
        assertFalse("held while the reload runs", reload.isCompleted)
        gate.complete(Unit)
        withTimeout(2_000) { reload.await() }
        // Done before the wait could look, and no busy at all: neither waits.
        withTimeout(2_000) { gateway.dispatchAndWait(ICodeIntent.DiscardDraft("/w/a.kt")) }
        withTimeout(2_000) { gateway.dispatchAndWait(ICodeIntent.SelectDocument("/w/a.kt")) }
    }
}
