package dev.icode.mobile.data.storage

import dev.icode.mobile.data.io.deleteTree
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * iCode's own staging at the top of the workspaces (docs/workspace-storage-plan.md §4.2, §4.5): a
 * clone or an import is written into a folder named `.clone-<n>` or `.import-<n>` and renamed to
 * its workspace's name only when complete. No half workspace is ever listed, and one that stops
 * leaves nothing behind. A name starting with a dot is never a workspace.
 */
internal class WorkspaceStaging(private val root: File) {

    /** A staging folder for [kind] (`clone`, `import`) that nothing uses; the caller makes it. */
    fun reserve(kind: String): File {
        root.mkdirs()
        return generateSequence(System.currentTimeMillis()) { it + 1 }
            .map { File(root, ".$kind-$it") }
            .first { !it.exists() }
    }

    /** [staging], complete, becomes the workspace [name] in one rename; refused when that name was taken meanwhile. */
    fun complete(staging: File, name: String): File {
        val target = File(root, name)
        if (target.exists()) throw IOException("A workspace named $name was made meanwhile")
        Files.move(staging.toPath(), target.toPath())
        return target
    }

    /** A stopped or failed one: its folder goes, a link inside it never followed. */
    fun discard(staging: File) = deleteTree(staging)

    /** What a process that ended mid-clone or mid-import left behind; only iCode's own names are touched. */
    fun sweep() {
        root.listFiles().orEmpty().filter { NAME.matches(it.name) }.forEach { runCatching { deleteTree(it) } }
    }

    private companion object {
        val NAME = Regex("""\.(clone|import)-\d+""")
    }
}
