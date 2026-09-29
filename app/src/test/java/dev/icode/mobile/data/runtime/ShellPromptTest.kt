package dev.icode.mobile.data.runtime

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ShellPromptTest {
    private val rootfs = Files.createTempDirectory("prompt").toFile()
    private val home = File(rootfs, "root")
    private var installed = true
    private val prompt = ShellPrompt(rootfs) { installed }

    @After fun clean() {
        rootfs.deleteRecursively()
    }

    @Test fun `the script is sourced once however often a shell starts`() {
        File(home, ".bashrc").apply { parentFile.mkdirs() }.writeText("alias ll='ls -l'\n")
        prompt.install()
        prompt.install()
        assertEquals(ShellPrompt.SCRIPT, File(home, ".icode-prompt.sh").readText())
        listOf(".bashrc", ".profile").forEach { name ->
            assertEquals(1, File(home, name).readLines().count { it == ". /root/.icode-prompt.sh" })
        }
        assertTrue(File(home, ".bashrc").readText().startsWith("alias ll='ls -l'\n"))
    }

    @Test fun `the accent is written as red, green and blue, and follows each change`() {
        home.mkdirs()
        prompt.useAccent(0xFFFF6B2C.toInt())
        assertEquals("255;107;44\n", File(home, ".icode-accent").readText())
        prompt.useAccent(0xFFB67CFF.toInt())
        assertEquals("182;124;255\n", File(home, ".icode-accent").readText())
    }

    @Test fun `a runtime not installed yet gets the accent with its first shell`() {
        installed = false
        prompt.useAccent(0xFF4FB3FF.toInt())
        assertFalse(File(home, ".icode-accent").exists())
        installed = true
        prompt.install()
        assertEquals("79;179;255\n", File(home, ".icode-accent").readText())
    }

    @Test fun `bash draws the project in the accent, and the old orange without a valid one`() {
        assumeTrue(File("/bin/bash").canExecute())
        home.mkdirs()
        prompt.install()
        assertEquals("\\[\\e[38;5;208m\\]demo/src\\[\\e[0m\\] \\[\\e[32m\\]$\\[\\e[0m\\] ", ps1("/workspace/demo/src"))
        prompt.useAccent(0xFFB67CFF.toInt())
        assertEquals("\\[\\e[38;2;182;124;255m\\]demo\\[\\e[0m\\] \\[\\e[32m\\]$\\[\\e[0m\\] ", ps1("/workspace/demo"))
        assertTrue(ps1("${home.path}/notes").startsWith("\\[\\e[38;2;182;124;255m\\]~/notes\\["))
        assertTrue(ps1(home.path).startsWith("\\[\\e[38;2;182;124;255m\\]~\\["))
        assertTrue(ps1("${home.path}x").startsWith("\\[\\e[38;2;182;124;255m\\]${home.path}x\\["))
        assertTrue(ps1("/workspace").startsWith("\\[\\e[38;2;182;124;255m\\]/workspace\\["))
        File(home, ".icode-accent").writeText("1;2;3m\u001b]0;title\u0007\n")
        assertTrue(ps1("/workspace/demo").startsWith("\\[\\e[38;5;208m\\]demo"))
    }

    /** PS1 as bash sets it before drawing a prompt in [pwd]. */
    private fun ps1(pwd: String): String {
        val process = ProcessBuilder(
            "/bin/bash", "--norc", "--noprofile", "-c",
            ". \"\$HOME/.icode-prompt.sh\"; PWD='$pwd'; \$PROMPT_COMMAND; printf %s \"\$PS1\"",
        ).apply { environment()["HOME"] = home.path }.redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        return output
    }
}
