package dev.icode.mobile.data.io

import dev.icode.mobile.domain.StorageMeasurement
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Bytes held by the regular files under [root].
 *
 * Symbolic links are not followed, and sockets or pipes hold nothing. A
 * directory that cannot be listed makes the size unknown rather than quietly
 * smaller, and passing [maxEntries] marks it truncated: an incomplete walk
 * never passes for an exact size. One file-system call per entry — a runtime
 * holds a hundred thousand of them.
 */
fun measureTree(root: File, maxEntries: Int): StorageMeasurement = tallyTree(root, maxEntries).let { StorageMeasurement(it.bytes, it.truncated) }

/**
 * What [root] holds: its regular files' bytes — null when a folder in it could not be read, or the
 * walk stopped short — and how many files (links among them) and folders lie under it. The walk is
 * [measureTree]'s: no link followed, [maxEntries] at most, and stopped once [keepGoing] says so.
 */
class TreeTally(val bytes: Long?, val files: Long, val folders: Long, val truncated: Boolean = false)

fun tallyTree(root: File, maxEntries: Int = Int.MAX_VALUE, keepGoing: () -> Boolean = { true }): TreeTally {
    require(maxEntries > 0)
    if (!Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS)) return TreeTally(null, 0, 0)
    var bytes = 0L
    var files = 0L
    var folders = 0L
    var visited = 0L
    var unreadable = false
    var truncated = false
    val walk = runCatching {
        Files.walkFileTree(
            root.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                    if (dir != root.toPath()) folders++
                    return count()
                }

                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    if (attributes.isRegularFile) bytes += attributes.size()
                    if (file != root.toPath()) files++
                    return count()
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {
                    unreadable = true
                    return FileVisitResult.TERMINATE
                }

                private fun count(): FileVisitResult {
                    // The root itself is not an entry of the tree.
                    if (++visited > maxEntries + 1L || !keepGoing()) {
                        truncated = true
                        return FileVisitResult.TERMINATE
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }
    val known = walk.isSuccess && !unreadable && !truncated
    return TreeTally(bytes.takeIf { known }, files, folders, truncated)
}
