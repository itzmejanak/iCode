package dev.icode.mobile.data.log

import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackendLogTest {
    private val lines = mutableListOf<Triple<Int, String, String>>()
    private val original = BackendLog.writer

    @Before fun capture() {
        BackendLog.writer = { priority, tag, message -> lines += Triple(priority, tag, message) }
    }

    @After fun restore() {
        BackendLog.writer = original
    }

    @Test fun `a token in a remote URL never reaches the log`() {
        assertEquals(
            "fatal: unable to access 'https://***@github.com/me/repo.git/'",
            BackendLog.credentialSafe("fatal: unable to access 'https://me:ghp_secret123@github.com/me/repo.git/'"),
        )
        assertEquals("https://***@host/x", BackendLog.credentialSafe("https://ghp_onlytoken@host/x"))
    }

    @Test fun `addresses without a secret are left alone`() {
        val text = "git@github.com:me/repo.git and https://github.com/me/repo.git"
        assertEquals(text, BackendLog.credentialSafe(text))
    }

    @Test fun `a warning carries the subsystem tag and the error, both redacted`() {
        BackendLog.w("git", "Push failed", IOException("https://me:tok@host/r.git: denied"))
        val (priority, tag, message) = lines.single()
        assertEquals(android.util.Log.WARN, priority)
        assertEquals("iCode/git", tag)
        assertTrue(message.startsWith("Push failed\njava.io.IOException: https://***@host/r.git: denied"))
        assertFalse(message.contains("tok@"))
    }
}
