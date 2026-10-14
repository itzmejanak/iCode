package dev.icode.mobile.data.storage

import android.os.Environment
import dev.icode.mobile.domain.BrowsedItem
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Phone storage, the file explorer's Shared place (docs/storage-explorer-plan.md §4.2): the top of
 * internal storage, read with All Files Access and changed only by what the user does in the
 * explorer — a paste, a rename, a delete, a new folder or file. Nothing outside [root] is listed or
 * chosen, whatever a path or a link says.
 */
internal class PhoneStorage(
    /** Phone storage's top, canonical: `/storage/emulated/0`. */
    val root: File,
    /** All Files Access, as Android answers it now. */
    val hasAccess: () -> Boolean,
) {
    /** [path] is phone storage or in it, as written. */
    fun contains(path: String): Boolean = path == root.path || path.startsWith(root.path + "/")

    /**
     * What is in the folder at [path], as the explorer lists it: every entry, a folder with how many
     * it holds, a file with its size, each with when it changed. A link is listed as what it is,
     * never followed.
     */
    fun list(path: String): List<BrowsedItem> {
        val folder = folder(path) ?: throw IOException("This folder is no longer on phone storage")
        return folder.listFiles().orEmpty().map { child ->
            val isFolder = !Files.isSymbolicLink(child.toPath()) && child.isDirectory
            BrowsedItem(
                name = child.name,
                location = child.path,
                folder = isFolder,
                bytes = if (isFolder) null else child.length(),
                changedAt = child.lastModified(),
                entries = if (isFolder) child.list()?.size else null,
            )
        }
    }

    /** [path] as a readable folder here, canonical — a link is followed only to stay here; null otherwise. */
    fun folder(path: String): File? = runCatching {
        File(path).canonicalFile.takeIf { contains(it.path) && it.isDirectory && it.canRead() }
    }.getOrNull()

    companion object {
        fun of() = PhoneStorage(Environment.getExternalStorageDirectory().canonicalFile, Environment::isExternalStorageManager)
    }
}
