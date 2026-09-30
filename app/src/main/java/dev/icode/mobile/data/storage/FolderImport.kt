package dev.icode.mobile.data.storage

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/** One entry of a folder chosen for Import. */
internal data class ImportEntry(
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

/** A folder chosen for Import, as its entries are read; tests put a folder of their own in its place. */
internal interface ImportTree {
    val top: ImportEntry
    fun children(folder: ImportEntry): List<ImportEntry>
    fun open(file: ImportEntry): InputStream
}

/**
 * A folder on phone storage, read directly with All Files Access. A link is not followed — its
 * target may lie anywhere — so it is skipped and counted; a folder that cannot be listed fails the
 * import rather than leaving it short.
 */
internal class FileImportTree(root: File) : ImportTree {
    override val top: ImportEntry = entryOf(root)

    override fun children(folder: ImportEntry): List<ImportEntry> =
        File(folder.id).listFiles()?.map(::entryOf) ?: throw IOException("${folder.name} could not be read")

    override fun open(file: ImportEntry): InputStream = FileInputStream(File(file.id))

    private fun entryOf(file: File): ImportEntry {
        val link = Files.isSymbolicLink(file.toPath())
        return ImportEntry(
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
 * Import's copy (docs/workspace-storage-plan.md §4.5): [plan] walks the picked folder once — what
 * will be copied, in order, its bytes, and what cannot be — and [copy] writes exactly that into
 * iCode's staging, each entry's time kept. The source is only read.
 */
internal object FolderImport {
    data class Planned(val relative: String, val entry: ImportEntry)

    data class Plan(
        /** Each folder before what it holds. */
        val entries: List<Planned>,
        val bytes: Long,
        /** Paths not copied: a link, or a name no folder can hold. */
        val skipped: List<String>,
    )

    /** [onEntry] runs for each entry listed; throwing from it stops the walk. */
    fun plan(tree: ImportTree, onEntry: () -> Unit = {}): Plan {
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
                // A name listed twice, or one a folder cannot hold, is never a second write over the first.
                if (!isPlainName(child.name) || !names.add(child.name) || !child.copyable) {
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
     * Writes [plan] into [into], which must not exist yet; [onEntry] runs as each entry is done and
     * stops the copy by throwing. Folder times are set last: writing into a folder changes its time.
     */
    fun copy(tree: ImportTree, plan: Plan, into: File, onEntry: (relative: String) -> Unit) {
        if (!into.mkdir()) throw IOException("The import could not be started")
        plan.entries.forEach { (relative, entry) ->
            val target = File(into, relative)
            if (entry.folder) {
                if (!target.mkdir()) throw IOException("$relative could not be created")
            } else {
                tree.open(entry).use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
                if (entry.modified > 0) target.setLastModified(entry.modified)
            }
            onEntry(relative)
        }
        plan.entries.asReversed().filter { it.entry.folder && it.entry.modified > 0 }
            .forEach { (relative, entry) -> File(into, relative).setLastModified(entry.modified) }
        tree.top.modified.takeIf { it > 0 }?.let(into::setLastModified)
    }

    private fun isPlainName(name: String) = name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name
}
