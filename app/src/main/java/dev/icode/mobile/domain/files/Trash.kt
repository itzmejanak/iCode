package dev.icode.mobile.domain.files

import java.util.concurrent.TimeUnit

/**
 * Something deleted, waiting in its place's trash (docs/workspaces-and-files.md): [id] names it
 * there, and [heldAt] is where it is held; [from] is where it was, and where Restore puts it back —
 * [path], as the runtime names it.
 */
data class TrashedItem(
    val id: String,
    val name: String,
    val from: String,
    val path: String,
    val heldAt: String,
    val place: StoragePlace,
    val deletedAt: Long,
    val folder: Boolean,
    /** A file's size, or a folder's as measured when it was deleted; null when it could not be. */
    val bytes: Long?,
    /** A whole workspace: restored, it is one again. */
    val workspace: Boolean,
) {
    /** As Trash's view lists it: where it is held, so its picture can be shown; where it was, and when it went. */
    val row: BrowsedItem get() = BrowsedItem(name, heldAt, path, place, folder, bytes, changedAt = deletedAt)
}

/** How much Trash holds, for Settings › Storage and Manage storage. */
data class TrashSummary(val items: Int, val bytes: Long)

object TrashRules {
    /** An item is removed after this long in Trash. */
    const val KEEP_DAYS = 30L

    /** When [item] is removed, [KEEP_DAYS] after it was deleted. */
    fun removedAt(item: TrashedItem): Long = item.deletedAt + TimeUnit.DAYS.toMillis(KEEP_DAYS)

    fun expired(item: TrashedItem, now: Long): Boolean = now >= removedAt(item)

    fun summary(items: List<TrashedItem>): TrashSummary = TrashSummary(items.size, items.sumOf { it.bytes ?: 0L })
}
