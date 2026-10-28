package dev.icode.mobile.data.io

import dev.icode.mobile.domain.files.ArchiveFormat
import dev.icode.mobile.domain.files.ArchiveFormats
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

/** How an archive's links are made: as written (a pinned download), only pointing inside what is unpacked, or never. */
internal enum class ArchiveLinks { AsWritten, Inside, None }

/** How an archive is unpacked into a folder. */
internal class Unpacking(
    val links: ArchiveLinks,
    /** Phone storage: two names that differ only in case cannot both be there; the second is left out. */
    val caseInsensitive: Boolean = false,
    /** A 7z's password, when it has one. */
    val password: CharArray? = null,
    /** Where each entry goes, as a path under the folder; null leaves it out. */
    val place: (name: String) -> String? = { it },
    /** Each entry once written, by its path in the folder; throwing stops the unpacking. */
    val onEntry: (path: String) -> Unit = {},
)

/** What unpacking wrote, and how many entries it left out: links leading out, names a folder could not hold twice. */
internal class Unpacked(val entries: Long, val skipped: Int)

/** An archive's entries and their size, from its own index — a zip's or a 7z's; a tar is a stream and has none. */
internal class ArchiveIndex(val entries: Long, val bytes: Long)

/**
 * Reading archives (docs/explorer-gaps-plan.md §3.11), for the runtime's pinned downloads — its root
 * filesystem, a tool's zips and tar.gz — and the explorer's Extract alike, on Commons Compress, and
 * XZ for Java for .7z and .xz. Every entry lands inside the folder, whatever its name says: `../` in
 * an entry is the oldest trick there is. Ownership is ignored: there is one user. Hard links are
 * copied, since Android denies `link()` to apps. Device nodes, FIFOs and sockets are left out: /dev
 * is the host's. Writing them is [ArchiveWriting]'s.
 */
internal object Archives {
    fun unpack(archive: File, format: ArchiveFormat, target: File, unpacking: Unpacking): Unpacked {
        format.unreadable?.let { throw IOException("${archive.name}: $it") }
        val writer = EntryWriter(target, unpacking)
        when (format) {
            ArchiveFormat.Zip -> zip(archive, writer)
            ArchiveFormat.SevenZ -> sevenZ(archive, unpacking.password, writer)
            ArchiveFormat.Gz, ArchiveFormat.Bz2, ArchiveFormat.Xz ->
                decompressed(archive, format).use { writer.file(ArchiveFormats.stem(archive.name), it, executable = false) }
            else -> decompressed(archive, format).use { input -> TarArchiveInputStream(input).use { tar(it, writer) } }
        }
        return writer.finish()
    }

    /** [archive]'s own index: a zip's or a 7z's entries and size, read before anything is written; null for a stream. */
    fun index(archive: File, format: ArchiveFormat, password: CharArray? = null): ArchiveIndex? = when (format) {
        ArchiveFormat.Zip -> ZipFile.builder().setFile(archive).get().use { zip ->
            zip.entries.toList().let { entries -> ArchiveIndex(entries.size.toLong(), entries.sumOf { it.size.coerceAtLeast(0) }) }
        }
        ArchiveFormat.SevenZ -> sevenZFile(archive, password).use { seven ->
            seven.entries.toList().let { entries -> ArchiveIndex(entries.size.toLong(), entries.sumOf { it.size }) }
        }
        else -> null
    }

    /** A 7z whose contents, or whose very index, can be read only with its password. */
    fun locked(archive: File): Boolean = runCatching {
        sevenZFile(archive, null).use { seven ->
            val first = generateSequence { seven.nextEntry }.firstOrNull { it.hasStream() } ?: return@use false
            seven.getInputStream(first).use { it.read() }
            false
        }
    }.getOrElse { it is PasswordRequiredException }

    private fun decompressed(archive: File, format: ArchiveFormat): InputStream {
        val raw = archive.inputStream().buffered()
        return when (format) {
            ArchiveFormat.TarGz, ArchiveFormat.Gz -> GzipCompressorInputStream(raw, true)
            ArchiveFormat.TarBz2, ArchiveFormat.Bz2 -> BZip2CompressorInputStream(raw, true)
            ArchiveFormat.TarXz, ArchiveFormat.Xz -> XZCompressorInputStream(raw, true)
            else -> raw
        }
    }

    private fun tar(tar: TarArchiveInputStream, writer: EntryWriter) {
        while (true) {
            val entry: TarArchiveEntry = tar.nextEntry ?: break
            when {
                entry.isDirectory -> writer.folder(entry.name)
                entry.isSymbolicLink -> writer.symbolicLink(entry.name, entry.linkName)
                entry.isLink -> writer.hardLink(entry.name, entry.linkName)
                // Only the executable bit matters: everything runs as one user.
                entry.isFile -> writer.file(entry.name, tar, executable = entry.mode and EXECUTE_BITS != 0)
            }
        }
    }

