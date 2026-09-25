package dev.icode.mobile.domain

/**
 * Git's view of the Explorer tree, as VS Code shows it: a file carries its own
 * status; a folder carries the strongest status of anything beneath it, so a
 * change deep inside a collapsed folder is visible from the top of the tree.
 */
object GitTreeStatus {
    /** Strongest first: what a folder shows when several kinds of change lie beneath it. */
    private val strength = listOf(
        GitStatusCodes.CONFLICTED,
        GitStatusCodes.DELETED,
        GitStatusCodes.MODIFIED,
        GitStatusCodes.ADDED,
        GitStatusCodes.UNTRACKED,
    )

    fun strongest(statuses: Collection<String>): String? =
        statuses.minByOrNull { status -> strength.indexOf(status).takeIf { it >= 0 } ?: strength.size }

    /**
     * [entries] with their Git status; [statusByPath] holds each change by the
     * absolute path Git reported. Git reports an untracked folder as one entry,
     * so everything inside it is untracked too.
     */
    fun decorate(entries: List<FileEntry>, statusByPath: Map<String, String>): List<FileEntry> {
        val untrackedFolders = statusByPath.filterValues { it == GitStatusCodes.UNTRACKED }.keys.map { it.trimEnd('/') + "/" }
        return entries.map { entry ->
            val own = statusByPath[entry.path]
                ?: GitStatusCodes.UNTRACKED.takeIf { untrackedFolders.any { folder -> entry.path.startsWith(folder) } }
            val status = if (entry.kind == FileKind.Folder) {
                val beneath = entry.path.trimEnd('/') + "/"
                strongest(listOfNotNull(own) + statusByPath.filterKeys { it.startsWith(beneath) }.values)
            } else {
                own
            }
            entry.copy(gitStatus = status)
        }
    }
}
