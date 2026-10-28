package dev.icode.mobile.data.io

import dev.icode.mobile.domain.files.ArchiveFormat
import dev.icode.mobile.domain.files.CompressFormat
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The explorer's Extract and Compress (docs/explorer-gaps-plan.md §3.11): every format, and what an untrusted archive may not do. */
class ExplorerArchivesTest {
    @get:Rule val temp = TemporaryFolder()

    private lateinit var site: File

    @Before fun tree() {
        site = temp.newFolder("site")
        File(site, "index.html").writeText("<h1>hi</h1>")
        File(site, "js").mkdirs()
        File(site, "js/app.js").writeText("run()")
        File(site, "build.sh").apply { writeText("#!/bin/sh") }.setExecutable(true, false)
        Files.createSymbolicLink(File(site, "latest").toPath(), File("index.html").toPath())
    }

    private fun out() = File(temp.root, "out-${System.nanoTime()}")

    private fun unpack(archive: File, format: ArchiveFormat, into: File, links: ArchiveLinks = ArchiveLinks.Inside, caseInsensitive: Boolean = false) =
        Archives.unpack(archive, format, into, Unpacking(links, caseInsensitive))

    @Test fun `a zip and a tar gz keep links and executable bits, both ways`() {
        listOf(CompressFormat.Zip to ArchiveFormat.Zip, CompressFormat.TarGz to ArchiveFormat.TarGz).forEach { (write, read) ->
            val archive = File(temp.root, "site-${write.name}${write.extension}")
            val packed = ArchiveWriting.pack(listOf(site), archive, write)
            assertEquals(6L, packed.entries)
            val into = out()
            val unpacked = unpack(archive, read, into)
            assertEquals(write.name, 6L, unpacked.entries)
            assertEquals("run()", File(into, "site/js/app.js").readText())
            assertTrue(write.name, File(into, "site/build.sh").canExecute())
            assertFalse(File(into, "site/index.html").canExecute())
            assertEquals("index.html", Files.readSymbolicLink(File(into, "site/latest").toPath()).toString())
        }
    }

    @Test fun `a 7z holds no link, and says how many it left out`() {
        val archive = File(temp.root, "site.7z")
        val packed = ArchiveWriting.pack(listOf(site), archive, CompressFormat.SevenZ)
        assertEquals(1, packed.skippedLinks)
        val index = Archives.index(archive, ArchiveFormat.SevenZ)!!
        assertEquals(5L, index.entries)
        assertEquals(25L, index.bytes)
        val into = out()
        unpack(archive, ArchiveFormat.SevenZ, into)
        assertEquals("<h1>hi</h1>", File(into, "site/index.html").readText())
        assertFalse(File(into, "site/latest").exists())
    }

