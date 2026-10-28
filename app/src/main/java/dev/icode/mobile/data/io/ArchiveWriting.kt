package dev.icode.mobile.data.io

import dev.icode.mobile.domain.files.CompressFormat
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.archivers.zip.UnixStat
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

/** What packing wrote, and the links it left out: a 7z can hold none. */
internal class Packed(val entries: Long, val skippedLinks: Int)

/**
 * Writing archives (docs/explorer-gaps-plan.md §3.11): Compress's zip, 7z or tar.gz of its sources,
 * each under its own name. Links are never followed: a zip and a tar.gz keep each as the link it is,
 * and every executable bit; a 7z can hold neither, so it leaves links out and counts them. Sockets
 * and pipes are nothing to keep.
 */
internal object ArchiveWriting {
    /** [sources] into [archive], each entry reported to [onEntry] by its name in it; throwing stops the packing. */
    fun pack(sources: List<File>, archive: File, format: CompressFormat, onEntry: (String) -> Unit = {}): Packed = when (format) {
        CompressFormat.Zip -> zip(sources, archive, onEntry)
        CompressFormat.TarGz -> tarGz(sources, archive, onEntry)
        CompressFormat.SevenZ -> sevenZ(sources, archive, onEntry)
    }

    private fun zip(sources: List<File>, archive: File, onEntry: (String) -> Unit): Packed {
        var entries = 0L
        ZipArchiveOutputStream(archive).use { zip ->
            walk(sources) { path, name, attributes ->
                val entry = ZipArchiveEntry(if (attributes.isDirectory) "$name/" else name).apply { time = attributes.lastModifiedTime().toMillis() }
                when {
                    attributes.isDirectory -> entry.unixMode = UnixStat.DIR_FLAG or FOLDER_MODE
                    attributes.isSymbolicLink -> entry.unixMode = UnixStat.LINK_FLAG or LINK_MODE
                    else -> entry.unixMode = UnixStat.FILE_FLAG or fileMode(path)
                }
                zip.putArchiveEntry(entry)
                when {
                    attributes.isSymbolicLink -> zip.write(Files.readSymbolicLink(path).toString().toByteArray())
                    attributes.isRegularFile -> Files.copy(path, zip)
                }
                zip.closeArchiveEntry()
                entries++
                onEntry(name)
            }
        }
        return Packed(entries, skippedLinks = 0)
    }

    private fun tarGz(sources: List<File>, archive: File, onEntry: (String) -> Unit): Packed {
        var entries = 0L
        TarArchiveOutputStream(GzipCompressorOutputStream(archive.outputStream().buffered())).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            walk(sources) { path, name, attributes ->
                val entry = when {
                    attributes.isDirectory -> TarArchiveEntry("$name/").apply { mode = TarArchiveEntry.DEFAULT_DIR_MODE }
                    attributes.isSymbolicLink -> TarArchiveEntry(name, TarConstants.LF_SYMLINK).apply { linkName = Files.readSymbolicLink(path).toString() }
                    else -> TarArchiveEntry(name).apply {
                        size = attributes.size()
                        mode = UnixStat.FILE_FLAG or fileMode(path)
                    }
                }
                entry.setModTime(attributes.lastModifiedTime())
                tar.putArchiveEntry(entry)
                if (attributes.isRegularFile) Files.copy(path, tar)
                tar.closeArchiveEntry()
                entries++
                onEntry(name)
            }
            tar.finish()
        }
        return Packed(entries, skippedLinks = 0)
    }

    private fun sevenZ(sources: List<File>, archive: File, onEntry: (String) -> Unit): Packed {
        var entries = 0L
        var links = 0
        SevenZOutputFile(archive).use { seven ->
            walk(sources) { path, name, attributes ->
                if (attributes.isSymbolicLink) return@walk run { links++ }
                seven.putArchiveEntry(seven.createArchiveEntry(path, name, LinkOption.NOFOLLOW_LINKS))
                if (attributes.isRegularFile) seven.write(path)
                seven.closeArchiveEntry()
                entries++
                onEntry(name)
            }
            seven.finish()
        }
        return Packed(entries, links)
    }

    /** Each entry of [sources], folders before what is in them, by its name in the archive; links as links, never followed. */
    private fun walk(sources: List<File>, visit: (path: Path, name: String, attributes: BasicFileAttributes) -> Unit) = sources.forEach { source ->
        val top = source.toPath()
        Files.walkFileTree(
            top,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                    visit(dir, nameOf(top, dir), attributes)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    if (attributes.isRegularFile || attributes.isSymbolicLink) visit(file, nameOf(top, file), attributes)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun nameOf(top: Path, path: Path): String =
        top.fileName.toString() + top.relativize(path).toString().takeIf { it.isNotEmpty() }?.let { "/$it" }.orEmpty()

    private fun fileMode(path: Path) = if (Files.isExecutable(path)) EXECUTABLE_MODE else FILE_MODE

    private const val FOLDER_MODE = 0b111_101_101
    private const val EXECUTABLE_MODE = 0b111_101_101
    private const val FILE_MODE = 0b110_100_100
    private const val LINK_MODE = 0b111_111_111
}
