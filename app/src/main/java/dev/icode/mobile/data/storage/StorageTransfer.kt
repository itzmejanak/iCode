package dev.icode.mobile.data.storage

import dev.icode.mobile.data.io.deleteTree
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.Locale

/** One entry of what a transfer copies. */
internal data class TransferEntry(
    val id: String,
    val name: String,
    val folder: Boolean,
    /** Bytes; 0 for a folder. */
    val size: Long,
    /** Milliseconds; 0 when it is not known. */
    val modified: Long,
    /** False for what is not a file or folder of its own — a link, which is not followed. */
    val copyable: Boolean = true,
)

/** A file or folder a transfer reads, entry by entry; tests put a tree of their own in its place. */
internal interface TransferTree {
    val top: TransferEntry
    fun children(folder: TransferEntry): List<TransferEntry>
    fun open(file: TransferEntry): InputStream
}

/**
 * A file or folder on disk, read directly — phone storage's with All Files Access. A link is not
 * followed — its target may lie anywhere — so it is skipped and counted; a folder that cannot be
 * listed fails the transfer rather than leaving it short.
 */
internal class FileTransferTree(root: File) : TransferTree {
    override val top: TransferEntry = entryOf(root)

    override fun children(folder: TransferEntry): List<TransferEntry> =
        File(folder.id).listFiles()?.map(::entryOf) ?: throw IOException("${folder.name} could not be read")

    override fun open(file: TransferEntry): InputStream = FileInputStream(File(file.id))

    private fun entryOf(file: File): TransferEntry {
        val link = Files.isSymbolicLink(file.toPath())
        return TransferEntry(
            id = file.path,
            name = file.name,
            folder = !link && file.isDirectory,
            size = if (!link && file.isFile) file.length() else 0,
            modified = file.lastModified(),
            copyable = !link,
        )
    }
}

/**
 * A copy between iCode's storage and phone storage (docs/storage-explorer-plan.md §4.1), Import's
 * both ways: [plan] walks the item once — what will be copied, in order, its bytes, and what cannot
 * be — and [copy] writes exactly that, each entry's time kept. The source is only read.
 */
internal object StorageTransfer {
    data class Planned(val relative: String, val entry: TransferEntry)

    data class Plan(
        /** Each folder before what it holds; none for a file, which is the top itself. */
        val entries: List<Planned>,
        val bytes: Long,
        /** Paths not copied: a link, or a name the folder cannot hold. */
        val skipped: List<String>,
    )

    /**
     * [onEntry] runs for each entry listed; throwing from it stops the walk. [caseInsensitive]: the
     * copy goes to phone storage, where `A.txt` and `a.txt` are one name — the second is skipped,
     * never written over the first.
     */
    fun plan(tree: TransferTree, caseInsensitive: Boolean = false, onEntry: () -> Unit = {}): Plan {
        if (!tree.top.folder) return Plan(emptyList(), tree.top.size, emptyList())
        val entries = ArrayList<Planned>()
        val skipped = ArrayList<String>()
        var bytes = 0L
        // Depth first, without recursion: a deep node_modules is not a deep stack.
        val folders = ArrayDeque<Planned>().apply { add(Planned("", tree.top)) }
        while (folders.isNotEmpty()) {
            val folder = folders.removeLast()
            val names = HashSet<String>()
            val inside = ArrayList<Planned>()
            tree.children(folder.entry).sortedBy { it.name }.forEach { child ->
                onEntry()
                val relative = if (folder.relative.isEmpty()) child.name else "${folder.relative}/${child.name}"
                val key = if (caseInsensitive) child.name.lowercase(Locale.ROOT) else child.name
                // A name listed twice, or one a folder cannot hold, is never a second write over the first.
                if (!isPlainName(child.name) || !names.add(key) || !child.copyable) {
                    skipped += relative
                    return@forEach
                }
                val planned = Planned(relative, child)
                entries += planned
                if (child.folder) inside += planned else bytes += child.size
            }
            // Reversed onto the stack, so folders are walked in name order.
            inside.asReversed().forEach(folders::addLast)
        }
        return Plan(entries, bytes, skipped)
    }

    /**
     * Writes [plan] at [into], which must not exist yet: a file's copy, or a folder holding the
     * plan's entries. [onEntry] runs as each entry is done — the top itself, for a file, as "" —
     * and stops the copy by throwing. Stopped or failed, what it made is removed; what was at
     * [into] already is never touched. Folder times are set last: writing into a folder changes its time.
     */
    fun copy(tree: TransferTree, plan: Plan, into: File, onEntry: (relative: String) -> Unit) {
        val top = tree.top
        if (!top.folder) {
            Files.newOutputStream(into.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).close()
            made(into) {
                write(tree, top, into)
                onEntry("")
            }
            return
        }
        if (!into.mkdir()) throw IOException(if (into.exists()) "“${into.name}” is already there" else "${into.name} could not be made")
        made(into) {
            plan.entries.forEach { (relative, entry) ->
                val target = File(into, relative)
                if (entry.folder) {
                    if (!target.mkdir()) throw IOException("$relative could not be created")
                } else {
                    write(tree, entry, target)
                }
                onEntry(relative)
            }
            plan.entries.asReversed().filter { it.entry.folder && it.entry.modified > 0 }
                .forEach { (relative, entry) -> File(into, relative).setLastModified(entry.modified) }
            top.modified.takeIf { it > 0 }?.let(into::setLastModified)
        }
    }

    /** [work] on what this copy made at [top]; stopped or failed, it goes. */
    private inline fun made(top: File, work: () -> Unit) {
        try {
            work()
        } catch (stopped: Throwable) {
            runCatching { deleteTree(top) }
            throw stopped
        }
    }

    private fun write(tree: TransferTree, entry: TransferEntry, target: File) {
        tree.open(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        if (entry.modified > 0) target.setLastModified(entry.modified)
    }

    private fun isPlainName(name: String) = name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name
}
