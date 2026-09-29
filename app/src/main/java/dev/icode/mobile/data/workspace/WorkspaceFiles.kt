package dev.icode.mobile.data.workspace

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Rename, move, copy and delete inside one workspace (S3). Every path is
 * checked against [root] first: nothing outside it, never the root itself,
 * never Git's own `.git`. Nothing is overwritten, and links are never followed —
 * a link is moved, copied or deleted as the link it is.
 */
internal class WorkspaceFiles(root: File) {
    private val root: Path = root.canonicalFile.toPath()

    fun rename(location: String, newName: String): File {
        val source = item(location)
        val target = source.resolveSibling(newName)
        requireInside(target)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw IOException("“$newName” already exists here")
        return Files.move(source, target).toFile()
    }

    fun move(location: String, destinationFolder: String): File {
        val source = item(location)
        val destination = folder(destinationFolder)
        if (destination == source.parent) return source.toFile()
        if (destination.startsWith(source)) throw IOException("A folder cannot be moved into itself")
        val target = destination.resolve(source.fileName)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("“${source.fileName}” already exists in ${root.relativize(destination).display()}")
        }
        return Files.move(source, target).toFile()
    }

    /** How many entries [location] holds, itself included: what a copy or delete of it touches. */
    fun count(location: String): Long {
        var entries = 0L
        Files.walkFileTree(
            item(location),
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

    /**
     * Into [destinationFolder], as “name copy” (then “name copy 2”…) when the name is taken,
     * reporting each entry as a path from the folder the source is in. A copy that stops —
     * failed, or cancelled from [onEntry] — removes what it made: nothing is left half-copied.
     */
    fun copy(location: String, destinationFolder: String, onEntry: (String) -> Unit = {}): File {
        val source = item(location)
        val destination = folder(destinationFolder)
        if (destination.startsWith(source)) throw IOException("A folder cannot be copied into itself")
        val target = freeName(destination, source.fileName.toString(), Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
        val shown = source.parent
        try {
            Files.walkFileTree(
                source,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                        Files.createDirectory(target.resolve(source.relativize(dir).toString()))
                        onEntry(shown.relativize(dir).toString())
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                        Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
                        onEntry(shown.relativize(file).toString())
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (stopped: Throwable) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) runCatching { removeTree(target) }
            throw stopped
        }
        return target.toFile()
    }

    /** Deletes [location] and everything in it, reporting each entry as a path from the folder it is in. */
    fun delete(location: String, onEntry: (String) -> Unit = {}) {
        val source = item(location)
        removeTree(source, onEntry = { onEntry(source.parent.relativize(it).toString()) })
    }

    private fun removeTree(top: Path, onEntry: (Path) -> Unit = {}) {
        Files.walkFileTree(
            top,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    onEntry(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                    if (error != null) throw error
                    Files.delete(dir)
                    onEntry(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    /** An existing entry of the workspace that may be changed: not the root, not Git's. */
    private fun item(location: String): Path {
        val path = absolute(location)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw IOException("It no longer exists")
        if (path == root) throw IOException("The workspace folder itself cannot be changed here")
        requireInside(path)
        return path
    }

    private fun folder(location: String): Path {
        val path = absolute(location)
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw IOException("Choose a folder in this workspace")
        if (path != root) requireInside(path)
        return path
    }

    /**
     * The parent is resolved to its real location, the name kept as given: a
     * link stays a link, and `..` cannot walk out of the workspace.
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
        if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) return candidate
        val dot = name.lastIndexOf('.').takeIf { !isDirectory && it > 0 } ?: name.length
        val stem = name.substring(0, dot)
        val extension = name.substring(dot)
        return generateSequence(1) { it + 1 }
            .map { number -> folder.resolve(if (number == 1) "$stem copy$extension" else "$stem copy $number$extension") }
            .first { !Files.exists(it, LinkOption.NOFOLLOW_LINKS) }
    }

    private fun Path.display(): String = toString().ifEmpty { "the workspace" }
}

/** This location is [path] itself or lies inside it. */
internal fun String.isAtOrUnder(path: String): Boolean = this == path || startsWith("$path/")

/** Where this location is after [from] became [to]; unchanged when it was not under [from]. */
internal fun String.relocated(from: String, to: String): String =
    if (isAtOrUnder(from)) to + removePrefix(from) else this
