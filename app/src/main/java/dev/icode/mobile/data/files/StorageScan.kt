package dev.icode.mobile.data.files

import dev.icode.mobile.data.io.DigestAlgorithm
import dev.icode.mobile.data.io.FileHeads
import dev.icode.mobile.data.io.isAtOrUnder
import dev.icode.mobile.data.io.tallyTree
import dev.icode.mobile.data.io.toHex
import dev.icode.mobile.domain.files.DuplicateGroup
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.ScanScope
import dev.icode.mobile.domain.files.ScannedFile
import dev.icode.mobile.domain.files.StorageInsights
import dev.icode.mobile.domain.files.StorageOverview
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.cancellation.CancellationException

/**
 * Manage storage's walk (docs/explorer-gaps-plan.md §3.10): every file where it looks, by its
 * category — no link followed, the hidden-files setting not applied. `.git` is in no list: its space
 * is Git's history, apart. Trash's folders are left out: Trash has its own row, and Shared's lies
 * inside phone storage. Then the copies among the files: those of one size are hashed, a first block
 * and then the whole. What it found is kept while iCode runs and follows iCode's own changes; copies
 * among what arrived since are found on the next scan.
 */
internal class StorageScan(private val places: FilePlaces, private val trash: FileTrash, private val clock: () -> Long = System::currentTimeMillis) {
    /** One scan's findings: its files, each `.git` with its size, and the copies among them. */
    class Found(
        val scope: ScanScope,
        val files: List<ScannedFile>,
        val gits: Map<String, Long>,
        val duplicates: List<DuplicateGroup>,
        val scannedAt: Long,
        val sharedUnread: Boolean,
    )

    @Volatile var found: Found? = null
        private set

    fun overview(): StorageOverview? = found?.let { StorageInsights.overview(it.scope, it.files, it.gits.values.sum(), it.duplicates, it.scannedAt, it.sharedUnread) }

    /** Where [scope] looks, its files counted to [onCount] as it goes; stopped, by a CancellationException, once [keepGoing] says so. */
    fun scan(scope: ScanScope, keepGoing: () -> Boolean, onCount: (Long) -> Unit): Found {
        val walk = Walk(keepGoing, onCount)
        roots(scope).forEach(walk::into)
        val duplicates = duplicates(walk.files, keepGoing)
        val sharedUnread = scope != ScanScope.Private && !places.storage.hasAccess()
        return Found(scope, walk.files, walk.gits, duplicates, clock(), sharedUnread).also { synchronized(this) { found = it } }
    }

    /** iCode's own [steps]: what went leaves, what moved follows, and what arrived where the scan looked is looked at. */
    fun follow(steps: List<FileStep>) = synchronized(this) {
        val before = found ?: return@synchronized
        val roots = roots(before.scope)
        fun looked(location: String) = roots.any { location.isAtOrUnder(it.path) } && !trash.isTrash(location)
        val arrived = Walk({ true }, {})
        steps.mapNotNull { step ->
            when (step) {
                is FileStep.Made -> step.location
                is FileStep.Restored -> step.to
                is FileStep.Moved -> step.to.takeUnless { looked(step.from) }
                is FileStep.Trashed, is FileStep.Removed -> null
            }
        }.filter(::looked).forEach { arrived.into(File(it)) }
        found = Found(
            before.scope,
            before.files.mapNotNull { StorageInsights.after(it, steps) }.filter { looked(it.location) } + arrived.files,
            before.gits.mapNotNull { (location, bytes) -> StorageInsights.after(location, steps)?.takeIf(::looked)?.let { it to bytes } }.toMap() + arrived.gits,
            before.duplicates.mapNotNull { StorageInsights.after(it, steps) },
            before.scannedAt,
            before.sharedUnread,
        )
    }

    /** Where [scope] looks: Private's workspaces, phone storage — once iCode may read it — or both. */
    private fun roots(scope: ScanScope): List<File> = listOfNotNull(
        places.workspaces.takeIf { scope != ScanScope.Shared },
        places.storage.root.takeIf { scope != ScanScope.Private && places.storage.hasAccess() },
    )

    /** One walk's files and `.git` folders, links never followed; a folder that cannot be read holds nothing it counts. */
    private inner class Walk(private val keepGoing: () -> Boolean, private val onCount: (Long) -> Unit) {
        val files = mutableListOf<ScannedFile>()
        val gits = mutableMapOf<String, Long>()

        fun into(top: File) {
            if (!Files.exists(top.toPath(), LinkOption.NOFOLLOW_LINKS)) return
            Files.walkFileTree(
                top.toPath(),
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                        if (!keepGoing()) throw CancellationException("Stopped")
                        if (trash.isTrash(dir.toString())) return FileVisitResult.SKIP_SUBTREE
                        if (dir.fileName?.toString() == ".git") {
                            gits[dir.toString()] = tallyTree(dir.toFile()).bytes ?: 0L
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                        if (!attributes.isRegularFile) return FileVisitResult.CONTINUE
                        val name = file.fileName.toString()
                        // Its kind by its name; its first bytes read only where the name cannot say (`.ts`, none).
                        files += ScannedFile(file.toString(), attributes.size(), attributes.lastModifiedTime().toMillis(), FileHeads.typeOf(file, name))
                        if (files.size % REPORT_EVERY == 0) onCount(files.size.toLong())
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, error: IOException): FileVisitResult = FileVisitResult.CONTINUE
                },
            )
            onCount(files.size.toLong())
        }
    }

    /** Files with the same contents: of one size, then of one first block, then whole, the oldest first; the largest waste first. */
    private fun duplicates(files: List<ScannedFile>, keepGoing: () -> Boolean): List<DuplicateGroup> =
        StorageInsights.sameSize(files)
            .flatMap { same -> same.groupBy { digest(it, FIRST_BLOCK, keepGoing) }.values.filter { it.size > 1 } }
            .flatMap { first -> if (first.first().bytes <= FIRST_BLOCK) listOf(first) else first.groupBy { digest(it, null, keepGoing) }.values.filter { it.size > 1 } }
            .map { group -> group.sortedBy { it.changedAt }.let { DuplicateGroup(it.first().bytes, it.map(ScannedFile::location)) } }
            .sortedByDescending { it.extraBytes }

    /** SHA-256 of [file]'s first [limit] bytes, or all of it; a file that cannot be read is like no other. */
    private fun digest(file: ScannedFile, limit: Int?, keepGoing: () -> Boolean): String {
        if (!keepGoing()) throw CancellationException("Stopped")
        return runCatching {
            val digest = DigestAlgorithm.Sha256.newDigest()
            File(file.location).inputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                var left = limit?.toLong() ?: Long.MAX_VALUE
                while (left > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    left -= read
                }
            }
            digest.digest().toHex()
        }.getOrElse { "unreadable ${file.location}" }
    }

    private companion object {
        const val REPORT_EVERY = 500
        const val FIRST_BLOCK = 4096
        const val BUFFER = 64 * 1024
    }
}
