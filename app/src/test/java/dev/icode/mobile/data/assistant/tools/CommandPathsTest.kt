package dev.icode.mobile.data.assistant.tools

import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/assistant-permissions-plan.md §4.5: what a command's text shows of where it reaches. */
class CommandPathsTest {
    private fun paths(command: String) = CommandPaths.of(command)

    @Test fun `absolute, home and relative paths are seen, between any of the shell's separators`() {
        assertEquals(listOf("/root", "../portofolio/package.json"), paths("cd /root && ls; cat ../portofolio/package.json"))
        assertEquals(listOf("~/notes", "/tmp/out"), paths("grep -r x ~/notes>/tmp/out"))
        assertEquals(listOf(".", "./build"), paths("ls . | wc -l && rm -rf ./build"))
        assertEquals(listOf("/opt/tools"), paths("./configure --prefix=/opt/tools").drop(1))
    }

    @Test fun `quotes and escapes join a word as the shell does`() {
        assertEquals(listOf("/my docs/a b.txt"), paths("""cat "/my docs/a b.txt""""))
        assertEquals(listOf("/my docs"), paths("ls '/my docs'"))
        assertEquals(listOf("/my docs"), paths("""ls /my\ docs"""))
    }

    @Test fun `a variable or a glob stands for what follows, and the folder named before it is seen`() {
        assertEquals(listOf("/var/log"), paths("ls /var/log/*.log"))
        assertEquals(listOf("/tmp"), paths("""rm -rf "/tmp/${'$'}USER""""))
        assertEquals(listOf("/"), paths("ls /${'$'}X"))
    }

    @Test fun `what is not a path is not taken for one`() {
        assertEquals(emptyList<String>(), paths("npm install && git status --short"))
        assertEquals(emptyList<String>(), paths("echo ${'$'}HOME/x https://example.com"))
        assertEquals(emptyList<String>(), paths("make # cd /etc"))
    }
}
