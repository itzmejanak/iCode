package dev.icode.mobile.domain.files

import java.util.concurrent.TimeUnit

/**
 * Something deleted, waiting in its place's trash (docs/explorer-gaps-plan.md §3.7): [id] names it
 * there; [from] is where it was, and where Restore puts it back.
 */
data class TrashedItem(
    val id: String,
    val name: String,
    val from: String,
    val place: StoragePlace,
    val deletedAt: Long,
    val folder: Boolean,
    /** A file's size, or a folder's as measured when it was deleted; null when it could not be. */
    val bytes: Long?,
    /** A whole workspace: restored, it is one again. */
    val workspace: Boolean,
)

/** How much Trash holds, for Settings › Storage and Manage storage. */
data class TrashSummary(val items: Int, val bytes: Long)

object TrashRules {
    /** An item is removed after this long in Trash. */
    const val KEEP_DAYS = 30L

    fun expired(item: TrashedItem, now: Long): Boolean = now - item.deletedAt >= TimeUnit.DAYS.toMillis(KEEP_DAYS)

    fun summary(items: List<TrashedItem>): TrashSummary = TrashSummary(items.size, items.sumOf { it.bytes ?: 0L })
}
