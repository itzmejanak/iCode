package dev.icode.mobile.data.io

import dev.icode.mobile.domain.StorageMeasurement
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
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
fun measureTree(root: File, maxEntries: Int): StorageMeasurement {
    require(maxEntries > 0)
    if (!root.exists()) return StorageMeasurement(null)
    var bytes = 0L
    var visited = 0
    var outcome: StorageMeasurement? = null
    val walk = runCatching {
        Files.walkFileTree(
            root.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult =
                    count()

                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    if (attributes.isRegularFile) bytes += attributes.size()
                    return count()
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {
                    outcome = StorageMeasurement(null)
                    return FileVisitResult.TERMINATE
                }

                private fun count(): FileVisitResult {
                    // The root itself is not an entry of the tree.
                    if (++visited > maxEntries + 1) {
                        outcome = StorageMeasurement(null, truncated = true)
                        return FileVisitResult.TERMINATE
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }
    if (walk.isFailure) return StorageMeasurement(null)
    return outcome ?: StorageMeasurement(bytes)
}
