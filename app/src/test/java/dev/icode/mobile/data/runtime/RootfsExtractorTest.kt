package dev.icode.mobile.data.runtime

import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RootfsExtractorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun archive(build: TarArchiveOutputStream.() -> Unit): File {
        val file = temporaryFolder.newFile("rootfs-${System.nanoTime()}.tar.gz")
        TarArchiveOutputStream(GZIPOutputStream(file.outputStream())).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.build()
            tar.finish()
        }
        return file
    }

    private fun TarArchiveOutputStream.file(name: String, body: String, mode: Int = 0b110_100_100) {
        val entry = TarArchiveEntry(name)
        val bytes = body.toByteArray()
        entry.size = bytes.size.toLong()
        entry.mode = mode
        putArchiveEntry(entry)
        write(bytes)
        closeArchiveEntry()
    }

    private fun target() = File(temporaryFolder.root, "out-${System.nanoTime()}")

    @Test
    fun `regular files land with their contents`() {
        val source = archive { file("etc/hostname", "icode\n") }
        val out = target()
        RootfsExtractor().extract(source, out) {}
        assertEquals("icode\n", File(out, "etc/hostname").readText())
    }

    @Test
    fun `directories are created even when only implied by a path`() {
        val source = archive { file("usr/lib/deep/thing.txt", "x") }
        val out = target()
        RootfsExtractor().extract(source, out) {}
        assertTrue(File(out, "usr/lib/deep").isDirectory)
    }

    @Test
    fun `an executable keeps its execute bit`() {
        val source = archive { file("bin/tool", "#!/bin/sh\n", mode = 0b111_101_101) }
        val out = target()
        RootfsExtractor().extract(source, out) {}
        assertTrue(File(out, "bin/tool").canExecute())
    }

    @Test
    fun `a plain file does not become executable`() {
        val source = archive { file("etc/passwd", "root:x:0:0\n") }
        val out = target()
        RootfsExtractor().extract(source, out) {}
        assertFalse(File(out, "etc/passwd").canExecute())
    }

    @Test
    fun `a symbolic link is created as a link, not a copy`() {
        val source = archive {
            file("usr/bin/sh", "shell")
            val link = TarArchiveEntry("bin/sh", TarArchiveEntry.LF_SYMLINK)
            link.linkName = "../usr/bin/sh"
            putArchiveEntry(link)
            closeArchiveEntry()
        }
        val out = target()
        RootfsExtractor().extract(source, out) {}
        assertTrue(Files.isSymbolicLink(File(out, "bin/sh").toPath()))
    }

    /** Android denies `link()` to app processes, so the content is copied. */
    @Test
    fun `a hard link becomes a copy with the same contents`() {
        val source = archive {
            file("usr/bin/perl", "perl binary")
            val link = TarArchiveEntry("usr/bin/perl5.38", TarArchiveEntry.LF_LINK)
            link.linkName = "usr/bin/perl"
            putArchiveEntry(link)
            closeArchiveEntry()
        }
        val out = target()
        RootfsExtractor().extract(source, out) {}
        val copy = File(out, "usr/bin/perl5.38")
        assertTrue(copy.isFile)
        assertFalse(Files.isSymbolicLink(copy.toPath()))
        assertEquals("perl binary", copy.readText())
    }

    @Test
    fun `a hard link is resolved even when its target appears later`() {
        val source = archive {
            val link = TarArchiveEntry("usr/bin/early", TarArchiveEntry.LF_LINK)
            link.linkName = "usr/bin/late"
            putArchiveEntry(link)
            closeArchiveEntry()
            file("usr/bin/late", "arrived later")
        }
        val out = target()
        RootfsExtractor().extract(source, out) {}
        assertEquals("arrived later", File(out, "usr/bin/early").readText())
    }

    @Test
    fun `an entry escaping the target is refused`() {
        val source = archive { file("../escaped.txt", "nope") }
        val out = target()
        RootfsExtractor().extract(source, out) {}
        assertFalse(File(temporaryFolder.root, "escaped.txt").exists())
    }

    @Test
    fun `progress is reported and the final count is the entries written`() {
        val source = archive { repeat(5) { index -> file("file$index.txt", "$index") } }
        val out = target()
        var lastReported = -1L
        val written = RootfsExtractor().extract(source, out) { lastReported = it }
        assertEquals(5L, written)
        assertEquals(5L, lastReported)
    }
}
