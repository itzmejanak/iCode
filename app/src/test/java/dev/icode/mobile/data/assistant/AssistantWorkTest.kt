package dev.icode.mobile.data.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Chats at work at once (docs/assistant-and-mcp.md), one job each, and the phone's screen one chat's at a time. */
class AssistantWorkTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var idled = 0
    /** Each chat that ended, and whether it goes on. */
    private val ended = java.util.Collections.synchronizedList(mutableListOf<Pair<Pair<String?, String>, Boolean>>())
    private val work = AssistantWork(scope) { chat, last, goesOn ->
        ended += chat to goesOn
        if (last) idled++
    }
    private val a = "/w/app" to "a"
    private val b = "/w/app" to "b"
    private val home = null to "c"

    @After fun stop() = scope.cancel()

    /** [chat] at work until it is stopped. */
    private fun busy(chat: Pair<String?, String>) = work.start(chat) { awaitCancellation() }

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            Thread.sleep(2)
        }
    }

    @Test fun `each chat works on its own, one job in a chat`() = runBlocking {
        assertTrue(busy(a))
        assertFalse("a chat at work takes no second job", busy(a))
        assertTrue(busy(home))
        assertTrue(work.runs(a) && work.runs(home))
        assertFalse(work.runs(b))
    }

    @Test fun `a stop reaches only its chat, and the last chat to end idles what serves them all`() = runBlocking {
        busy(a)
        busy(home)
        work.stop(a)
        until { !work.runs(a) }
        assertTrue(work.runs(home))
        assertEquals(0, idled)
        work.stop(home)
        until { idled == 1 }
        assertFalse(work.runs(home))
    }

    @Test fun `a chat stopped before its job began is not left working`() = runBlocking {
        repeat(100) {
            assertTrue(busy(a))
            work.stop(a)
            until { !work.runs(a) }
        }
    }

    @Test fun `stopping a workspace's chats waits for each, and leaves the others`() = runBlocking {
        busy(a)
        busy(b)
        busy(home)
        withTimeout(5_000) { work.stopIn("/w/app") }
        until { !work.runs(a) && !work.runs(b) }
        assertTrue(work.runs(home))
        // Stopped with their workspace, they do not go on; a chat stopped on its own does.
        assertEquals(setOf(a to false, b to false), ended.toSet())
        work.stop(home)
        until { ended.size == 3 }
        assertEquals(home to true, ended.last())
    }

    @Test fun `the phone's screen is one chat's until its turn ends`() = runBlocking {
        busy(a)
        busy(b)
        assertTrue(work.takePhone(a))
        assertFalse("taken already", work.takePhone(a))
        try {
            work.takePhone(b)
            fail("another chat had the screen")
        } catch (refused: AssistantToolException) {
            assertEquals("Another chat is using the phone's screen now. Try again when it is done, or tell the user.", refused.message)
        }
        work.stop(a)
        until { !work.runs(a) }
        assertTrue(work.takePhone(b))
    }
}
