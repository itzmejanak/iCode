package dev.icode.mobile.data.runtime

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile

/**
 * Unpacks a pinned archive directly, without PRoot: the root filesystem (a tar.gz), and the zips a
 * runtime tool's setup downloads.
 *
 * Extracting through PRoot means every syscall is intercepted with ptrace, and
 * unpacking a base system is thousands of small files — open, write, close,
 * chown, utimes each — so the interception dominates and the install crawls.
 * Nothing about unpacking actually needs the guest: it is writing files into a
 * directory this app owns.
 *
 * The two things PRoot was there for are handled directly instead:
 *
 * - **Ownership** is ignored. There is one user here, and the guest runs as
 *   fake root anyway, so the `chown` calls that would fail are simply not made.
 * - **Hard links are copied.** Android denies `link()` to app processes, which
 *   is the whole reason `--link2symlink` existed for this step. A copy costs
 *   some space and is correct, where a dangling emulated symlink is not.
 */
internal class ArchiveExtractor {

    /**
     * Extracts the tar.gz [archive] into [target], reporting entries as it goes,
     * and returns how many it wrote.
     */
    fun extractTarGz(archive: File, target: File, onEntry: (Long) -> Unit): Long {
        target.mkdirs()
        val root = target.canonicalFile
        var entries = 0L
        val deferredLinks = mutableListOf<Pair<File, String>>()

        archive.inputStream().buffered().use { raw ->
            GZIPInputStream(raw).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        val destination = resolve(root, entry.name) ?: continue
                        write(entry, destination, tar, deferredLinks)
                        if (++entries % ENTRY_REPORT_STEP == 0L) onEntry(entries)
                    }
                }
            }
        }
        // Hard links can point at an entry that appears later in the archive,
        // so they are made once everything else exists.
        deferredLinks.forEach { (destination, source) ->
            val origin = resolve(root, source) ?: return@forEach
            if (origin.isFile) {
                destination.parentFile?.mkdirs()
                origin.copyTo(destination, overwrite = true)
            }
        }
        onEntry(entries)
        return entries
    }

    /**
     * Extracts the zip [archive] into [target]: each entry [place] gives a path for, at that path
     * under [target] — null leaves it out. Returns how many it wrote. A zip keeps its executable
     * bits only as Unix modes, which `java.util.zip` cannot read; commons-compress can.
     */
    fun extractZip(archive: File, target: File, place: (String) -> String? = { it }): Long {
        target.mkdirs()
        val root = target.canonicalFile
        var entries = 0L
        ZipFile.builder().setFile(archive).get().use { zip ->
            zip.entries.asSequence().forEach { entry ->
                val destination = place(entry.name)?.let { resolve(root, it) } ?: return@forEach
                when {
                    entry.isDirectory -> destination.mkdirs()
                    entry.isUnixSymlink -> writeSymbolicLink(destination, zip.getUnixSymlink(entry))
                    else -> {
                        destination.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input -> destination.outputStream().buffered().use { input.copyTo(it) } }
                        if (entry.unixMode and EXECUTE_BITS != 0) destination.setExecutable(true, false)
                    }
                }
                entries++
            }
        }
        return entries
    }

    private fun write(
        entry: TarArchiveEntry,
        destination: File,
        tar: InputStream,
        deferredLinks: MutableList<Pair<File, String>>,
    ) {
        when {
            entry.isDirectory -> destination.mkdirs()
            entry.isSymbolicLink -> writeSymbolicLink(destination, entry.linkName)
            entry.isLink -> deferredLinks += destination to entry.linkName
            entry.isFile -> {
                destination.parentFile?.mkdirs()
                destination.outputStream().buffered().use { output -> tar.copyTo(output) }
                // Only the executable bit matters: everything runs as one user.
                if (entry.mode and EXECUTE_BITS != 0) destination.setExecutable(true, false)
            }
            // Device nodes, FIFOs and sockets: /dev is bind-mounted from the
            // host, so recreating them here would be pointless and would fail.
            else -> Unit
        }
    }

    private fun writeSymbolicLink(destination: File, linkName: String) {
        destination.parentFile?.mkdirs()
        runCatching {
            Files.deleteIfExists(destination.toPath())
            Files.createSymbolicLink(destination.toPath(), Paths.get(linkName))
        }
    }

    /**
     * Refuses an entry that would land outside the target.
     *
     * The archive's checksum is verified before this runs, so this is not the
     * only guard — but a path in an archive is untrusted input, and `../` in a
     * tar entry is the oldest trick there is.
     */
    private fun resolve(root: File, name: String): File? {
        if (name.isBlank()) return null
        val candidate = File(root, name)
        val resolved = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        val rootPath = root.path + File.separator
        return if (resolved.path == root.path || resolved.path.startsWith(rootPath)) resolved else null
    }

    private companion object {
        /** Owner, group and other execute bits. */
        const val EXECUTE_BITS = 0b001_001_001

        /** Report every this many entries, not every entry. */
        const val ENTRY_REPORT_STEP = 200L
    }
}

/** Raised when an archive cannot be unpacked into a usable tree. */
internal class ExtractionException(message: String) : IOException(message)