    /** A zip keeps its executable bits and links only as Unix modes, which `java.util.zip` cannot read; Commons Compress can. */
    private fun zip(archive: File, writer: EntryWriter) = ZipFile.builder().setFile(archive).get().use { zip ->
        zip.entries.asSequence().forEach { entry ->
            if (!zip.canReadEntryData(entry)) {
                throw IOException(
                    if (entry.generalPurposeBit.usesEncryption()) "${archive.name} is encrypted: iCode cannot open an encrypted zip"
                    else "${archive.name} uses a compression iCode cannot read",
                )
            }
            when {
                entry.isDirectory -> writer.folder(entry.name)
                entry.isUnixSymlink -> writer.symbolicLink(entry.name, zip.getUnixSymlink(entry))
                else -> zip.getInputStream(entry).use { writer.file(entry.name, it, executable = entry.unixMode and EXECUTE_BITS != 0) }
            }
        }
    }

    /** A 7z holds files and folders only: no links, no executable bits (§2). */
    private fun sevenZ(archive: File, password: CharArray?, writer: EntryWriter) = sevenZFile(archive, password).use { seven ->
        while (true) {
            val entry = seven.nextEntry ?: break
            when {
                entry.isAntiItem -> Unit
                entry.isDirectory -> writer.folder(entry.name)
                else -> seven.getInputStream(entry).use { writer.file(entry.name, it, executable = false) }
            }
        }
    }

    private fun sevenZFile(archive: File, password: CharArray?): SevenZFile =
        SevenZFile.builder().setFile(archive).apply { password?.let(::setPassword) }.get()

    /** Owner, group and other execute bits. */
    const val EXECUTE_BITS = 0b001_001_001
}

/** Writes an archive's entries under [target], as [unpacking] says, counting what it wrote and what it left out. */
private class EntryWriter(target: File, private val unpacking: Unpacking) {
    private val root: File = target.apply { mkdirs() }.canonicalFile
    private var entries = 0L
    private var skipped = 0
    private val namesSeen = HashMap<String, String>()
    private val hardLinks = mutableListOf<Pair<File, String>>()

    fun folder(name: String) {
        val destination = destination(name) ?: return
        destination.mkdirs()
        written(destination)
    }

    fun file(name: String, input: InputStream, executable: Boolean) {
        val destination = destination(name) ?: return
        if (twin(destination)) return
        destination.parentFile?.mkdirs()
        destination.outputStream().buffered().use { input.copyTo(it) }
        if (executable) destination.setExecutable(true, false)
        written(destination)
    }

    fun symbolicLink(name: String, link: String) {
        val destination = destination(name) ?: return
        val points = (if (Paths.get(link).isAbsolute) Paths.get(link) else destination.toPath().resolveSibling(link)).normalize()
        val made = when (unpacking.links) {
            ArchiveLinks.AsWritten -> true
            ArchiveLinks.Inside -> points.startsWith(root.toPath()) && points != root.toPath()
            ArchiveLinks.None -> false
        }
        if (!made || twin(destination)) return run { skipped++ }
        destination.parentFile?.mkdirs()
        runCatching {
            Files.deleteIfExists(destination.toPath())
            Files.createSymbolicLink(destination.toPath(), Paths.get(link))
        }
        written(destination)
    }

    /** A hard link can point at an entry later in the archive: it is copied once everything else is there. */
    fun hardLink(name: String, origin: String) {
        destination(name)?.let { hardLinks += it to origin }
    }

    fun finish(): Unpacked {
        hardLinks.forEach { (destination, origin) ->
            val source = unpacking.place(origin)?.let { resolve(it) }?.takeIf { it.isFile } ?: return@forEach
            if (twin(destination)) return@forEach
            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = true)
            written(destination)
        }
        return Unpacked(entries, skipped)
    }

    private fun destination(name: String): File? = unpacking.place(name)?.let(::resolve)

    /** [name] under the root, or null when it would land outside it. */
    private fun resolve(name: String): File? {
        if (name.isBlank()) return null
        val resolved = runCatching { File(root, name).canonicalFile }.getOrNull() ?: return null
        return resolved.takeIf { it.path == root.path || it.path.startsWith(root.path + File.separator) }
    }

    /** On phone storage, a name already written but for its case: the second is left out, and counted. */
    private fun twin(destination: File): Boolean {
        if (!unpacking.caseInsensitive) return false
        val seen = namesSeen.putIfAbsent(destination.path.lowercase(), destination.path) ?: return false
        return (seen != destination.path).also { if (it) skipped++ }
    }

    private fun written(destination: File) {
        entries++
        unpacking.onEntry(root.toPath().relativize(destination.toPath()).toString())
    }
}