    @Test fun `a tar, alone or compressed every way, and one compressed file`() {
        fun tar(name: String, wrap: (OutputStream) -> OutputStream): File = File(temp.root, name).also { file ->
            TarArchiveOutputStream(wrap(file.outputStream())).use { tar ->
                val bytes = "hello".toByteArray()
                tar.putArchiveEntry(TarArchiveEntry("notes/hello.txt").apply { size = bytes.size.toLong() })
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        mapOf(
            tar("a.tar") { it } to ArchiveFormat.Tar,
            tar("a.tar.gz") { GzipCompressorOutputStream(it) } to ArchiveFormat.TarGz,
            tar("a.tar.bz2") { BZip2CompressorOutputStream(it) } to ArchiveFormat.TarBz2,
            tar("a.tar.xz") { XZCompressorOutputStream(it) } to ArchiveFormat.TarXz,
        ).forEach { (archive, format) ->
            val into = out()
            unpack(archive, format, into)
            assertEquals(format.name, "hello", File(into, "notes/hello.txt").readText())
        }
        mapOf(
            ArchiveFormat.Gz to { it: OutputStream -> GzipCompressorOutputStream(it) },
            ArchiveFormat.Bz2 to { it: OutputStream -> BZip2CompressorOutputStream(it) },
            ArchiveFormat.Xz to { it: OutputStream -> XZCompressorOutputStream(it) },
        ).forEach { (format, wrap) ->
            val archive = File(temp.root, "notes.txt${format.suffixes.first()}")
            wrap(archive.outputStream()).use { it.write("plain".toByteArray()) }
            val into = out()
            unpack(archive, format, into)
            assertEquals(format.name, "plain", File(into, "notes.txt").readText())
        }
    }

    @Test fun `a 7z with a password opens with it, and says so without`() {
        val archive = File(temp.root, "secret.7z")
        SevenZOutputFile(archive, "s3cret".toCharArray()).use { seven ->
            val file = File(site, "index.html")
            seven.putArchiveEntry(seven.createArchiveEntry(file, "index.html"))
            seven.write(file.toPath())
            seven.closeArchiveEntry()
        }
        assertTrue(Archives.locked(archive))
        assertFalse(Archives.locked(File(temp.root, "plain.7z").also { ArchiveWriting.pack(listOf(site), it, CompressFormat.SevenZ) }))
        assertThrows(PasswordRequiredException::class.java) { unpack(archive, ArchiveFormat.SevenZ, out()) }
        val into = out()
        Archives.unpack(archive, ArchiveFormat.SevenZ, into, Unpacking(ArchiveLinks.Inside, password = "s3cret".toCharArray()))
        assertEquals("<h1>hi</h1>", File(into, "index.html").readText())
        assertThrows(IOException::class.java) {
            Archives.unpack(archive, ArchiveFormat.SevenZ, out(), Unpacking(ArchiveLinks.Inside, password = "wrong".toCharArray()))
        }
    }

    private fun hostile(): File = File(temp.root, "hostile.tar").also { file ->
        TarArchiveOutputStream(file.outputStream()).use { tar ->
            fun link(name: String, to: String) {
                tar.putArchiveEntry(TarArchiveEntry(name, TarConstants.LF_SYMLINK).apply { linkName = to })
                tar.closeArchiveEntry()
            }
            fun file(name: String, body: String) {
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = body.length.toLong() })
                tar.write(body.toByteArray())
                tar.closeArchiveEntry()
            }
            file("../escaped.txt", "nope")
            file("app/README.md", "upper")
            file("app/readme.md", "lower")
            link("app/inside", "README.md")
            link("app/up", "../../../etc")
            link("app/root", "/etc/passwd")
        }
    }

    @Test fun `in Private an entry never escapes, and a link is made only pointing inside`() {
        val into = out()
        val unpacked = unpack(hostile(), ArchiveFormat.Tar, into)
        assertFalse(File(temp.root, "escaped.txt").exists())
        assertTrue(Files.isSymbolicLink(File(into, "app/inside").toPath()))
        assertFalse(Files.exists(File(into, "app/up").toPath(), LinkOption.NOFOLLOW_LINKS))
        assertFalse(Files.exists(File(into, "app/root").toPath(), LinkOption.NOFOLLOW_LINKS))
        assertEquals(2, unpacked.skipped)
        assertEquals("lower", File(into, "app/readme.md").readText())
    }

    @Test fun `on phone storage no link is made, and a name twinned but for case is left out`() {
        val into = out()
        val unpacked = unpack(hostile(), ArchiveFormat.Tar, into, ArchiveLinks.None, caseInsensitive = true)
        assertEquals("upper", File(into, "app/README.md").readText())
        assertFalse(File(into, "app/readme.md").exists())
        assertFalse(Files.exists(File(into, "app/inside").toPath(), LinkOption.NOFOLLOW_LINKS))
        assertEquals(4, unpacked.skipped)
    }

    @Test fun `what iCode cannot open says why, and an index is a zip's or a 7z's alone`() {
        val rar = File(temp.root, "old.rar").apply { writeText("Rar!") }
        assertEquals("old.rar: iCode cannot open .rar archives: their format is UnRAR's own", assertThrows(IOException::class.java) { unpack(rar, ArchiveFormat.Rar, out()) }.message)
        assertNull(Archives.index(rar, ArchiveFormat.TarGz))
        val zip = File(temp.root, "site.zip").also { ArchiveWriting.pack(listOf(site), it, CompressFormat.Zip) }
        assertEquals(6L, Archives.index(zip, ArchiveFormat.Zip)!!.entries)
    }
}
