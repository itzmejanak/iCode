package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.StorageMeasurement
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Bounded, symlink-safe measurements; an incomplete walk never masquerades as an exact size. */
internal object StorageInspector {
    fun measure(root: File, maxEntries: Int = 100_000): StorageMeasurement {
        require(maxEntries > 0)
        if (!root.exists()) return StorageMeasurement(null)
        var bytes = 0L
        var visited = 0
        val pending = ArrayDeque(listOf(root))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (Files.isSymbolicLink(current.toPath())) continue
            if (current.isFile) {
                bytes += current.length()
            } else if (current.isDirectory) {
                val children = current.listFiles() ?: return StorageMeasurement(null)
                for (child in children) {
                    if (++visited > maxEntries) return StorageMeasurement(null, truncated = true)
                    pending.addLast(child)
                }
            } else {
                return StorageMeasurement(null)
            }
        }
        return StorageMeasurement(bytes)
    }

    /** Only installer archives and PRoot temporary children are cache-cleanup targets. */
    fun clearRuntimeCache(cacheDir: File, prootTemp: File): Int {
        val cacheRoot = cacheDir.canonicalFile
        require(prootTemp.canonicalFile.parentFile == cacheRoot)
        val targets = cacheRoot.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".tar.gz") } + prootTemp.listFiles().orEmpty()
        var removed = 0
        targets.forEach { target ->
            if (!target.canonicalFile.toPath().startsWith(cacheRoot.toPath()) ||
                Files.isSymbolicLink(target.toPath())) return@forEach
            Files.walkFileTree(target.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                    if (error != null) throw error
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            })
            removed++
        }
        return removed
    }
}
