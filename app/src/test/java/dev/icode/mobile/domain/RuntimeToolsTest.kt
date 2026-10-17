package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeToolsTest {
    private val git = RuntimeTools.Git
    private val ssh = RuntimeTools.SshClient
    private val curl = RuntimeTools.Curl
    private val python = RuntimeTools.Python

    @Test fun `each tool has its own id, and Standard is what iCode's features use`() {
        assertEquals(RuntimeTools.all.size, RuntimeTools.all.map { it.id }.toSet().size)
        assertEquals(setOf("git", "ssh-client", "ripgrep"), RuntimeTools.standard)
        assertTrue(RuntimeTools.all.filter { it.id in RuntimeTools.standard }.all { it.usedBy != null })
    }

    @Test fun `a tool is installed only when dpkg has every one of its packages`() {
        assertTrue(RuntimeToolRules.isInstalled(git, setOf("git", "libc6")))
        assertFalse(RuntimeToolRules.isInstalled(python, setOf("python3", "python3-pip")))
    }

    @Test fun `the page opens on Standard and on what is installed already`() {
        assertEquals(RuntimeTools.standard + "curl", RuntimeToolRules.initialChoice(setOf("curl", "ca-certificates")))
    }

    @Test fun `a plan installs what is chosen and missing, and removes what is unticked and installed`() {
        val installed = setOf("git", "ripgrep", "curl", "ca-certificates")
        val plan = RuntimeToolRules.plan(setOf("git", "ssh-client", "python"), installed)
        assertEquals(listOf(ssh, python), plan.install)
        assertEquals(listOf("openssh-client", "python3", "python3-pip", "python3-venv"), plan.installPackages)
        assertEquals(listOf(RuntimeTools.Ripgrep, curl), plan.remove)
        assertEquals(listOf("ripgrep", "curl", "ca-certificates"), plan.removePackages)
        assertTrue(RuntimeToolRules.plan(setOf("git", "ripgrep", "curl"), installed).isEmpty)
    }

    @Test fun `a package a kept tool also lists is not removed`() {
        val fetch = RuntimeTool("fetch", "Fetch", "", null, listOf("wget", "ca-certificates"))
        val plan = RuntimeToolRules.plan(setOf("fetch"), setOf("curl", "ca-certificates", "wget"), listOf(curl, fetch))
        assertEquals(listOf(curl), plan.remove)
        assertEquals(listOf("curl"), plan.removePackages)
    }

    @Test fun `the download counts each archive once and is unknown until every part is measured`() {
        val downloads = mapOf(
            "ssh-client" to RuntimeToolDownload(mapOf("openssh-client.deb" to 890_282L, "libedit2.deb" to 97_526L)),
            "python" to RuntimeToolDownload(mapOf("python3.deb" to 23_000L, "libedit2.deb" to 97_526L)),
        )
        assertEquals(890_282L + 97_526L + 23_000L, RuntimeToolRules.downloadBytes(listOf(ssh, python), downloads))
        assertNull(RuntimeToolRules.downloadBytes(listOf(ssh, git), downloads))
        assertEquals(0L, RuntimeToolRules.downloadBytes(emptyList(), downloads))
    }

    @Test fun `an install says what it installs, and may be cancelled only before dpkg runs`() {
        val work = RuntimeToolWork(RuntimeToolAction.Install, listOf(git, ssh))
        assertEquals("Installing Git, SSH client", work.title)
        assertTrue(work.cancellable)
        assertTrue(work.copy(progress = AptProgress(phase = AptPhase.Downloading)).cancellable)
        assertFalse(work.copy(progress = AptProgress(phase = AptPhase.Installing)).cancellable)
        assertFalse(work.copy(failure = "E: Unable to fetch").cancellable)
        assertTrue(work.includes(ssh))
        assertFalse(work.includes(python))
        assertEquals("Removing curl", RuntimeToolWork(RuntimeToolAction.Remove, listOf(curl)).title)
    }

    @Test fun `a button waiting on a tool sees its install, not a removal or a failure`() {
        val installing = RuntimeToolWork(
            RuntimeToolAction.Install, listOf(git, ssh), AptProgress(phase = AptPhase.Installing, total = 6, done = 3),
        )
        val state = RuntimeToolsState(work = installing)
        assertEquals("Installing Git, SSH client · Setting up 3 of 6", state.installing(ssh)?.status)
        assertNull(state.installing(python))
        assertNull(state.copy(work = installing.copy(failure = "E: no")).installing(ssh))
        assertNull(state.copy(work = RuntimeToolWork(RuntimeToolAction.Remove, listOf(ssh))).installing(ssh))
    }

    @Test fun `Set Up Tools asks the install type first and lists the tools only for Custom, Runtime Tools lists then confirms`() {
        assertEquals(listOf(RuntimeToolStep.InstallType, RuntimeToolStep.Verify), RuntimeToolRules.steps(setup = true, custom = false))
        assertEquals(
            listOf(RuntimeToolStep.InstallType, RuntimeToolStep.Select, RuntimeToolStep.Verify),
            RuntimeToolRules.steps(setup = true, custom = true),
        )
        assertEquals(listOf(RuntimeToolStep.Select, RuntimeToolStep.Verify), RuntimeToolRules.steps(setup = false, custom = true))
    }

    @Test fun `a row says Installed, Will be removed, its download, or that it is being measured`() {
        val state = RuntimeToolsState(
            installedPackages = setOf("git"),
            downloads = mapOf("ssh-client" to RuntimeToolDownload(mapOf("a.deb" to 1_000L, "b.deb" to 500L))),
        )
        assertEquals(RuntimeToolStatus.Installed, RuntimeToolRules.status(git, ticked = true, state))
        assertEquals(RuntimeToolStatus.WillBeRemoved, RuntimeToolRules.status(git, ticked = false, state))
        assertEquals(RuntimeToolStatus.Download(1_500L), RuntimeToolRules.status(ssh, ticked = true, state))
        assertEquals(RuntimeToolStatus.SizeUnknown, RuntimeToolRules.status(curl, ticked = false, state))
        assertEquals(RuntimeToolStatus.Measuring, RuntimeToolRules.status(curl, ticked = false, state.copy(measuring = true)))
    }

    @Test fun `the foot says what the change costs and the space there is`() {
        val installed = setOf("git", "curl", "ca-certificates")
        val state = RuntimeToolsState(
            installedPackages = installed,
            downloads = mapOf("ssh-client" to RuntimeToolDownload(mapOf("ssh.deb" to 2_048L))),
        )
        val plan = RuntimeToolRules.plan(setOf("git", "ssh-client"), installed)
        assertEquals("Download 2.0 KB · Remove 1 tool · 1.0 KB available", RuntimeToolRules.summary(plan, state, 1_024L))
        assertEquals("Nothing to change", RuntimeToolRules.summary(RuntimeToolRules.plan(setOf("git", "curl"), installed), state, null))
        assertEquals("Reading what is installed…", RuntimeToolRules.summary(null, state, null))
        val unmeasured = RuntimeToolRules.plan(setOf("git", "curl", "python"), installed)
        assertEquals("Download size unknown", RuntimeToolRules.summary(unmeasured, state, null))
        assertEquals("Measuring the download…", RuntimeToolRules.summary(unmeasured, state.copy(measuring = true), null))
    }
}
