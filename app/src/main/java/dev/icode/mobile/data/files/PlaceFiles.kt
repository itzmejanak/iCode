package dev.icode.mobile.data.files

import dev.icode.mobile.data.io.deleteTree
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Make, rename, move, copy and delete in one place (docs/storage-explorer-plan.md §4.2): Private,
 * rooted at the workspaces' folder — a workspace is an item at its top — or phone storage, Shared,
 * where [gitOwnsDotGit] is false: a `.git` there is the user's data like any other. Every path is
 * checked against [root] first: nothing outside it, never the root itself, and in Private never
 * Git's own `.git`. Nothing is overwritten, and links are never followed — a link is moved, copied
 * or deleted as the link it is.
 */
internal class PlaceFiles(root: File, val gitOwnsDotGit: Boolean) {
    private val root: Path = root.canonicalFile.toPath()

    /** A new, empty folder ([isFolder]) or file named [name] in [parentLocation]; refused when the name is taken. */
    fun create(parentLocation: String, name: String, isFolder: Boolean): File {
        val parent = folder(parentLocation)
        val target = parent.resolve(name)
        requireInside(target)
        if (target.parent != parent) throw IOException("Invalid ${if (isFolder) "folder" else "file"} destination")
        return try {
            (if (isFolder) Files.createDirectory(target) else Files.createFile(target)).toFile()
        } catch (taken: FileAlreadyExistsException) {
            throw IOException("A file or folder with this name already exists")
        }
    }

    fun rename(location: String, newName: String): File {
        val source = item(location)
        val target = source.resolveSibling(newName)
        requireInside(target)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw IOException("“$newName” already exists here")
        return Files.move(source, target).toFile()
    }

    /**
     * [location] moved to [target], exactly, in one step; refused when [target] is taken. Its folder
     * is made when it is gone — Undo puts things back where folders have since gone.
     */
    fun move(location: String, target: String) {
        val source = item(location)
        val destination = absolute(target)
        requireInside(destination)
        if (destination.startsWith(source)) throw IOException("A folder cannot be moved into itself")
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw IOException("“${destination.fileName}” is already there")
        Files.createDirectories(destination.parent)
        Files.move(source, destination)
    }

    /** How many entries [location] holds, itself included: what a copy or delete of it touches. */
    fun count(location: String): Long = countEntries(item(location))

    /**
     * [location] copied as [target], exactly; refused when [target] is taken. Each entry is reported
     * as a path from the folder the source is in. A copy that stops — failed, or cancelled from
     * [onEntry] — removes what it made: nothing is left half-copied.
     */
    fun copy(location: String, target: String, onEntry: (String) -> Unit = {}) {
        val source = item(location)
        val destination = absolute(target)
        requireInside(destination)
        if (destination.startsWith(source)) throw IOException("A folder cannot be copied into itself")
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw IOException("“${destination.fileName}” is already there")
        val shown = source.parent
        try {
            Files.walkFileTree(
                source,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                        Files.createDirectory(destination.resolve(source.relativize(dir).toString()))
                        onEntry(shown.relativize(dir).toString())
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                        Files.copy(file, destination.resolve(source.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
                        onEntry(shown.relativize(file).toString())
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (stopped: Throwable) {
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) runCatching { deleteTree(destination) }
            throw stopped
        }
    }

    /** Deletes [location] and everything in it, reporting each entry as a path from the folder it is in. */
    fun delete(location: String, onEntry: (String) -> Unit = {}) {
        val source = item(location)
        deleteTree(source, onEntry = { onEntry(source.parent.relativize(it).toString()) })
    }

    /** Why [location] cannot be changed here — outside, the place itself, or Git's — or null when it can. */
    fun refusal(location: String): String? = runCatching { requireInside(absolute(location)) }.exceptionOrNull()?.message

    /** Why nothing can be put in the folder [location] — not a folder, outside, or Git's — or null when it can. */
    fun folderRefusal(location: String): String? = runCatching { folder(location) }.exceptionOrNull()?.message

    /** An existing entry of the place that may be changed: not the root, not Git's. */
    private fun item(location: String): Path {
        val path = absolute(location)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw IOException("${path.fileName} no longer exists")
        requireInside(path)
        return path
    }

    private fun folder(location: String): Path {
        val path = absolute(location)
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw IOException("Choose a folder here")
        if (path != root) requireInside(path)
        return path
    }

    /**
     * The parent is resolved to its real location, the name kept as given: a link stays a link, and
     * `..` cannot walk out of the place.
     */
    private fun absolute(location: String): Path {
        val file = File(location)
        val parent = file.absoluteFile.parentFile ?: return root
        return parent.canonicalFile.toPath().resolve(file.name)
    }

    private fun requireInside(path: Path) {
        if (path == root) throw IOException("This folder itself cannot be changed here")
        if (!path.startsWith(root)) throw IOException("Only items in ${if (gitOwnsDotGit) "iCode's storage" else "phone storage"} can be changed here")
        if (gitOwnsDotGit && root.relativize(path).any { it.toString() == ".git" }) throw IOException("The .git folder belongs to Git")
    }
}

/**
 * [name] in [folder], or when it is taken “name copy”, then “name copy 2”… — a file's extension kept
 * last. On phone storage `Files.exists` answers as its filesystem does, ignoring case.
 */
internal fun freeName(folder: Path, name: String, isDirectory: Boolean): Path {
    val candidate = folder.resolve(name)
    if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) return candidate
    val dot = name.lastIndexOf('.').takeIf { !isDirectory && it > 0 } ?: name.length
    val stem = name.substring(0, dot)
    val extension = name.substring(dot)
    return generateSequence(1) { it + 1 }
        .map { number -> folder.resolve(if (number == 1) "$stem copy$extension" else "$stem copy $number$extension") }
        .first { !Files.exists(it, LinkOption.NOFOLLOW_LINKS) }
}

/** How many entries [top] holds, itself included, a link counted as itself. */
internal fun countEntries(top: Path): Long {
    var entries = 0L
    Files.walkFileTree(
        top,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                entries++
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                entries++
                return FileVisitResult.CONTINUE
            }
        },
    )
    return entries
}

/** Something is at [location] — a link, whatever it points to, included. */
internal fun fileExists(location: String): Boolean = Files.exists(File(location).toPath(), LinkOption.NOFOLLOW_LINKS)
