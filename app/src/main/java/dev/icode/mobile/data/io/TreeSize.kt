package dev.icode.mobile.data.io

import dev.icode.mobile.domain.StorageMeasurement
import java.io.File
import java.nio.file.Files

/**
 * Bytes held by the regular files under [root].
 *
 * Symbolic links are not followed, and sockets or pipes hold nothing. A
 * directory that cannot be listed makes the size unknown rather than quietly
 * smaller, and passing [maxEntries] marks it truncated: an incomplete walk
 * never passes for an exact size.
 */
fun measureTree(root: File, maxEntries: Int): StorageMeasurement {
    require(maxEntries > 0)
    if (!root.exists()) return StorageMeasurement(null)
    var bytes = 0L
    var visited = 0
    val pending = ArrayDeque(listOf(root))
    while (pending.isNotEmpty()) {
        val current = pending.removeFirst()
        val path = current.toPath()
        when {
            Files.isSymbolicLink(path) -> Unit
            Files.isRegularFile(path) -> bytes += current.length()
            Files.isDirectory(path) -> {
                val children = current.listFiles() ?: return StorageMeasurement(null)
                for (child in children) {
                    if (++visited > maxEntries) return StorageMeasurement(null, truncated = true)
                    pending.addLast(child)
                }
            }
        }
    }
    return StorageMeasurement(bytes)
}
