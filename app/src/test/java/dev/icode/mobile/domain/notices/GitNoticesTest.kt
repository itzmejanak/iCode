package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.GitTransferLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitNoticesTest {
    private val target = GitNotices.Target(branch = "main", upstream = "origin/main", remote = "origin")

    @Test fun `an address is said as people say it, without its scheme, account or git`() {
        assertEquals("github.com/acme/superpowers", GitNotices.addressOf("https://ada:ghp_x@github.com/acme/superpowers.git"))
        assertEquals("host/me/repo", GitNotices.addressOf("git@host:me/repo.git"))
        assertEquals("github.com:443/acme/app", GitNotices.addressOf("https://github.com:443/acme/app"))
    }

    @Test fun `a refused sign-in is said, not a progress count that happens to read 403`() {
        val refused = GitNotices.transferFailed(GitTransferLabel.FETCHING, "fatal", listOf("fatal: The requested URL returned error: 403"), target, null)
        assertEquals("Git couldn't sign in to origin. Check the token in Git credentials.", refused.detail)
        val counting = GitNotices.transferFailed(GitTransferLabel.FETCHING, "fatal: early EOF", listOf("Receiving objects:  40% (403/1000)"), target, null)
        assertNull(counting.detail)
    }

    @Test fun `what Git printed is the output, or its reason when it printed nothing`() {
        assertEquals("fatal: early EOF", GitNotices.transferFailed(GitTransferLabel.PULLING, "fatal: early EOF", emptyList(), target, null).output)
    }

    @Test fun `a transfer cut short names the branch it pulled into, when it was kept`() {
        val cut = GitNotices.cutShort(GitTransferLabel.PULLING, "/workspace/phone", "main", "origin/main")
        assertEquals("The pull into main didn't finish", cut.title)
        assertEquals("phone · main → origin/main", cut.place?.line)
        assertEquals("The pull didn't finish", GitNotices.cutShort(GitTransferLabel.PULLING, "/workspace/phone").title)
    }
}
