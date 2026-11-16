package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.countOf

/** What Settings › Storage tells (docs/engineering-rules.md): the runtime's cache, cleared or not. */
object StorageNotices {
    private val runtime = NoticePlace(NoticePlaces.RUNTIME)

    fun cacheBusy() = Notice(
        NoticeTone.HeadsUp, NoticeSource.Storage, "Finish what runs in the runtime first", runtime,
        detail = "Runtime installs, Git, terminal sessions and language servers use its cache. Then clear it.",
    )

    /** Nothing to clear is the tap's answer alone: it rings, and is not kept. */
    fun cacheCleared(removed: Int) = if (removed == 0) {
        Notice(NoticeTone.Info, NoticeSource.Storage, "The runtime's cache is already clear", runtime, keep = NoticeKeep.Moment)
    } else {
        Notice(NoticeTone.Done, NoticeSource.Storage, "Removed ${countOf(removed, "cached item")} from the runtime", runtime)
    }

    fun cacheNotCleared() = Notice(NoticeTone.Problem, NoticeSource.Storage, "Couldn't clear the runtime's cache", runtime)
}
