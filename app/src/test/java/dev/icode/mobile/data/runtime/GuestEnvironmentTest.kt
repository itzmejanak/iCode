package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.log.BackendLog
import java.io.File
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GuestEnvironmentTest {
    @get:Rule val temp = TemporaryFolder()

    private val originalWriter = BackendLog.writer

    @Before fun quiet() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun restore() {
        BackendLog.writer = originalWriter
    }

    private fun env(vararg pairs: Pair<String, String>) = pairs.joinToString("\u0000") { "${it.first}=${it.second}" } + "\u0000"

    @Test fun `what follows the marker is the environment, less the reading shell's own bookkeeping`() {
        val output = "Welcome to your profile!\n\u0000icode-environment\u0000" + env(
            "PATH" to "/root/.nvm/versions/node/v22/bin:/usr/bin",
            "NVM_DIR" to "/root/.nvm",
            "MULTI" to "one\ntwo=three",
            "PWD" to "/root", "OLDPWD" to "/", "SHLVL" to "1", "_" to "/usr/bin/env", "HISTFILE" to "/dev/null",
            "HOME" to "/root", "LD_LIBRARY_PATH" to "/opt/lib", "LD_PRELOAD" to "/opt/lib/hook.so",
        )
        // The user's library path and preload stay theirs: PRoot's own never reach the guest to collide.
        assertEquals(
            mapOf(
                "PATH" to "/root/.nvm/versions/node/v22/bin:/usr/bin", "NVM_DIR" to "/root/.nvm", "MULTI" to "one\ntwo=three",
                "HOME" to "/root", "LD_LIBRARY_PATH" to "/opt/lib", "LD_PRELOAD" to "/opt/lib/hook.so",
            ),
            GuestEnvironment.parse(output),
        )
        assertNull(GuestEnvironment.parse("bash: exec zsh"))
    }

    @Test fun `a history file the user's profile sets is theirs, the reading's own is not`() {
        val output = "\u0000icode-environment\u0000" + env("HISTFILE" to "/root/.history")
        assertEquals(mapOf("HISTFILE" to "/root/.history"), GuestEnvironment.parse(output))
    }

    @Test fun `the profile is read by the user's login shell, as a login and interactive`() {
        assertEquals(
            "exec '/usr/bin/zsh' -l -i -c '/usr/bin/printf '\\''\\000icode-environment\\000'\\''; /usr/bin/env -0' </dev/null",
            GuestEnvironment.script("/usr/bin/zsh"),
        )
    }

    @Test fun `each context gets its variables, the profile's only where it reads one`() = runBlocking {
        val rootfs = temp.newFolder("rootfs")
        val environment = GuestEnvironment(rootfs, { "/bin/bash" }) { "\u0000icode-environment\u0000" + env("PATH" to "/root/.cargo/bin:/usr/bin") }
        assertEquals("/root/.cargo/bin:/usr/bin", environment.of(GuestContext.Git).variables["PATH"])
        assertEquals("git", environment.of(GuestContext.Git).variables[GuestVariables.CONTEXT])
        assertEquals(RuntimeLayout.GUEST_PATH, environment.of(GuestContext.Terminal).variables["PATH"])
        assertEquals(RuntimeLayout.GUEST_PATH, environment.of(GuestContext.Install).variables["PATH"])
    }

    @Test fun `it is read once, and again when a profile file changes`() = runBlocking {
        val rootfs = temp.newFolder("rootfs")
        val profile = File(rootfs, "root/.bashrc").apply { parentFile.mkdirs(); writeText("export A=1") }
        var reads = 0
        val environment = GuestEnvironment(rootfs, { "/bin/bash" }) { script ->
            assertEquals(GuestEnvironment.script("/bin/bash"), script)
            reads += 1
            "\u0000icode-environment\u0000" + env("A" to "$reads")
        }
        assertEquals(mapOf("A" to "1"), environment.current().variables)
        assertEquals(mapOf("A" to "1"), environment.current().variables)
        assertEquals(1, reads)
        profile.writeText("export A=22")
        assertEquals(mapOf("A" to "2"), environment.current().variables)
        File(rootfs, "etc/profile.d").mkdirs()
        File(rootfs, "etc/profile.d/nvm.sh").writeText("export B=1")
        environment.current()
        assertEquals(3, reads)
    }

    @Test fun `a profile that cannot be read leaves iCode's own environment, and says why`() = runBlocking {
        val rootfs = temp.newFolder("rootfs")
        assertEquals(
            GuestEnvironment.Login(emptyMap(), "your shell profile ended before it was read through (an `exec` in it?)"),
            GuestEnvironment(rootfs, { "/bin/bash" }) { "bash: no job control" }.current(),
        )
        assertEquals(
            GuestEnvironment.Login(emptyMap(), "your shell profile could not be run"),
            GuestEnvironment(rootfs, { "/bin/bash" }) { throw IOException("no runtime") }.current(),
        )
    }

    @Test(timeout = 5_000)
    fun `a profile that never finishes is given up when its time is out`() = runBlocking {
        val login = GuestEnvironment(temp.newFolder("rootfs"), { "/bin/bash" }, timeoutMillis = 1_000) { awaitCancellation() }.current()
        assertEquals("your shell profile did not finish in 1 s", login.failure)
    }
}
