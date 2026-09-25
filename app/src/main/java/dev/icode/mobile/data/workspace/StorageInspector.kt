package dev.icode.mobile.data.workspace

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Bounded, symlink-safe measurements; an incomplete walk never masquerades as an exact size. */
internal object StorageInspector {
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
