package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.ICodeSnapshot
import java.io.File

/**
 * What iCode tells of workspaces — opening, refreshing, renaming them — and of what is kept about
 * them (docs/notices-redesign-plan.md D8): each by its name, read where it is ([at]) as the place.
 */
object WorkspaceNotices {
    fun gone(location: String) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Workspaces, "${nameOf(location)} no longer exists",
        detail = "It was moved or deleted outside iCode.",
    )

    fun notOpened(location: String) = Notice(NoticeTone.Problem, NoticeSource.Workspaces, "Couldn't open ${nameOf(location)}", NoticePlace(nameOf(location), location))

    /** What iCode linked to has gone since. */
    fun notThere(at: ICodeSnapshot, location: String) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Workspaces, "${nameOf(location)} is no longer there", NoticePlaces.of(at, location),
        detail = "It was moved or deleted since.",
    )

    fun draftOutside(location: String) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Workspaces, "${nameOf(location)} is outside iCode's workspaces",
        detail = "Import its folder, or discard the draft.",
    )

    /** Asked again and again as iCode comes back: folded into one. */
    fun notRefreshed(at: ICodeSnapshot, root: String) = Notice(
        NoticeTone.Problem, NoticeSource.Workspaces, "Couldn't refresh ${nameOf(root)}", NoticePlaces.of(at, root, inside = false),
        repeatKey = "not-refreshed:$root",
    )

    /** Made, or changed, but the open workspace's tree, Git and index could not follow. */
    fun changeNotShown(at: ICodeSnapshot, root: String, made: String? = null) = Notice(
        NoticeTone.Problem, NoticeSource.Workspaces, "Couldn't refresh ${nameOf(root)}", NoticePlaces.of(at, root, inside = false),
        detail = "${made?.let(::nameOf)?.let { "$it was made" } ?: "The change was made"}; the Explorer may not show it yet.",
        repeatKey = "not-refreshed:$root",
    )

    fun notListed(at: ICodeSnapshot, root: String) =
        Notice(NoticeTone.Problem, NoticeSource.Workspaces, "Couldn't list ${nameOf(root)} again", NoticePlaces.of(at, root, inside = false))

    fun folderUnread(at: ICodeSnapshot, location: String) =
        Notice(NoticeTone.Problem, NoticeSource.Workspaces, "Couldn't read ${nameOf(location)}", NoticePlaces.of(at, location))

    // What is kept about a workspace.

    fun settingsNotMoved(to: String) = Notice(
        NoticeTone.Problem, NoticeSource.Workspaces, "Couldn't keep ${nameOf(to)}'s settings after the rename", NoticePlace(nameOf(to), to),
        detail = "Its Workspace settings may start over.",
    )

    fun settingsNotCleared(location: String) = Notice(
        NoticeTone.Problem, NoticeSource.Workspaces, "Couldn't clear the settings of ${nameOf(location)}",
        detail = "A workspace made later with its name may start with them.",
    )

    fun historyNotMoved(to: String) = Notice(
        NoticeTone.Problem, NoticeSource.Workspaces, "Couldn't keep ${nameOf(to)}'s place in Recent after the rename", NoticePlace(nameOf(to), to),
    )

    fun historyNotCleared(location: String) =
        Notice(NoticeTone.Problem, NoticeSource.Workspaces, "Couldn't clear what iCode kept of ${nameOf(location)}")

    fun sessionNotKept() = Notice(
        NoticeTone.Problem, NoticeSource.Workspaces, "Couldn't remember the open workspace",
        detail = "iCode may not open it again by itself next time.",
    )

    private fun nameOf(location: String) = File(location).name.ifEmpty { location }
}
