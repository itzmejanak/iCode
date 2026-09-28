package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GitRemoteRulesTest {
    @Test fun `ordinary remote names pass`() {
        listOf("origin", "upstream", "my-fork", "team_1", "v2.mirror").forEach { assertNull(it, GitRemoteRules.nameError(it)) }
    }

    @Test fun `names Git or a shell would misread are refused`() {
        listOf("", " ", "-x", "a b", "a/b", "a..b", "x.lock", "a:b").forEach { assertNotNull(it, GitRemoteRules.nameError(it)) }
    }

    @Test fun `addresses Git accepts pass`() {
        listOf(
            "https://github.com/ada/app.git",
            "git@github.com:ada/app.git",
            "ssh://git@host.example:2222/ada/app.git",
            "/storage/emulated/0/backup/app.git",
        ).forEach { assertNull(it, GitRemoteRules.urlError(it)) }
    }

    @Test fun `a web address carrying an account or token is refused`() {
        assertEquals(
            "Leave the account out of the address; save a token in Git credentials instead",
            GitRemoteRules.urlError("https://ada:ghp_secret@github.com/ada/app.git"),
        )
        assertNotNull(GitRemoteRules.urlError("HTTPS://ghp_onlytoken@github.com/ada/app.git"))
        assertNotNull(GitRemoteRules.urlError("https://github.com/ada/my app.git"))
        assertNotNull(GitRemoteRules.urlError(""))
    }

    @Test fun `a host is a server name with an optional port`() {
        listOf("github.com", "git.example.com:8443", "localhost", "10.0.2.2").forEach { assertNull(it, GitRemoteRules.hostError(it)) }
        listOf("", "https://github.com", "github.com/ada", "ada@github.com", "git hub.com", "-x.com").forEach {
            assertNotNull(it, GitRemoteRules.hostError(it))
        }
    }
}
