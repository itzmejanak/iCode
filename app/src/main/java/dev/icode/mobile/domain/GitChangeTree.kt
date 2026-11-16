package dev.icode.mobile.domain

// Source Control's changes as a tree (docs/workspaces-and-files.md), as VS Code's
// "View as Tree": each change under its folders, folders first, each by name.

/** One row of the tree: a folder that holds changes, or a changed file. */
sealed interface GitChangeRow {
    val depth: Int

    /**
     * [path] from the repository's top; [name] the folders it stands for — "src/main" when one
     * holds only the next; [changes] how many are under it.
     */
    data class Folder(val path: String, val name: String, override val depth: Int, val changes: Int, val expanded: Boolean) : GitChangeRow

    data class File(val change: GitChange, override val depth: Int) : GitChangeRow
}

object GitChangeTree {
    /**
     * [changes] as rows: under each folder its folders, then its files, each by name; a collapsed
     * folder — its path in [collapsed] — hides what is in it. A folder Git reports whole (another
     * repository inside this one, "vendor/lib/") is a row of its own, as a file is.
     */
    fun rows(changes: List<GitChange>, collapsed: Set<String>): List<GitChangeRow> {
        val top = Node("", "")
        changes.forEach { change ->
            val folders = change.path.trimEnd('/').split('/').dropLast(1)
            var node = top
            node.changes++
            folders.forEach { name ->
                val path = if (node.path.isEmpty()) name else "${node.path}/$name"
                node = node.folders.getOrPut(name) { Node(name, path) }
                node.changes++
            }
            node.files += change
        }
        return buildList { emit(top, depth = 0, collapsed) }
    }

    private fun MutableList<GitChangeRow>.emit(node: Node, depth: Int, collapsed: Set<String>) {
        node.folders.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).forEach { child ->
            val folder = child.compacted()
            val expanded = folder.path !in collapsed
            add(GitChangeRow.Folder(folder.path, folder.name, depth, folder.changes, expanded))
            if (expanded) emit(folder, depth + 1, collapsed)
        }
        node.files.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).forEach { add(GitChangeRow.File(it, depth)) }
    }

    private class Node(val name: String, val path: String) {
        val folders = mutableMapOf<String, Node>()
        val files = mutableListOf<GitChange>()
        var changes = 0

        /** A chain of folders, each holding only the next, as one: `src/main/java`. */
        fun compacted(): Node {
            var node = this
            var name = this.name
            while (node.files.isEmpty() && node.folders.size == 1) {
                node = node.folders.values.single()
                name = "$name/${node.name}"
            }
            return if (node === this) this else Node(name, node.path).also { merged ->
                merged.folders += node.folders
                merged.files += node.files
                merged.changes = node.changes
            }
        }
    }
}
