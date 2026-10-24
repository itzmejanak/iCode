package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.GpgAnswer
import dev.icode.mobile.domain.GpgPrompt
import dev.icode.mobile.domain.GpgRequest
import dev.icode.mobile.domain.GpgRequests
import java.io.BufferedReader
import java.io.File
import java.io.Writer
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs the script iCode installs as gpg-agent's pinentry and gpg's agent program with a real POSIX
 * shell, as [GpgBridge] answers it: gpg-agent's side of the Assuan protocol is written here, and
 * iCode's answers go into the request's pipe.
 */
class GuestGpgTest {
    private lateinit var root: File
    private lateinit var requests: File
    private val started = mutableListOf<Process>()

    @Before fun install() {
        assumeTrue("needs a POSIX shell and mkfifo", File(SH).canExecute() && listOf("/usr/bin/mkfifo", "/bin/mkfifo").any { File(it).canExecute() })
        root = Files.createTempDirectory("guest-gpg").toFile()
        requests = File(root, "requests")
        listOf(PINENTRY, AGENT).forEach { name -> File(root, name).writeText(GuestGpg.script(requests.path)) }
    }

    @After fun stop() {
        started.forEach(Process::destroyForcibly)
        if (::root.isInitialized) root.deleteRecursively()
    }

    /** The pinentry as gpg-agent starts it, past its greeting. */
    private inner class Pinentry {
        val process: Process = ProcessBuilder(SH, File(root, PINENTRY).path).start().also { started += it }
        private val replies: BufferedReader = process.inputStream.bufferedReader()
        private val commands: Writer = process.outputStream.bufferedWriter()
        val id = process.id()

        init {
            assertEquals("OK Pleased to meet you", replies.readLine())
        }

        fun send(line: String) = commands.run { write(line + "\n"); flush() }

        fun reply(): String = replies.readLine()

        /** [line] and its one-line reply. */
        fun ask(line: String): String = send(line).let { reply() }
    }

    private fun request(id: String): String {
        val file = File(requests, "$id${GuestGpg.REQUEST}")
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (!file.exists()) {
            check(System.currentTimeMillis() < deadline) { "No request from $id" }
            Thread.sleep(POLL_MILLIS)
        }
        return file.readText()
    }

    /** What iCode writes into the request's pipe. */
    private fun answer(id: String, line: String) = File(requests, "$id${GuestGpg.ANSWER}").writeText(line + "\n")

    private fun gone(id: String): Boolean = listOf(GuestGpg.REQUEST, GuestGpg.ANSWER).none { File(requests, "$id$it").exists() }

    @Test fun `install writes the script under both names and the system's gpg settings naming them`() {
        val rootfs = File(root, "rootfs")
        GuestGpg.install(rootfs)
        listOf("usr/local/bin/icode-pinentry", "usr/local/bin/icode-gpg-agent").forEach { path ->
            val file = File(rootfs, path)
            assertTrue(file.canExecute())
            assertEquals(GuestGpg.script("/${GuestGpg.REQUESTS}"), file.readText())
        }
        val agentConf = File(rootfs, "etc/gnupg/gpg-agent.conf").readLines().filterNot { it.startsWith("#") }
        assertEquals(listOf("pinentry-program /usr/local/bin/icode-pinentry", "default-cache-ttl 1800"), agentConf)
        assertEquals(listOf("agent-program /usr/local/bin/icode-gpg-agent"), File(rootfs, "etc/gnupg/gpg.conf").readLines().filterNot { it.startsWith("#") })
        // Each says it is iCode's: the first line, or after the script's #!.
        listOf("usr/local/bin/icode-pinentry", "etc/gnupg/gpg.conf", "etc/gnupg/gpg-agent.conf").forEach { path ->
            assertTrue(path, File(rootfs, path).readLines().take(2).any { it.startsWith("# ${RuntimeLayout.MANAGED} ") })
        }
    }

