package dev.icode.mobile.domain.files

/** An item held for Paste: [workspace] when it is a whole workspace, not something in one. */
data class HeldItem(val location: String, val name: String, val folder: Boolean, val workspace: Boolean)

/**
 * What is cut or copied, waiting for Paste (docs/explorer-gaps-plan.md §3.5): every explorer's one
 * clipboard, across both places, holding many. Pasted, a cut goes; a copy stays to paste again.
 */
data class FileClipboard(val items: List<HeldItem>, val cut: Boolean) {
    /** "README.md", or "3 items": what the paste bar says it holds. */
    val name: String get() = FileWords.subject(items.map { it.name })

    /**
     * Why they cannot be pasted as workspaces — at the top of Private — or null when they can: a file
     * is not a workspace, and a cut workspace already is one. The paste's own check and the paste
     * bar's both read it.
     */
    val notAWorkspace: String?
        get() = items.firstNotNullOfOrNull { item ->
            when {
                !item.folder -> "A workspace is a folder: paste ${item.name} inside one"
                cut && item.workspace -> "${item.name} is a workspace already"
                else -> null
            }
        }
}
