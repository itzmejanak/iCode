package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticeSource
import dev.icode.mobile.domain.notices.NoticeTone
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
import org.junit.Assert.assertTrue
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

    @Test fun `a failure told is one log line and one notice, its title the builder's and its output the error's own words`() = runBlocking {
        val holder = ICodeStateHolder()
        val effect = async(start = CoroutineStart.UNDISPATCHED) { holder.effects.first() }
        holder.tell(Notice(NoticeTone.Problem, NoticeSource.Git, "Couldn't push main"), "git", IOException("Remote rejected the push"))
        assertEquals(Said(NoticeTone.Problem, "Couldn't push main", output = "Remote rejected the push"), effect.await().said())
        assertEquals(1, logged.size)
        assertEquals(true, logged.single().startsWith("iCode/git Couldn't push main\njava.io.IOException: Remote rejected the push"))
    }

    @Test fun `a notice told gets an id and the time, unless it brings its own`() = runBlocking {
        val holder = ICodeStateHolder()
        val first = async(start = CoroutineStart.UNDISPATCHED) { holder.effects.first() }
        holder.tell(Notice(NoticeTone.Info, NoticeSource.Git, "Fetched origin"))
        val stamped = (first.await() as ICodeEffect.Told).notice
        assertTrue(stamped.id.isNotEmpty() && stamped.at > 0)
        val second = async(start = CoroutineStart.UNDISPATCHED) { holder.effects.first() }
        holder.tell(stamped.copy(id = "web:1", at = 7))
        assertEquals("web:1" to 7L, (second.await() as ICodeEffect.Told).notice.let { it.id to it.at })
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
