package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.storage.LogicalFiles
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Rename, move, copy and delete inside one workspace (S3), over its logical tree (S4,
 * docs/workspace-storage-plan.md §8): an item is named as the workspace names it and changed where
 * its bytes are. Every path is checked against the root first: nothing outside it, never the root
 * itself, never Git's own `.git`. Nothing is overwritten, and links are never followed — a link is
 * moved, copied or deleted as the link it is. Across the storage boundary the kernel refuses a
 * rename (EXDEV), so a move there is a copy then a delete. A mount is its path: it, and a folder
 * holding one, stays where it is.
 */
internal class WorkspaceFiles(private val tree: LogicalFiles) {
    private val root: Path = File(tree.workspace.userRoot).canonicalFile.toPath()

    fun rename(location: String, newName: String): File {
        val source = movable(location)
        val target = source.resolveSibling(newName)
        requireInside(target)
        if (exists(target)) throw IOException("“$newName” already exists here")
        Files.move(bytes(source), bytes(target))
        return target.toFile()
    }

    /** Moving [location] into [destinationFolder] crosses the storage boundary: a copy then a delete, which takes long. */
    fun crossesStorage(location: String, destinationFolder: String): Boolean {
        val source = absolute(location)
        return crosses(source, absolute(destinationFolder).resolve(source.fileName.toString()))
    }

    /** Across the storage boundary, each entry is reported twice: copied, then deleted. */
    fun move(location: String, destinationFolder: String, onEntry: (String) -> Unit = {}): File {
        val source = movable(location)
        val destination = folder(destinationFolder)
        if (destination == source.parent) return source.toFile()
        if (destination.startsWith(source)) throw IOException("A folder cannot be moved into itself")
        val target = destination.resolve(source.fileName.toString())
        if (exists(target)) {
            throw IOException("“${source.fileName}” already exists in ${root.relativize(destination).display()}")
        }
        if (crosses(source, target)) {
            copyTree(source, target, onEntry)
            removeTree(source) { onEntry(source.parent.relativize(it).toString()) }
        } else {
            Files.move(bytes(source), bytes(target))
        }
        return target.toFile()
    }

    /** How many entries [location] holds, itself included: what a copy or delete of it touches. */
    fun count(location: String): Long {
        var entries = 0L
        walk(item(location), before = { _, _ -> entries++ })
        return entries
    }

    /**
     * Into [destinationFolder], as “name copy” (then “name copy 2”…) when the name is taken,
     * reporting each entry as a path from the folder the source is in. A copy that stops —
     * failed, or cancelled from [onEntry] — removes what it made: nothing is left half-copied.
     */
    fun copy(location: String, destinationFolder: String, onEntry: (String) -> Unit = {}): File {
        val source = item(location)
        val destination = folder(destinationFolder)
        if (destination.startsWith(source)) throw IOException("A folder cannot be copied into itself")
        val target = freeName(destination, source.fileName.toString(), isFolder(source))
        copyTree(source, target, onEntry)
        return target.toFile()
    }

    /** Deletes [location] and everything in it, reporting each entry as a path from the folder it is in. */
    fun delete(location: String, onEntry: (String) -> Unit = {}) {
        val source = item(location)
        removeTree(source, onEntry = { onEntry(source.parent.relativize(it).toString()) })
    }

