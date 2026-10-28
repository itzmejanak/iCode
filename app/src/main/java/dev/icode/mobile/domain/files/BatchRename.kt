package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.WorkspaceNameRules

/**
 * One rule renaming many (docs/explorer-gaps-plan.md §3.4). A file's rule works on its name before
 * the extension, which it keeps — but for [Extension], whose work the extension is; a folder's on its
 * whole name, and [Extension] leaves a folder as it is.
 */
sealed interface RenameRule {
    /** [find] becomes [replacement], every time it is found. */
    data class Replace(val find: String, val replacement: String, val matchCase: Boolean = false) : RenameRule

    /** [text] before the name, or after it. */
    data class Add(val text: String, val atStart: Boolean) : RenameRule

    /** [name] and a number, from [start], in the order the items are given: "Photo 1", "Photo 2"… */
    data class Number(val name: String, val start: Int = 1) : RenameRule

    /** A file's extension becomes [extension] — with or without its dot — or goes, when it is blank. */
    data class Extension(val extension: String) : RenameRule
}

/** An item to rename: where it is, its name, and whether it is a folder. */
data class RenameSource(val location: String, val name: String, val folder: Boolean)

/** [from] becoming [to]; [problem] says why it cannot, and none is renamed while one cannot. */
data class RenamePreview(val location: String, val from: String, val to: String, val problem: String?) {
    val changes: Boolean get() = from != to
}

object BatchRename {
    /**
     * What [rule] makes of each of [items], and why a new name cannot be: a name no file or folder
     * may have, two items given one name, or one already in the folder — [taken], the names there
     * that are not being renamed. On phone storage, which ignores case, [ignoreCase] names two names
     * that differ only in case the same.
     */
    fun preview(items: List<RenameSource>, rule: RenameRule, taken: Set<String>, ignoreCase: Boolean): List<RenamePreview> {
        val width = (rule as? RenameRule.Number)?.let { maxOf(it.start.toString().length, (it.start + items.size - 1).toString().length) } ?: 0
        val renamed = items.mapIndexed { index, item -> item to renamed(item, rule, index, width) }
        fun key(name: String) = if (ignoreCase) name.lowercase() else name
        val counts = renamed.groupingBy { (_, name) -> key(name) }.eachCount()
        val takenKeys = taken.mapTo(HashSet(), ::key)
        return renamed.map { (item, name) ->
            val problem = when {
                name == item.name -> null
                else -> (if (item.folder) WorkspaceNameRules.workspaceFolderValidationError(name) else WorkspaceNameRules.fileValidationError(name))
                    ?: "Two items would be named “$name”".takeIf { counts.getValue(key(name)) > 1 }
                    ?: "“$name” is already here".takeIf { key(name) in takenKeys }
            }
            RenamePreview(item.location, item.name, name, problem)
        }
    }

    /** Something changes, and nothing is refused: the rename can go ahead. */
    fun ready(previews: List<RenamePreview>): Boolean = previews.any { it.changes } && previews.none { it.problem != null }

    private fun renamed(item: RenameSource, rule: RenameRule, index: Int, width: Int): String {
        val (stem, extension) = if (item.folder) item.name to "" else split(item.name)
        return when (rule) {
            is RenameRule.Replace -> if (rule.find.isEmpty()) item.name else stem.replace(rule.find, rule.replacement, ignoreCase = !rule.matchCase) + extension
            is RenameRule.Add -> (if (rule.atStart) rule.text + stem else stem + rule.text) + extension
            is RenameRule.Number -> {
                val number = (rule.start + index).toString().padStart(width, '0')
                (if (rule.name.isBlank()) number else "${rule.name.trimEnd()} $number") + extension
            }
            is RenameRule.Extension -> if (item.folder) item.name else rule.extension.trim().removePrefix(".").let { if (it.isEmpty()) stem else "$stem.$it" }
        }
    }

    /** A file's name before its extension, and the extension with its dot: "a.tar" and ".gz"; ".bashrc" and "". */
    private fun split(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name to "" else name.substring(0, dot) to name.substring(dot)
    }
}
