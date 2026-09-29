package dev.icode.mobile.data.runtime

import com.termux.terminal.TextStyle
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ShellPromptTest {
    private val rootfs = Files.createTempDirectory("prompt").toFile()
    private val home = File(rootfs, "root")
    private val prompt = ShellPrompt(rootfs)

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

    @Test fun `the prompt's colour is the terminal's accent slot`() {
        assertEquals(TextStyle.COLOR_INDEX_CURSOR, ShellPrompt.ACCENT_COLOR_INDEX)
    }

    @Test fun `bash shows the project, a subdirectory, or the home as ~`() {
        assumeTrue(File("/bin/bash").canExecute())
        prompt.install()
        val accent = "\\[\\e[38;5;258m\\]"
        assertEquals("$accent" + "demo\\[\\e[0m\\] \\[\\e[32m\\]$\\[\\e[0m\\] ", ps1("/workspace/demo"))
        assertTrue(ps1("/workspace/demo/src").startsWith("$accent" + "demo/src\\["))
        assertTrue(ps1("/workspace").startsWith("$accent/workspace\\["))
        assertTrue(ps1(home.path).startsWith("$accent~\\["))
        assertTrue(ps1("${home.path}/notes").startsWith("$accent~/notes\\["))
        assertTrue(ps1("${home.path}x").startsWith("$accent${home.path}x\\["))
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
