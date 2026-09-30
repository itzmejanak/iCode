package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeComponentsTest {
    private val git = RuntimeComponents.Git
    private val ssh = RuntimeComponents.SshClient
    private val curl = RuntimeComponents.Curl
    private val python = RuntimeComponents.Python

    @Test fun `each component has its own id, and Standard is what iCode's features use`() {
        assertEquals(RuntimeComponents.all.size, RuntimeComponents.all.map { it.id }.toSet().size)
        assertEquals(setOf("git", "ssh-client", "ripgrep"), RuntimeComponents.standard)
        assertTrue(RuntimeComponents.all.filter { it.id in RuntimeComponents.standard }.all { it.usedBy != null })
    }

    @Test fun `a component is installed only when dpkg has every one of its packages`() {
        assertTrue(ComponentRules.isInstalled(git, setOf("git", "libc6")))
        assertFalse(ComponentRules.isInstalled(python, setOf("python3", "python3-pip")))
    }

    @Test fun `the page opens on Standard and on what is installed already`() {
        assertEquals(RuntimeComponents.standard + "curl", ComponentRules.initialChoice(setOf("curl", "ca-certificates")))
    }

    @Test fun `a plan installs what is chosen and missing, and removes what is unticked and installed`() {
        val installed = setOf("git", "ripgrep", "curl", "ca-certificates")
        val plan = ComponentRules.plan(setOf("git", "ssh-client", "python"), installed)
        assertEquals(listOf(ssh, python), plan.install)
        assertEquals(listOf("openssh-client", "python3", "python3-pip", "python3-venv"), plan.installPackages)
        assertEquals(listOf(RuntimeComponents.Ripgrep, curl), plan.remove)
        assertEquals(listOf("ripgrep", "curl", "ca-certificates"), plan.removePackages)
        assertTrue(ComponentRules.plan(setOf("git", "ripgrep", "curl"), installed).isEmpty)
    }

    @Test fun `a package a kept component also lists is not removed`() {
        val fetch = RuntimeComponent("fetch", "Fetch", "", null, listOf("wget", "ca-certificates"))
        val plan = ComponentRules.plan(setOf("fetch"), setOf("curl", "ca-certificates", "wget"), listOf(curl, fetch))
        assertEquals(listOf(curl), plan.remove)
        assertEquals(listOf("curl"), plan.removePackages)
    }

    @Test fun `the download counts each archive once and is unknown until every part is measured`() {
        val downloads = mapOf(
            "ssh-client" to ComponentDownload(mapOf("openssh-client.deb" to 890_282L, "libedit2.deb" to 97_526L)),
            "python" to ComponentDownload(mapOf("python3.deb" to 23_000L, "libedit2.deb" to 97_526L)),
        )
        assertEquals(890_282L + 97_526L + 23_000L, ComponentRules.downloadBytes(listOf(ssh, python), downloads))
        assertNull(ComponentRules.downloadBytes(listOf(ssh, git), downloads))
        assertEquals(0L, ComponentRules.downloadBytes(emptyList(), downloads))
    }

    @Test fun `an install says what it installs, and may be cancelled only before dpkg runs`() {
        val work = ComponentWork(ComponentAction.Install, listOf(git, ssh))
        assertEquals("Installing Git, SSH client", work.title)
        assertTrue(work.cancellable)
        assertTrue(work.copy(progress = AptProgress(phase = AptPhase.Downloading)).cancellable)
        assertFalse(work.copy(progress = AptProgress(phase = AptPhase.Installing)).cancellable)
        assertFalse(work.copy(failure = "E: Unable to fetch").cancellable)
        assertTrue(work.includes(ssh))
        assertFalse(work.includes(python))
        assertEquals("Removing curl", ComponentWork(ComponentAction.Remove, listOf(curl)).title)
    }
}
