package dev.icode.mobile.data.git

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitCheckoutRepairTest {
    @get:Rule val temp = TemporaryFolder()

    private fun records(vararg records: String): GitCheckoutRepair.Recorded {
        val reader = GitCheckoutRepair.Reader()
        records.forEach { reader.accept(it.toByteArray()) }
        return reader.recorded()
    }

    @Test fun `the changed files come first, then the index entries Git records as executables and links`() {
        val recorded = records(
            "notes.md",
            "",
            "100644 1111111111111111111111111111111111111111 0\tnotes.md",
            "100755 2222222222222222222222222222222222222222 0\tgradlew",
            "120000 3333333333333333333333333333333333333333 0\tsub dir/link\twith tab",
            "100755 4444444444444444444444444444444444444444 2\tconflicted.sh",
        )
        assertEquals(setOf("notes.md"), recorded.modified)
        assertEquals(listOf("gradlew"), recorded.executables)
        assertEquals(listOf("sub dir/link\twith tab"), recorded.links)
    }

    @Test fun `executables get x, and an unchanged link file becomes the link it names`() {
        val top = temp.newFolder("app")
        val gradlew = File(top, "gradlew").apply { writeText("#!/bin/sh\necho ok\n") }
        File(top, "lib").mkdirs()
        File(top, "lib/target.txt").writeText("target")
        val link = File(top, "lib/link").apply { writeText("target.txt") }
        val restored = GitCheckoutRepair.restore(
            top,
            GitCheckoutRepair.Recorded(emptySet(), listOf("gradlew"), listOf("lib/link")),
        )
        assertEquals(GitCheckoutRepair.Restored(executables = 1, links = 1), restored)
        assertTrue(gradlew.canExecute())
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertEquals(Paths.get("target.txt"), Files.readSymbolicLink(link.toPath()))
        assertEquals("target", link.readText())
        assertEquals(listOf("gradlew", "lib"), top.list()!!.sorted())
    }

    @Test fun `a link file the user changed stays their file`() {
        val top = temp.newFolder("app")
        val edited = File(top, "link").apply { writeText("my own notes") }
        GitCheckoutRepair.restore(top, GitCheckoutRepair.Recorded(setOf("link"), emptyList(), listOf("link")))
        assertFalse(Files.isSymbolicLink(edited.toPath()))
        assertEquals("my own notes", edited.readText())
    }

    @Test fun `a path leading out of the repository or through a link is left alone`() {
        val top = temp.newFolder("app")
        val outside = temp.newFolder("outside")
        val victim = File(outside, "victim").apply { writeText("elsewhere") }
        Files.createSymbolicLink(File(top, "out").toPath(), outside.toPath())
        val restored = GitCheckoutRepair.restore(
            top,
            GitCheckoutRepair.Recorded(emptySet(), listOf("../outside/victim", "out/victim"), listOf("out/victim", "/etc/passwd")),
        )
        assertEquals(GitCheckoutRepair.Restored(0, 0), restored)
        assertFalse(victim.canExecute())
        assertFalse(Files.isSymbolicLink(victim.toPath()))
    }

    /** What was measured with Git 2.43, on a checkout made as phone storage leaves it. */
    @Test fun `with the real Git, a phone-storage checkout reads clean once restored`() {
        assumeTrue("git is not installed", runCatching { git(temp.root, "--version") }.isSuccess)
        val origin = temp.newFolder("origin")
        git(origin, "init", "-q", "-b", "main")
        File(origin, "gradlew").apply { writeText("#!/bin/sh\necho ok\n"); setExecutable(true) }
        File(origin, "sub").mkdirs()
        File(origin, "sub/data.txt").writeText("data")
        Files.createSymbolicLink(File(origin, "sub/uplink").toPath(), Paths.get("../gradlew"))
        Files.createSymbolicLink(File(origin, "elink").toPath(), Paths.get("gradlew"))
        git(origin, "add", "-A")
        git(origin, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "init")

        val imported = File(temp.root, "imported")
        // As Git on phone storage checks out: links as files, both settings recorded false.
        git(temp.root, "clone", "-q", "--config", "core.symlinks=false", "--config", "core.fileMode=false", origin.path, imported.path)
        // Phone storage has no x bit, and the user edited one link's file.
        File(imported, "gradlew").setExecutable(false, false)
        File(imported, "elink").writeText("my notes")

        val reader = GitCheckoutRepair.Reader()
        shell(imported, GitCheckoutRepair.SCRIPT).split('\u0000').dropLast(1).forEach { reader.accept(it.toByteArray()) }
        val restored = GitCheckoutRepair.restore(imported, reader.recorded())
        shell(imported, GitCheckoutRepair.SETTINGS_SCRIPT)

        assertEquals(GitCheckoutRepair.Restored(executables = 1, links = 1), restored)
        assertEquals("ok\n", shell(imported, "./gradlew"))
        assertTrue(Files.isSymbolicLink(File(imported, "sub/uplink").toPath()))
        assertEquals(" T elink\n", git(imported, "status", "--porcelain"))
    }

    private fun git(directory: File, vararg arguments: String) = run(directory, listOf("git") + arguments)

    private fun shell(directory: File, script: String) = run(directory, listOf("sh", "-c", script))

    private fun run(directory: File, command: List<String>): String {
        // Unit tests compile against android.jar, which has no Redirect.DISCARD (Java 9).
        val process = ProcessBuilder(command).directory(directory).redirectError(File("/dev/null")).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "$command failed" }
        return output
    }
}
