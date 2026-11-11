package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.ICodeSnapshot

/** What the bell, the Notifications page and Android's shade do with notices (docs/notices-redesign-plan.md D3–D7). */
object NoticeRules {
    /** The page keeps this many of what needs the user, and apart from them this many of the rest, newest first. */
    const val MAX_NEEDS_YOU = 100
    const val MAX_DONE = 150

    /** Android's shade holds at most this many, the newest. */
    const val MAX_POSTED = 20

    /** The same event told again within this folds into the one before (D6). */
    const val FOLD_MILLIS = 10 * 60_000L

    /** Work that ran this long is worth telling when it ends while iCode is out of view (D7); a long command's too. */
    const val LONG_WORK_MILLIS = 30_000L

    /**
     * Rings the bell: what needs the user, and a tap's own confirmation. What is kept as done — a
     * success, or what happened on its own — is kept silently (owner's decisions, 2026-11-11: §7, Q1
     * and Q6).
     */
    fun rings(notice: Notice): Boolean = notice.keep != NoticeKeep.Done

    /**
     * Rings the bell when told with the Notifications page open or not ([pageOpen]): what [rings] does,
     * but what the open page keeps ([kept]) is already in its list there, so it does not ring the bell
     * as well, to ring once the page is left.
     */
    fun rings(notice: Notice, pageOpen: Boolean): Boolean = rings(notice) && !(pageOpen && kept(notice))

    /** Kept on the page: all but a tap's own confirmation. */
    fun kept(notice: Notice): Boolean = notice.keep != NoticeKeep.Moment

    /** On the bell's count, and the page's dot: what needs the user, unread. */
    fun counted(notice: Notice): Boolean = notice.keep == NoticeKeep.NeedsYou && !notice.read

    /** How long the bell stays awake once a notice rings it: a problem longest. */
    fun bellMillis(tone: NoticeTone): Long = when (tone) {
        NoticeTone.Problem -> 15_000
        NoticeTone.HeadsUp, NoticeTone.Info -> 10_000
        NoticeTone.Done -> 3_000
    }

    /** The bell's colour at rest: the most serious of what is counted; null with nothing to count. */
    fun bellTone(notices: List<Notice>): NoticeTone? = notices.filter(::counted).minOfOrNull { it.tone }

    /** The notices Android's shade holds: unread, not swiped away, needing the user or finished while away; the newest. */
    fun posted(notices: List<Notice>): List<Notice> = notices
        .filter { !it.read && !it.dismissed && (it.keep == NoticeKeep.NeedsYou || it.away) }
        .sortedByDescending { it.at }
        .take(MAX_POSTED)

    /**
     * What [notice] offers now, in [snapshot]: Undo while its change is still what Undo would reverse;
     * what acts in a workspace only while that workspace is the open one, so a pull is never run, nor a
     * page shown, in another.
     */
    fun available(notice: Notice, snapshot: ICodeSnapshot): List<NoticeAction> {
        val here = notice.place?.workspaceLocation.let { it == null || it == snapshot.activeWorkspaceLocation }
        return notice.actions.filter { action ->
            when (action) {
                is NoticeAction.Undo -> snapshot.explorer.history.undo == action.change
                is NoticeAction.OpenFile, is NoticeAction.Run -> here
                is NoticeAction.Show -> here || action.target !in IN_A_WORKSPACE
                is NoticeAction.OpenSession, is NoticeAction.OpenUrl -> true
            }
        }
    }

    private val IN_A_WORKSPACE = setOf(NoticeTarget.SourceControl, NoticeTarget.Terminal, NoticeTarget.Output, NoticeTarget.Assistant)

    /**
     * [notices] with [notice] added: folded into the newest one of its [Notice.repeatKey] told within
     * [FOLD_MILLIS], else first; then what needs the user and the rest each cut to their own most.
     */
    fun added(notices: List<Notice>, notice: Notice): List<Notice> {
        val folded = notice.repeatKey?.let { key ->
            notices.firstOrNull { it.repeatKey == key && notice.at - it.at in 0..FOLD_MILLIS }
        }
        val next = if (folded == null) notice else notice.copy(repeats = folded.repeats + 1)
        val all = (listOf(next) + notices.filterNot { it.id == folded?.id }).sortedByDescending { it.at }
        val (needsYou, rest) = all.partition { it.keep == NoticeKeep.NeedsYou }
        return (needsYou.take(MAX_NEEDS_YOU) + rest.take(MAX_DONE)).sortedByDescending { it.at }
    }
}

/** Which notices the page shows, and Clear all clears (D6). */
sealed interface NoticeFilter {
    fun matches(notice: Notice): Boolean

    data object All : NoticeFilter {
        override fun matches(notice: Notice) = true
    }

    data object NeedsYou : NoticeFilter {
        override fun matches(notice: Notice) = notice.keep == NoticeKeep.NeedsYou
    }

    data object Done : NoticeFilter {
        override fun matches(notice: Notice) = notice.keep == NoticeKeep.Done
    }

    /** Those of one workspace, or of one place outside them, by its name. */
    data class At(val place: String) : NoticeFilter {
        override fun matches(notice: Notice) = notice.place?.workspace == place
    }

    /** Those of no place. */
    data object Elsewhere : NoticeFilter {
        override fun matches(notice: Notice) = notice.place?.workspace == null
    }

    companion object {
        /** The places [notices] are about, the newest first, each once: the page's choices. */
        fun placesOf(notices: List<Notice>): List<String> = notices.mapNotNull { it.place?.workspace }.distinct()
    }
}
