package dev.icode.mobile.domain

/**
 * Unstaged changes held for Discard's confirmation: one file's, or every one a folder holds
 * ([folder]). Confirmed, they are discarded only while Git still reports exactly these.
 */
data class GitDiscard(val path: String, val folder: Boolean, val changes: List<GitChange>) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/')

    companion object {
        /** What discarding [path] would throw away: its own unstaged change, else each under it; null when nothing. */
        fun of(changes: List<GitChange>, path: String): GitDiscard? {
            changes.firstOrNull { it.path == path && it.discardable }?.let { return GitDiscard(path, folder = false, listOf(it)) }
            val inside = changes.filter { it.discardable && it.path.startsWith("$path/") }
            return if (inside.isEmpty()) null else GitDiscard(path, folder = true, inside)
        }
    }
}
