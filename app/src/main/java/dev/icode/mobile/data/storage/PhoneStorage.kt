package dev.icode.mobile.data.storage

import android.os.Environment
import dev.icode.mobile.domain.ImportBrowserState
import dev.icode.mobile.domain.PhoneFolder
import java.io.File
import java.io.IOException

/**
 * Phone storage as Import reads it (docs/workspace-storage-plan.md §4.5): the folders under the root
 * of internal storage, listed and read with All Files Access, never written. Nothing outside [root]
 * is listed or chosen, whatever a path or a link says.
 */
internal class PhoneStorage(
    /** Internal storage's root, canonical: `/storage/emulated/0`. */
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
        if (!hasAccess()) return ImportBrowserState(false, root.path, lexical.path, displayPath(lexical))
        val folder = folder(path) ?: throw IOException("This folder is no longer on phone storage")
        val folders = folder.listFiles().orEmpty()
            .filter { !it.name.startsWith('.') && it.isDirectory && folder(it.path) != null }
            .map { child ->
                val children = child.listFiles()
                PhoneFolder(child.name, child.path, children?.count { it.isDirectory } ?: 0, children?.count { it.isFile } ?: 0)
            }
            .sortedBy { it.name.lowercase() }
        return ImportBrowserState(true, root.path, folder.path, displayPath(folder), folders)
    }

    /** The folder above [path], never above the root. */
    fun parentOf(path: String): String = File(path).parentFile?.takeIf { within(it) }?.path ?: root.path

    /** [path] as a readable folder here, canonical — a link is followed only to stay here; null otherwise. */
    fun folder(path: String): File? = runCatching {
        File(path).canonicalFile.takeIf { within(it) && it.isDirectory && it.canRead() }
    }.getOrNull()

    /** "Internal storage / Projects / RevPlayer": how Android's own Files app names it. */
    fun displayPath(folder: File): String {
        val relative = folder.path.removePrefix(root.path).trim('/')
        return (listOf(ROOT_NAME) + relative.split('/').filter { it.isNotEmpty() }).joinToString(" / ")
    }

    private fun within(file: File) = file.path == root.path || file.path.startsWith(root.path + "/")

    companion object {
        private const val ROOT_NAME = "Internal storage"

        fun of() = PhoneStorage(Environment.getExternalStorageDirectory().canonicalFile, Environment::isExternalStorageManager)
    }
}
