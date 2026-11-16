package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.FileType

/** What one item is, as Properties names it (docs/workspaces-and-files.md). */
enum class FactKind { File, Folder, Workspace, Link }

/**
 * What Properties shows, for one item — [kind] — or many, [folders] of them folders. A file's
 * size is known at once; a folder's, and many items', is [measured] as Properties is open, null until
 * then. There is no creation time because Android does not keep one.
 */
data class FileFacts(
    val locations: List<String>,
    /** One item's kind; null for many. */
    val kind: FactKind?,
    /** How many of the items are folders. */
    val folders: Int,
    /** Where a link points, as it is written. */
    val linkTarget: String? = null,
    /** One item's last change. */
    val modifiedAt: Long? = null,
    /** A file in Private that runs. */
    val executable: Boolean? = null,
    /** One file's kind, its first bytes read where its name could not say (docs/workspaces-and-files.md). */
    val type: FileType? = null,
    val measured: TreeFacts? = null,
)

/** What items hold, measured: their files' bytes — null when a folder could not be read — and how many files and folders. */
data class TreeFacts(val bytes: Long?, val files: Long, val folders: Long)
