package dev.icode.mobile.data.storage

import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.domain.WorkspaceNameRules
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

    /**
     * Why [name] cannot be made in [into] now — a new workspace by default, or a project in a
     * workspace's folder; null when it can. Clone and Import ask before they start.
     */
    fun nameProblem(name: String, into: File = root): String? =
        if (into == root) WorkspaceNameRules.newWorkspaceError(name) { File(root, it).exists() }
        else WorkspaceNameRules.workspaceFolderValidationError(name)
            ?: name.trim().takeIf { File(into, it).exists() }?.let { "“$it” is already in ${into.name}" }

    /**
     * [staging], complete, becomes [name] in [into] — a new workspace by default, or a project in
     * a folder of one — in one rename; refused when the name was taken meanwhile, and anywhere but
     * a folder in the workspaces.
     */
    fun complete(staging: File, name: String, into: File = root): File {
        val folder = into.toPath()
        val base = root.toPath()
        if (!into.isDirectory || !folder.startsWith(base) || base.relativize(folder).any { it.toString() == ".git" }) {
            throw IOException("${into.name} is not a folder in the workspaces")
        }
        nameProblem(name, into)?.let { throw IOException(if (into == root) "A workspace named $name was made meanwhile" else it) }
        val target = File(into, name)
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
