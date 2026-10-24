package dev.icode.mobile.data.runtime

import com.termux.terminal.TextStyle
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ShellPromptTest {
    private val rootfs = Files.createTempDirectory("prompt").toFile()
    private val home = File(rootfs, "root").apply { mkdirs() }
    private val script = File(rootfs, "etc/icode/prompt.sh")

    @After fun clean() {
        rootfs.deleteRecursively()
    }

    @Test fun `terminals starting again and again write iCode's script, and never a dotfile`() {
        val bashrc = File(home, ".bashrc").apply { writeText("alias ll='ls -l'\neval \"\$(starship init bash)\"\n") }.readBytes()
        val profile = File(home, ".profile").apply { writeText("PATH=\"\$HOME/bin:\$PATH\"\n") }.readBytes()
        repeat(3) { ShellPrompt.write(rootfs) }
        assertEquals(ShellPrompt.SCRIPT, script.readText())
        assertArrayEquals(bashrc, File(home, ".bashrc").readBytes())
        assertArrayEquals(profile, File(home, ".profile").readBytes())
        assertEquals(listOf(".bashrc", ".profile"), home.list()!!.sorted())
    }

    @Test fun `a home iCode creates ends its bashrc with the one line that sources the script`() {
        File(home, ".bashrc").writeText("# ~/.bashrc: executed by bash(1) for non-login shells.\n")
        ShellPrompt.addTo(home)
        val lines = File(home, ".bashrc").readLines()
        assertEquals(1, lines.count { "/etc/icode/prompt.sh" in it })
        assertEquals(". /etc/icode/prompt.sh  # Managed by iCode: its prompt — delete this line to use your own", lines.last())
        assertTrue(File(home, ".bashrc").readText().startsWith("# ~/.bashrc: executed by bash(1)"))
    }

    @Test fun `the prompt's colour is the terminal's accent slot`() {
        assertEquals(TextStyle.COLOR_INDEX_CURSOR, ShellPrompt.ACCENT_COLOR_INDEX)
    }

    @Test fun `bash shows the project, a subdirectory, or the home as ~`() {
        assumeTrue(File("/bin/bash").canExecute())
        ShellPrompt.write(rootfs)
        val accent = "\\[\\e[38;5;258m\\]"
        assertEquals("$accent" + "demo\\[\\e[0m\\] \\[\\e[32m\\]$\\[\\e[0m\\] ", ps1("/workspace/demo"))
        assertTrue(ps1("/workspace/demo/src").startsWith("$accent" + "demo/src\\["))
        assertTrue(ps1("/workspace").startsWith("$accent/workspace\\["))
        assertTrue(ps1(home.path).startsWith("$accent~\\["))
        assertTrue(ps1("${home.path}/notes").startsWith("$accent~/notes\\["))
        assertTrue(ps1("${home.path}x").startsWith("$accent${home.path}x\\["))
    }

    @Test fun `a fresh home shows iCode's prompt, and a prompt set after its line wins, as Starship's does`() {
        assumeTrue(File("/bin/bash").canExecute())
        ShellPrompt.write(rootfs)
        File(home, ".bashrc").writeText("")
        ShellPrompt.addTo(home)
        assertEquals("__icode_prompt", bash(File(home, ".bashrc").readText() + "printf %s \"\$PROMPT_COMMAND\""))
        File(home, ".bashrc").appendText("PROMPT_COMMAND=starship_precmd\n")
        assertEquals("starship_precmd", bash(File(home, ".bashrc").readText() + "printf %s \"\$PROMPT_COMMAND\""))
    }

    /** PS1 as bash sets it before drawing a prompt in [pwd]. */
    private fun ps1(pwd: String): String = bash(". \"${ShellPrompt.SCRIPT_PATH}\"; PWD='$pwd'; \$PROMPT_COMMAND; printf %s \"\$PS1\"")

    /** [command] in bash with no startup files, [ShellPrompt.SCRIPT_PATH] read from this test's tree. */
    private fun bash(command: String): String {
        val process = ProcessBuilder("/bin/bash", "--norc", "--noprofile", "-c", command.replace(ShellPrompt.SCRIPT_PATH, script.path))
            .apply { environment()["HOME"] = home.path }.redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        return output
    }
}
