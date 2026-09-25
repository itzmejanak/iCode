package dev.icode.mobile.ui.screens.entry

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The destination shown before a clone must match the directory Git actually
 * creates, or the workspace opened afterwards points at nothing.
 */
class CloneDirectoryNameTest {

    @Test
    fun `https url drops the git suffix`() {
        assertEquals("repo", directoryNameFor("https://github.com/owner/repo.git"))
    }

    @Test
    fun `https url without a suffix keeps the last segment`() {
        assertEquals("repo", directoryNameFor("https://github.com/owner/repo"))
    }

    @Test
    fun `scp style ssh url is handled`() {
        assertEquals("icodeTest", directoryNameFor("git@github.com:itzmejanak/icodeTest.git"))
    }

    @Test
    fun `ssh protocol url is handled`() {
        assertEquals("repo", directoryNameFor("ssh://git@host.example/owner/repo.git"))
    }

    @Test
    fun `a trailing slash does not produce an empty name`() {
        assertEquals("repo", directoryNameFor("https://github.com/owner/repo/"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("repo", directoryNameFor("  https://github.com/owner/repo.git  "))
    }

    @Test
    fun `a single segment ssh path still resolves`() {
        assertEquals("repo", directoryNameFor("git@host:repo.git"))
    }

    @Test
    fun `an empty url yields an empty name`() {
        assertEquals("", directoryNameFor(""))
        assertEquals("", directoryNameFor("   "))
    }

    @Test
    fun `a name that merely contains git is not truncated`() {
        assertEquals("gitignore-tools", directoryNameFor("https://host/owner/gitignore-tools"))
    }
}
