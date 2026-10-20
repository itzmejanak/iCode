package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.Said
import dev.icode.mobile.domain.said
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class ICodeStateHolderTest {
    private val logged = mutableListOf<String>()
    private val originalWriter = BackendLog.writer

    @Before fun captureLog() {
        BackendLog.writer = { _, tag, message -> logged += "$tag $message" }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
    }

    @Test fun `a reported failure is one log line and one notice, its title the caller's and its detail the error's own words`() = runBlocking {
        val holder = ICodeStateHolder()
        val effect = async(start = CoroutineStart.UNDISPATCHED) { holder.effects.first() }
        holder.report("git", "Push failed", IOException("Remote rejected the push"))
        assertEquals(Said(NoticeTone.Problem, "Push failed", "Remote rejected the push"), effect.await().said())
        assertEquals(1, logged.size)
        assertEquals(true, logged.single().startsWith("iCode/git Push failed\njava.io.IOException: Remote rejected the push"))
    }

    @Test fun `an error without words falls back to the caller's`() {
        val holder = ICodeStateHolder()
        assertEquals("Clone failed", holder.failureText("git", "Clone failed", IOException()))
        assertEquals("Clone failed", holder.failureText("git", "Clone failed", IOException("  ")))
    }

    @Test fun `what the user sees never carries a token`() {
        val holder = ICodeStateHolder()
        val text = holder.failureText("git", "Fetch failed", IOException("could not read from https://me:ghp_x@host/r.git"))
        assertEquals("could not read from https://***@host/r.git", text)
        assertFalse(logged.single().contains("ghp_x"))
    }
}