    private fun copyTree(source: Path, target: Path, onEntry: (String) -> Unit) {
        val made = mutableListOf<Path>()
        try {
            walk(source, before = { entry, folder ->
                val copy = target.resolve(source.relativize(entry).toString())
                when {
                    // A mount inside the copy is already there, kept by iCode: it is filled, not made.
                    folder && isMount(copy) && isFolder(copy) -> Unit
                    folder -> Files.createDirectory(bytes(copy)).also { made.add(copy) }
                    else -> Files.copy(bytes(entry), bytes(copy), StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
                        .also { made.add(copy) }
                }
                onEntry(source.parent.relativize(entry).toString())
            })
        } catch (stopped: Throwable) {
            made.asReversed().forEach { runCatching { Files.deleteIfExists(bytes(it)) } }
            throw stopped
        }
    }

    private fun removeTree(top: Path, onEntry: (Path) -> Unit = {}) {
        walk(top, after = { entry, folder ->
            if (folder) removePlaceholders(entry)
            Files.delete(bytes(entry))
            onEntry(entry)
        })
    }

    /**
     * The runtime leaves an empty folder in [folder]'s own storage where each mount inside it is
     * placed; it goes with the folder. One that holds something is the user's, never deleted unseen.
     */
    private fun removePlaceholders(folder: Path) {
        tree.workspace.mountsIn(root.relativize(folder).toString()).forEach { mount ->
            val placeholder = bytes(folder).resolve(mount.path.substringAfterLast('/'))
            if (Files.isDirectory(placeholder, LinkOption.NOFOLLOW_LINKS)) Files.delete(placeholder)
        }
    }

    /**
     * [top] and, when it is a folder, everything in it as the logical tree has it: [before] sees a
     * folder before its entries, [after] after them. A link is never walked into.
     */
    private fun walk(
        top: Path,
        before: (Path, Boolean) -> Unit = { _, _ -> },
        after: (Path, Boolean) -> Unit = { _, _ -> },
    ) {
        val folder = isFolder(top)
        before(top, folder)
        if (folder) tree.list(top.toString()).forEach { entry -> walk(top.resolve(entry.name), before, after) }
        after(top, folder)
    }

    /** An existing entry of the workspace that may be changed: not the root, not Git's. */
    private fun item(location: String): Path {
        val path = absolute(location)
        if (!exists(path)) throw IOException("It no longer exists")
        if (path == root) throw IOException("The workspace folder itself cannot be changed here")
        requireInside(path)
        return path
    }

    /** An item that may change place: not a mount, nor a folder holding one. */
    private fun movable(location: String): Path {
        val path = item(location)
        val mount = tree.workspace.mountsAtOrUnder(root.relativize(path).toString()).firstOrNull() ?: return path
        val kept = if (root.resolve(mount.path) == path) "“${path.fileName}” is" else "“${path.fileName}” holds ${mount.path}, which is"
        throw IOException("$kept kept in iCode's storage for this project, so it stays where it is")
    }

    private fun folder(location: String): Path {
        val path = absolute(location)
        if (!isFolder(path)) throw IOException("Choose a folder in this workspace")
        if (path != root) requireInside(path)
        return path
    }

    /**
     * The parent is resolved to its real location, the name kept as given: a
     * link stays a link, and `..` cannot walk out of the workspace. This is the
     * user's side of the tree, which holds no links below the root.
     */
    private fun absolute(location: String): Path {
        val file = File(location)
        val parent = file.absoluteFile.parentFile ?: return root
        return parent.canonicalFile.toPath().resolve(file.name)
    }

    private fun requireInside(path: Path) {
        if (!path.startsWith(root) || path == root) throw IOException("Only items inside this workspace can be changed")
        if (root.relativize(path).any { it.toString() == ".git" }) throw IOException("The .git folder belongs to Git")
    }

    private fun freeName(folder: Path, name: String, isDirectory: Boolean): Path {
        val candidate = folder.resolve(name)
        if (!exists(candidate)) return candidate
        val dot = name.lastIndexOf('.').takeIf { !isDirectory && it > 0 } ?: name.length
        val stem = name.substring(0, dot)
        val extension = name.substring(dot)
        return generateSequence(1) { it + 1 }
            .map { number -> folder.resolve(if (number == 1) "$stem copy$extension" else "$stem copy $number$extension") }
            .first { !exists(it) }
    }

    /** Where [path]'s bytes are. */
    private fun bytes(path: Path): Path = tree.bytes(path.toString()).toPath()

    private fun exists(path: Path) = Files.exists(bytes(path), LinkOption.NOFOLLOW_LINKS)

    private fun isFolder(path: Path) = Files.isDirectory(bytes(path), LinkOption.NOFOLLOW_LINKS)

    private fun isMount(path: Path) = tree.workspace.mounts.any { root.resolve(it.path) == path }

    /** One side in the user's folder and the other in private storage: two filesystems. */
    private fun crosses(source: Path, target: Path) = tree.isPrivate(source.toString()) != tree.isPrivate(target.toString())

    private fun Path.display(): String = toString().ifEmpty { "the workspace" }
}

/** This location is [path] itself or lies inside it. */
internal fun String.isAtOrUnder(path: String): Boolean = this == path || startsWith("$path/")

/** Where this location is after [from] became [to]; unchanged when it was not under [from]. */
internal fun String.relocated(from: String, to: String): String =
    if (isAtOrUnder(from)) to + removePrefix(from) else this
