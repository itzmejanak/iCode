package dev.icode.mobile.data.language

import java.io.File

/**
 * The folders a server is told are its projects (`workspaceFolders`, docs/editor-and-language-services.md): the
 * workspace's top when it holds one of the root markers, or has none to look for; else each folder
 * within a few levels that holds one, the top when none does.
 */
internal object LanguageRoots {
    private const val DEPTH = 3
    private const val MAX_FOLDERS = 10

    /** Folders no project is the top of, which can hold thousands of others. */
    private val SKIPPED = setOf(".git", "node_modules", "build", "dist", "out", "target", ".gradle", ".idea", ".venv", "venv", "__pycache__", ".cache")

    fun folders(workspace: String, markers: List<String>): List<String> {
        val top = File(workspace)
        if (markers.isEmpty() || holds(top, markers)) return listOf(top.path)
        val found = mutableListOf<String>()
        var level = listOf(top)
        repeat(DEPTH) {
            level = level.flatMap { folder -> folder.listFiles { child -> child.isDirectory && child.name !in SKIPPED }.orEmpty().sortedBy { it.name } }
            val (projects, others) = level.partition { holds(it, markers) }
            projects.forEach { if (found.size < MAX_FOLDERS) found += it.path }
            if (found.size >= MAX_FOLDERS) return found
            // A project's own folders are its, not projects of their own.
            level = others
        }
        return found.ifEmpty { listOf(top.path) }
    }

    private fun holds(folder: File, markers: List<String>) = markers.any { File(folder, it).exists() }
}
