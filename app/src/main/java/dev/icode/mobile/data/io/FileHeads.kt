package dev.icode.mobile.data.io

import dev.icode.mobile.domain.FileType
import dev.icode.mobile.domain.FileTypes
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * What a file is, read where it lies (docs/file-kinds-icons-plan.md D2): by its name, and where the
 * name says too little — none known, or one two formats share — by its first bytes. Only those names
 * are opened; one that cannot be read is what its name says.
 */
internal object FileHeads {
    fun typeOf(file: File): FileType = typeOf(file.toPath(), file.name)

    fun typeOf(path: Path, name: String = path.fileName?.toString().orEmpty()): FileType =
        if (FileTypes.needsContent(name)) FileTypes.of(name, head(path)) else FileTypes.of(name)

    /** [path]'s first [FileTypes.HEAD_BYTES] bytes, fewer for a smaller file; none when it cannot be read. */
    fun head(path: Path): ByteArray = runCatching {
        Files.newInputStream(path).use { input ->
            val head = ByteArray(FileTypes.HEAD_BYTES)
            var read = 0
            while (read < head.size) {
                val count = input.read(head, read, head.size - read)
                if (count < 0) break
                read += count
            }
            head.copyOf(read)
        }
    }.getOrDefault(ByteArray(0))
}
