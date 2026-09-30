package dev.icode.mobile.data.storage

import android.os.Environment
import dev.icode.mobile.domain.ImportBrowserState
import dev.icode.mobile.domain.PhoneFolder
import dev.icode.mobile.domain.PhoneStoragePaths
import java.io.File
import java.io.IOException

/**
 * Phone storage as Import reads it (docs/workspace-storage-plan.md §4.5): the folders under the root
 * of internal storage, listed and read with All Files Access, never written. Nothing outside [root]
 * is listed or chosen, whatever a path or a link says.
 */
internal class PhoneStorage(
    /** Phone storage's root (sdcard), canonical: `/storage/emulated/0`. */
    val root: File,
    /** All Files Access, as Android answers it now. */
    val hasAccess: () -> Boolean,
) {
    /**
     * What the Import browser shows at [path]. Without access it lists nothing and waits there;
     * with access, [path] must be a folder here.
     */
    fun browse(path: String): ImportBrowserState {
        val lexical = File(path).takeIf { it.isAbsolute && within(it) } ?: root
        if (!hasAccess()) return ImportBrowserState(false, root.path, lexical.path)
        val folder = folder(path) ?: throw IOException("This folder is no longer on phone storage")
        val folders = folder.listFiles().orEmpty()
            .filter { !it.name.startsWith('.') && it.isDirectory && folder(it.path) != null }
            .map { child ->
                val children = child.listFiles()
                PhoneFolder(child.name, child.path, children?.count { it.isDirectory } ?: 0, children?.count { it.isFile } ?: 0)
            }
            .sortedBy { it.name.lowercase() }
        return ImportBrowserState(true, root.path, folder.path, folders)
    }

    /** [path] as a readable folder here, canonical — a link is followed only to stay here; null otherwise. */
    fun folder(path: String): File? = runCatching {
        File(path).canonicalFile.takeIf { within(it) && it.isDirectory && it.canRead() }
    }.getOrNull()

    /** "sdcard / Projects / RevPlayer". */
    fun displayPath(folder: File): String = PhoneStoragePaths.display(root.path, folder.path)

    private fun within(file: File) = file.path == root.path || file.path.startsWith(root.path + "/")

    companion object {
        fun of() = PhoneStorage(Environment.getExternalStorageDirectory().canonicalFile, Environment::isExternalStorageManager)
    }
}
