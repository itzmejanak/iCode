package dev.icode.mobile.domain.files

/**
 * What the explorer chooses a folder for, as a folder picker (docs/workspaces-and-files.md): where
 * a clone goes, in Private; or, in either place, where items are moved or copied, or archives
 * extracted.
 */
sealed interface FolderPick {
    data object Clone : FolderPick

    /** Move to… ([move]) or Copy to…: [items], as they were when chosen. */
    data class Transfer(val items: List<HeldItem>, val move: Boolean) : FolderPick

    /** Extract to…: each of [archives] into the folder chosen. */
    data class Extract(val archives: List<String>) : FolderPick
}

/** The picker's foot bar: where it is, what the button does there, and why it cannot, when it cannot. */
data class PickBar(val title: String, val caption: String, val action: String, val refusal: String? = null)

object FolderPicks {
    /**
     * The bar for [pick] at [here] — the folder shown, its runtime path [shown]; at Private's top
     * ([top]), what is put there becomes a workspace. A clone there is a new workspace; folders moved
     * or copied there become workspaces, which a file cannot. Nothing goes inside itself, and what is
     * moved where it already is stays as it is.
     */
    fun bar(pick: FolderPick, here: String, shown: String, top: Boolean): PickBar = when (pick) {
        FolderPick.Clone -> if (top) {
            PickBar("/workspace/<name>", "It becomes a workspace of its own", "New workspace")
        } else {
            PickBar(shown, "It goes in this folder", "Place here")
        }
        is FolderPick.Transfer -> {
            val verb = if (pick.move) "Move" else "Copy"
            val doing = "${if (pick.move) "Moving" else "Copying"} ${FileWords.subject(pick.items.map { it.name })}"
            val refusal = when {
                top -> FileClipboard(pick.items, cut = pick.move).notAWorkspace
                else -> pick.items.firstOrNull { here == it.location || here.startsWith(it.location + "/") }?.let { "“${it.name}” cannot go inside itself" }
            } ?: "Already here".takeIf { pick.move && pick.items.all { it.location.substringBeforeLast('/') == here } }
            if (top) PickBar(shown, "$doing here, each a workspace", "$verb here as workspaces", refusal)
            else PickBar(shown, "$doing into $shown", "$verb here", refusal)
        }
        is FolderPick.Extract -> PickBar(
            shown,
            "Extracting ${FileWords.subject(pick.archives.map { it.substringAfterLast('/') })} ${if (top) "here, as a workspace" else "into $shown"}",
            "Extract here",
        )
    }
}