    @Test fun `a passphrase is asked of iCode in gpg-agent's words and passed on, and nothing is left behind`() {
        val pinentry = Pinentry()
        assertEquals("OK", pinentry.ask("OPTION ttyname=/dev/pts/0"))
        assertEquals("OK", pinentry.ask("SETDESC Please enter the passphrase to unlock the OpenPGP secret key:%0A%22T <t@x>%22"))
        assertEquals("OK", pinentry.ask("SETPROMPT Passphrase:"))
        pinentry.send("GETPIN")
        val prompt = (GpgRequests.parse(pinentry.id, request(pinentry.id)) as GpgRequest.Ask).prompt
        assertEquals(
            GpgPrompt(pinentry.id, true, null, "Please enter the passphrase to unlock the OpenPGP secret key:\n\"T <t@x>\"", "Passphrase:", null, null, null),
            prompt,
        )
        answer(pinentry.id, "D s3cret%25pass")
        assertEquals("D s3cret%25pass", pinentry.reply())
        assertEquals("OK", pinentry.reply())
        assertTrue(gone(pinentry.id))
        assertEquals("OK", pinentry.ask("BYE"))
        assertTrue(pinentry.process.waitFor(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        assertEquals(0, pinentry.process.exitValue())
    }

    @Test fun `Cancel is pinentry's cancel, and an error is said with the next question only`() {
        val pinentry = Pinentry()
        pinentry.ask("SETDESC Unlock")
        pinentry.ask("SETERROR Bad Passphrase (try 2 of 3)")
        pinentry.send("GETPIN")
        assertTrue(request(pinentry.id).lines().contains("SETERROR Bad Passphrase (try 2 of 3)"))
        answer(pinentry.id, GpgRequests.answerLine(GpgAnswer.Cancel))
        assertEquals("ERR 83886179 Operation cancelled <Pinentry>", pinentry.reply())
        pinentry.send("GETPIN")
        assertFalse(request(pinentry.id).contains("SETERROR"))
        answer(pinentry.id, "OK")
        // An empty passphrase: no data line, as pinentry sends it.
        assertEquals("OK", pinentry.reply())
    }

    @Test fun `a confirmation keeps its buttons, and SETNOTOK is unknown so gpg-agent uses SETCANCEL`() {
        val pinentry = Pinentry()
        assertEquals("ERR 536871187 Unknown IPC command <User defined source 1>", pinentry.ask("SETNOTOK Enter new passphrase"))
        pinentry.ask("SETOK Yes, protection is not needed")
        pinentry.ask("SETCANCEL Enter new passphrase")
        pinentry.send("CONFIRM")
        val prompt = (GpgRequests.parse(pinentry.id, request(pinentry.id)) as GpgRequest.Ask).prompt
        assertFalse(prompt.asksPassphrase)
        assertEquals("Yes, protection is not needed" to "Enter new passphrase", prompt.ok to prompt.cancel)
        answer(pinentry.id, "OK")
        assertEquals("OK", pinentry.reply())
        pinentry.send("GETINFO pid")
        assertEquals("D ${pinentry.id}", pinentry.reply())
        assertEquals("OK", pinentry.reply())
    }

    @Test fun `gpg-agent taking the question back ends the pinentry, and its request goes with it`() {
        val pinentry = Pinentry()
        pinentry.send("GETPIN")
        request(pinentry.id)
        pinentry.process.destroy()
        assertTrue(pinentry.process.waitFor(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        assertTrue(gone(pinentry.id))
    }

    @Test fun `as gpg's agent program it asks iCode for an agent, and exits 0 only when one answers`() {
        listOf("0" to 0, "2" to 1).forEach { (status, exit) ->
            val program = ProcessBuilder(SH, File(root, AGENT).path, "--homedir", "/root/.gnupg", "--use-standard-socket", "--daemon")
                .start().also { started += it }
            val id = program.id()
            assertEquals(GpgRequest.StartAgent("/root/.gnupg"), GpgRequests.parse(id, request(id)))
            answer(id, status)
            assertTrue(program.waitFor(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            assertEquals(exit, program.exitValue())
            assertTrue(gone(id))
        }
    }

    private companion object {
        const val SH = "/bin/sh"
        const val PINENTRY = "icode-pinentry"
        const val AGENT = "icode-gpg-agent"
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 10L
    }
}

/** Its PID: the tests run on a desktop JDK, but compile against Android's Process, which has no pid(). */
private fun Process.id(): String = Process::class.java.getMethod("pid").invoke(this).toString()
