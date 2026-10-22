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
        assertTrue(RuntimeToolRules.isInstalled(git, RuntimeInventory(setOf("git", "libc6"))))
        assertFalse(RuntimeToolRules.isInstalled(python, RuntimeInventory(setOf("python3", "python3-pip"))))
    }

    @Test fun `a tool with a setup is installed only when its receipt matches too`() {
        val sdk = RuntimeTools.AndroidSdk
        assertFalse(RuntimeToolRules.isInstalled(sdk, RuntimeInventory(setOf("openjdk-17-jdk-headless"))))
        assertFalse("the JDK went with a Repair", RuntimeToolRules.isInstalled(sdk, RuntimeInventory(emptySet(), setOf("android-sdk"))))
        assertTrue(RuntimeToolRules.isInstalled(sdk, RuntimeInventory(setOf("openjdk-17-jdk-headless"), setOf("android-sdk"))))
    }

    @Test fun `the Android SDK is opt-in, used by Build & Run, and asks for Google's licence`() {
        val sdk = RuntimeTools.AndroidSdk
        assertFalse(sdk.id in RuntimeTools.standard)
        assertEquals("android-sdk", sdk.setup)
        assertEquals("android-sdk-license", sdk.licence)
        assertEquals(listOf("openjdk-17-jdk-headless"), sdk.packages)
    }

    @Test fun `CMake and the NDKs are opt-in rows after the Android SDK, under its licence, each after what it requires`() {
        val ids = RuntimeTools.all.map { it.id }
        assertEquals(listOf("android-sdk", "cmake", "android-ndk-28", "android-ndk-27"), ids.subList(ids.indexOf("android-sdk"), ids.indexOf("android-sdk") + 4))
        RuntimeTools.all.forEach { tool -> tool.requires.forEach { assertTrue("${tool.id} lists $it after it", ids.indexOf(it) in 0 until ids.indexOf(tool.id)) } }
        listOf(RuntimeTools.Cmake, RuntimeTools.Ndk28, RuntimeTools.Ndk27).forEach { tool ->
            assertFalse(tool.id in RuntimeTools.standard)
            assertEquals("android-sdk-license", tool.licence)
            assertEquals(tool.id, tool.setup)
        }
        assertEquals(emptyList<String>(), RuntimeTools.Cmake.packages)
        // The runtime unpacks an NDK's tar.xz with its own xz, outside the app's memory.
        assertEquals(listOf("xz-utils"), RuntimeTools.Ndk28.packages)
        assertEquals("NDK (Side by side) 28.2.13676358", RuntimeTools.Ndk28.name)
        assertEquals(listOf("cmake", "android-sdk"), RuntimeTools.Ndk27.requires)
    }

    @Test fun `ticking a tool ticks what it requires, and unticking one unticks what requires it`() {
        val ticked = RuntimeToolRules.toggle(setOf("git"), "android-ndk-27")
        assertEquals(setOf("git", "android-ndk-27", "cmake", "android-sdk"), ticked)
        val both = RuntimeToolRules.toggle(ticked, "android-ndk-28")
        assertEquals(setOf("git", "android-ndk-27", "cmake", "android-sdk", "android-ndk-28"), both)
        // An NDK unticked leaves what it required: other tools may need it.
        assertEquals(setOf("git", "cmake", "android-sdk", "android-ndk-28"), RuntimeToolRules.toggle(both, "android-ndk-27"))
        assertEquals(setOf("git", "android-sdk"), RuntimeToolRules.toggle(both, "cmake"))
        assertEquals(setOf("git"), RuntimeToolRules.toggle(both, "android-sdk"))
    }

    @Test fun `a plan reads the choice with its requirements, and installs in the catalog's order`() {
        val jdk = RuntimeInventory(setOf("git"))
        val plan = RuntimeToolRules.plan(setOf("git", "android-ndk-28"), jdk)
        assertEquals(listOf(RuntimeTools.AndroidSdk, RuntimeTools.Cmake, RuntimeTools.Ndk28), plan.install)
        assertEquals(listOf("android-sdk-license"), plan.licences)
        val all = RuntimeInventory(setOf("git", "openjdk-17-jdk-headless", "xz-utils"), setOf("android-sdk", "cmake", "android-ndk-28"))
        assertEquals(listOf(RuntimeTools.AndroidSdk, RuntimeTools.Cmake, RuntimeTools.Ndk28), RuntimeToolRules.plan(setOf("git"), all).remove)
    }

    @Test fun `the page opens on Standard and on what is installed already`() {
        assertEquals(RuntimeTools.standard + "curl", RuntimeToolRules.initialChoice(RuntimeInventory(setOf("curl", "ca-certificates"))))
    }

    @Test fun `a plan installs what is chosen and missing, and removes what is unticked and installed`() {
        val installed = RuntimeInventory(setOf("git", "ripgrep", "curl", "ca-certificates"))
        val plan = RuntimeToolRules.plan(setOf("git", "ssh-client", "python"), installed)
        assertEquals(listOf(ssh, python), plan.install)
        assertEquals(listOf("openssh-client", "python3", "python3-pip", "python3-venv"), plan.installPackages)
        assertEquals(listOf(RuntimeTools.Ripgrep, curl), plan.remove)
        assertEquals(listOf("ripgrep", "curl", "ca-certificates"), plan.removePackages)
        assertTrue(RuntimeToolRules.plan(setOf("git", "ripgrep", "curl"), installed).isEmpty)
    }

    @Test fun `a package a kept tool also lists is not removed`() {
        val fetch = RuntimeTool("fetch", "Fetch", "", null, listOf("wget", "ca-certificates"))
        val plan = RuntimeToolRules.plan(setOf("fetch"), RuntimeInventory(setOf("curl", "ca-certificates", "wget")), listOf(curl, fetch))
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
        assertEquals(
            listOf(RuntimeToolStep.InstallType, RuntimeToolStep.Verify),
            RuntimeToolRules.steps(setup = true, custom = false, licences = false),
        )
        assertEquals(
            listOf(RuntimeToolStep.InstallType, RuntimeToolStep.Select, RuntimeToolStep.Verify),
            RuntimeToolRules.steps(setup = true, custom = true, licences = false),
        )
        assertEquals(
            listOf(RuntimeToolStep.Select, RuntimeToolStep.Verify),
            RuntimeToolRules.steps(setup = false, custom = true, licences = false),
        )
    }

    @Test fun `installing a tool with a licence ends on the License Agreement`() {
        val plan = RuntimeToolRules.plan(setOf("git", "android-sdk"), RuntimeInventory(setOf("git")))
        assertEquals(listOf("android-sdk-license"), plan.licences)
        assertEquals(emptyList<String>(), RuntimeToolRules.plan(setOf("python"), RuntimeInventory(emptySet())).licences)
        assertEquals(
            listOf(RuntimeToolStep.Select, RuntimeToolStep.Verify, RuntimeToolStep.Licence),
            RuntimeToolRules.steps(setup = false, custom = true, licences = true),
        )
    }

    @Test fun `once apt is done the run says its setup's step, share and file, and may be cancelled where the setup says`() {
        val apt = RuntimeToolWork(RuntimeToolAction.Install, listOf(RuntimeTools.AndroidSdk), AptProgress(phase = AptPhase.Installing, total = 4, done = 4))
        assertEquals("4 / 4", apt.counts)
        val setup = apt.copy(setup = ToolSetupProgress("Downloading Android SDK Command-line Tools", 0.25f, "45 of 181 MB", cancellable = true))
        assertEquals("Downloading Android SDK Command-line Tools", setup.step)
        assertEquals(0.25f, setup.fraction)
        assertEquals("45 of 181 MB", setup.item)
        assertNull(setup.counts)
        assertTrue(setup.cancellable)
        assertFalse(setup.copy(setup = ToolSetupProgress("Setting up build tools for ARM64", cancellable = false)).cancellable)
        assertEquals("Installing Android SDK · Downloading Android SDK Command-line Tools", setup.status)
    }

    @Test fun `a row says Installed, Will be removed, its download, or that it is being measured`() {
        val state = RuntimeToolsState(
            installed = RuntimeInventory(setOf("git")),
            downloads = mapOf("ssh-client" to RuntimeToolDownload(mapOf("a.deb" to 1_000L, "b.deb" to 500L))),
        )
        assertEquals(RuntimeToolStatus.Installed, RuntimeToolRules.status(git, ticked = true, state))
        assertEquals(RuntimeToolStatus.WillBeRemoved, RuntimeToolRules.status(git, ticked = false, state))
        assertEquals(RuntimeToolStatus.Download(1_500L), RuntimeToolRules.status(ssh, ticked = true, state))
        assertEquals(RuntimeToolStatus.SizeUnknown, RuntimeToolRules.status(curl, ticked = false, state))
        assertEquals(RuntimeToolStatus.Measuring, RuntimeToolRules.status(curl, ticked = false, state.copy(measuring = true)))
    }

    @Test fun `the foot says what the change costs and the space there is`() {
        val installed = RuntimeInventory(setOf("git", "curl", "ca-certificates"))
        val state = RuntimeToolsState(
            installed = installed,
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
