package dev.icode.mobile.data.storage

import dev.icode.mobile.domain.LogicalWorkspace
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** One entry of a logical folder: its [location] as iCode names it, and where its [bytes] are. */
internal data class LogicalEntry(val name: String, val location: String, val bytes: File)

/**
 * iCode's own side of the projection (docs/workspace-storage-plan.md §8). The Explorer, the editor
 * and the file operations name an entry by its path under the user's folder, as they always have —
 * so tabs, recents, drafts, Git's status and search results keep their locations — and ask here
 * where its bytes are. The runtime sees the same tree through [LogicalWorkspace.binds].
 */
internal class LogicalFiles(val workspace: LogicalWorkspace) {
    private val root = workspace.userRoot.trimEnd('/')

    /** [location]'s path from the workspace root ("" for the root itself), or null outside it. */
    fun relative(location: String): String? {
        val path = location.trimEnd('/')
        return when {
            path == root -> ""
            path.startsWith("$root/") -> path.substring(root.length + 1)
            else -> null
        }
    }

    /** Where [location]'s bytes are; a location outside the workspace is its own. */
    fun bytes(location: String): File = relative(location)?.let { File(workspace.resolve(it).path) } ?: File(location)

    /**
     * The folder at [location] as one tree: its own entries and each mount directly inside it. A
     * name is where the resolver puts it, so a mount shows its private folder, never the empty
     * placeholder the runtime leaves in the user's folder. A link is listed as the link it is.
     */
    fun list(location: String): List<LogicalEntry> {
        val folder = relative(location)
        val own = bytes(location)
        val listed = own.list().orEmpty().toSet()
        val names = listed.toSortedSet()
        if (folder != null) workspace.mountsIn(folder).mapTo(names) { it.path.substringAfterLast('/') }
        return names.mapNotNull { name ->
            val child = "${location.trimEnd('/')}/$name"
            val bytes = bytes(child)
            // Listed where the resolver puts it, it is there; sent elsewhere, it may not be.
            val there = (name in listed && bytes == File(own, name)) || Files.exists(bytes.toPath(), LinkOption.NOFOLLOW_LINKS)
            LogicalEntry(name, child, bytes).takeIf { there }
        }
    }

    /** [location] lies in private storage: a mount, or inside one. */
    fun isPrivate(location: String): Boolean = relative(location)?.let(workspace::mountOf) != null
}
