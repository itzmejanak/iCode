package dev.icode.mobile.data.git

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class GitIndexVersionsTest {
    // What `git ls-files -s -z` printed (git 2.43) for a repository mid-merge: a.txt edited,
    // c.txt in conflict, crlf.txt checked out as CRLF, a link, and a name with a space and a star.
    private val listing = listOf(
        "100644 814f4a422927b82f5f8a43f8fab6d3839e3983f2 0\ta.txt",
        "100644 f2ad6c76f0115a6ba5b00456a849810e7ec0af20 1\tc.txt",
        "100644 ba2906d0666cf726c7eaadd2cd3db615dedfdf3a 2\tc.txt",
        "100644 2299c37978265a95cbe835a4b0f0bbf15aad5549 3\tc.txt",
        "100644 b77b4eb1d946f923f61785536da9ca5af6909f06 0\tcrlf.txt",
        "120000 8d14cbf983b3fad683171c9418998d9f68340823 0\tlink",
        "100755 babcbcdd108e41f29ea3c3c60354eebc6cf64ad5 0\twe ird*.txt",
    ).joinToString("\u0000", postfix = "\u0000")

    @Test fun `a file has a version when the index holds it at stage 0 as a regular file`() {
        val paths = listOf("a.txt", "crlf.txt", "we ird*.txt", "link", "c.txt", "u.txt")
        // cat-file wrote a file for each path it could read: the link's target too, the conflicted and untracked none.
        val written = mapOf(0 to "one\ntwo\n", 1 to "x\r\ny\r\n", 2 to "star\n", 3 to "a.txt")
        val versions = GitIndexVersions.read(listing, paths) { written[it]?.toByteArray() }
        assertEquals(setOf("a.txt", "crlf.txt", "we ird*.txt"), versions.keys)
        assertEquals("x\r\ny\r\n", versions.getValue("crlf.txt").bytes.decodeToString())
        assertEquals("100755", versions.getValue("we ird*.txt").mode)
        assertEquals("100644", versions.getValue("a.txt").mode)
    }

    @Test fun `a version Git could not write is none`() {
        assertEquals(emptyMap<String, IndexVersion>(), GitIndexVersions.read(listing, listOf("a.txt")) { null })
    }

    @Test fun `the scripts hand each path to Git as one argument, whatever it holds`() {
        assumeTrue("needs a POSIX shell", File(SH).canExecute())
        val root = Files.createTempDirectory("index-versions").toFile()
        try {
            // A git that records its arguments one per line, and writes each one's name for cat-file.
            val bin = File(root, "bin").apply { mkdirs() }
            File(bin, "git").apply {
                writeText("#!/bin/sh\nfor a in \"\$@\"; do printf '%s\\n' \"\$a\" >> \"$root/args\"; done\nprintf '=end=\\n' >> \"$root/args\"\necho object-id\n")
                setExecutable(true)
            }
            val odd = "it's \$HOME \"a b\" `x`.txt"
            run(root, bin, GitIndexVersions.script(listOf(odd), "$root/out") + "; " + GitIndexVersions.stageScript(odd, "100644", "$root/staged"))
            val calls = File(root, "args").readText().split("=end=\n").filter { it.isNotEmpty() }.map { it.lines().filter(String::isNotEmpty) }
            val base = listOf("-c", "core.quotepath=false", "-c", "safe.directory=*", "--no-optional-locks")
            assertEquals(base + listOf("--literal-pathspecs", "ls-files", "-s", "-z", "--", odd), calls[0])
            assertEquals(base + listOf("cat-file", "--filters", ":$odd"), calls[1])
            assertEquals(base + listOf("hash-object", "-w", "--path=$odd", "--", "$root/staged"), calls[2])
            assertEquals(base + listOf("update-index", "--cacheinfo", "100644", "object-id", odd), calls[3])
        } finally {
            root.deleteRecursively()
        }
    }

    private fun run(root: File, bin: File, script: String) {
        val process = ProcessBuilder(SH, "-c", script).directory(root).apply { environment()["PATH"] = "${bin.path}:/usr/bin:/bin" }.start()
        check(process.waitFor(10, TimeUnit.SECONDS)) { "the script did not finish" }
        assertEquals(process.errorStream.bufferedReader().readText(), 0, process.exitValue())
    }

    private companion object {
        const val SH = "/bin/sh"
    }
}
