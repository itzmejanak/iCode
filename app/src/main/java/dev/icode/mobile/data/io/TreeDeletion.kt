package dev.icode.mobile.data.io

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Deletes [top] and everything in it without following a link: a link is deleted as the link it
 * is, never what it points to. The one recursive delete in iCode — the runtime, a workspace and a
 * staging folder all sit in its storage side by side, and `File.deleteRecursively` follows a link
 * to a folder (measured: a relative link in the runtime's tree took a workspace's file with it).
 * [onEntry] sees each entry once it is gone, a folder after its contents.
 */
internal fun deleteTree(top: Path, onEntry: (Path) -> Unit = {}) {
    if (!Files.exists(top, LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(top, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
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
    })
}

internal fun deleteTree(top: File) = deleteTree(top.toPath())
