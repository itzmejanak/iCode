package dev.icode.mobile.data.runtime

import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveExtractorTest {

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
        ArchiveExtractor().extractTarGz(source, out) {}
        assertEquals("icode\n", File(out, "etc/hostname").readText())
    }

    @Test
    fun `directories are created even when only implied by a path`() {
        val source = archive { file("usr/lib/deep/thing.txt", "x") }
        val out = target()
        ArchiveExtractor().extractTarGz(source, out) {}
        assertTrue(File(out, "usr/lib/deep").isDirectory)
    }

    @Test
    fun `an executable keeps its execute bit`() {
        val source = archive { file("bin/tool", "#!/bin/sh\n", mode = 0b111_101_101) }
        val out = target()
        ArchiveExtractor().extractTarGz(source, out) {}
        assertTrue(File(out, "bin/tool").canExecute())
    }

    @Test
    fun `a plain file does not become executable`() {
        val source = archive { file("etc/passwd", "root:x:0:0\n") }
        val out = target()
        ArchiveExtractor().extractTarGz(source, out) {}
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
        ArchiveExtractor().extractTarGz(source, out) {}
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
        ArchiveExtractor().extractTarGz(source, out) {}
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
        ArchiveExtractor().extractTarGz(source, out) {}
        assertEquals("arrived later", File(out, "usr/bin/early").readText())
    }

    @Test
    fun `an entry escaping the target is refused`() {
        val source = archive { file("../escaped.txt", "nope") }
        val out = target()
        ArchiveExtractor().extractTarGz(source, out) {}
        assertFalse(File(temporaryFolder.root, "escaped.txt").exists())
    }

    @Test
    fun `progress is reported and the final count is the entries written`() {
        val source = archive { repeat(5) { index -> file("file$index.txt", "$index") } }
        val out = target()
        var lastReported = -1L
        val written = ArchiveExtractor().extractTarGz(source, out) { lastReported = it }
        assertEquals(5L, written)
        assertEquals(5L, lastReported)
    }

    @Test
    fun `a tar gz unpacks where its entries are placed, its links and hard links mapped the same way`() {
        val file = temporaryFolder.newFile("tool-${System.nanoTime()}.tar.gz")
        TarArchiveOutputStream(GZIPOutputStream(file.outputStream())).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.putArchiveEntry(TarArchiveEntry("android-ndk-r27/"))
            tar.closeArchiveEntry()
            tar.file("android-ndk-r27/source.properties", "Pkg.Revision = 27.0.12077973\n")
            tar.file("android-ndk-r27/toolchains/llvm/prebuilt/linux-arm64/bin/clang", "elf", mode = 0b111_101_101)
            tar.putArchiveEntry(TarArchiveEntry("android-ndk-r27/toolchains/llvm/prebuilt/linux-x86_64", TarConstants.LF_SYMLINK).apply { linkName = "linux-arm64" })
            tar.closeArchiveEntry()
            tar.putArchiveEntry(
                TarArchiveEntry("android-ndk-r27/toolchains/llvm/prebuilt/linux-arm64/bin/clang++", TarConstants.LF_LINK)
                    .apply { linkName = "android-ndk-r27/toolchains/llvm/prebuilt/linux-arm64/bin/clang" },
            )
            tar.closeArchiveEntry()
            tar.finish()
        }
        val out = target()
        val written = ArchiveExtractor().extractTarGz(file, out, { it.substringAfter('/', "").takeIf(String::isNotEmpty) }) {}
        assertEquals(4L, written)
        assertEquals("Pkg.Revision = 27.0.12077973\n", File(out, "source.properties").readText())
        val clang = File(out, "toolchains/llvm/prebuilt/linux-arm64/bin/clang")
        assertTrue(clang.canExecute())
        assertEquals("linux-arm64", Files.readSymbolicLink(File(out, "toolchains/llvm/prebuilt/linux-x86_64").toPath()).toString())
        assertEquals("elf", File(out, "toolchains/llvm/prebuilt/linux-x86_64/bin/clang").readText())
        assertEquals("elf", File(out, "toolchains/llvm/prebuilt/linux-arm64/bin/clang++").readText())
        assertFalse(File(out, "android-ndk-r27").exists())
    }

    private fun zip(vararg entries: Pair<String, Int>): File {
        val file = temporaryFolder.newFile("tools-${System.nanoTime()}.zip")
        ZipArchiveOutputStream(file).use { zip ->
            entries.forEach { (name, mode) ->
                zip.putArchiveEntry(ZipArchiveEntry(name).apply { unixMode = mode })
                zip.write(name.toByteArray())
                zip.closeArchiveEntry()
            }
        }
        return file
    }

    @Test
    fun `a zip's entries land where they are placed, executables kept executable, the rest left out`() {
        val source = zip("build-tools/aapt2" to 0b111_000_000, "build-tools/NOTICE" to 0b110_100_100, "others/veridex" to 0b111_101_101)
        val out = target()
        val written = ArchiveExtractor().extractZip(source, out) { name -> name.removePrefix("build-tools/").takeIf { it != name } }
        assertEquals(2L, written)
        assertEquals("build-tools/aapt2", File(out, "aapt2").readText())
        assertTrue(File(out, "aapt2").canExecute())
        assertFalse(File(out, "NOTICE").canExecute())
        assertFalse(File(out, "veridex").exists())
    }

    @Test
    fun `a zip entry escaping the target is refused`() {
        val out = target()
        ArchiveExtractor().extractZip(zip("../escaped.txt" to 0b110_100_100), out)
        assertFalse(File(temporaryFolder.root, "escaped.txt").exists())
    }
}
