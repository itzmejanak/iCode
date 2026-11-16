package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.countOf
import java.io.File

/**
 * What the editor tells (docs/engineering-rules.md): of the document at a location, by its
 * name, with where it is — its workspace and folder, read from the snapshot ([at]) — as the place. A
 * save by hand, and one that failed, fold when told again soon after.
 */
object EditorNotices {
    fun saved(at: ICodeSnapshot, location: String) =
        notice(NoticeTone.Done, at, location, "Saved ${nameOf(location)}", repeatKey = "saved:$location")

    /** The error's words are the output; an automatic save that keeps failing folds into one. */
    fun notSaved(at: ICodeSnapshot, location: String) = notice(
        NoticeTone.Problem, at, location, "Couldn't save ${nameOf(location)}",
        detail = "Its edits are still open in the editor, and kept in Document Recovery.",
        repeatKey = "not-saved:$location",
    )

    fun notOpened(at: ICodeSnapshot, location: String) = notice(NoticeTone.Problem, at, location, "Couldn't open ${nameOf(location)}")

    /** The disk's version, asked for to reload or to save over, could not be read. */
    fun diskUnread(at: ICodeSnapshot, location: String) = notice(NoticeTone.Problem, at, location, "Couldn't read ${nameOf(location)} from storage")

    fun deletedOutside(at: ICodeSnapshot, location: String) = notice(
        NoticeTone.Info, at, location, "${nameOf(location)} was deleted outside iCode",
        detail = "Its text stays open in the editor. Save it to keep it.",
        actions = listOf(NoticeAction.OpenFile(location)),
    )

    fun outsideWorkspace(location: String) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Editor, "${nameOf(location)} is outside the open workspace",
        detail = "Open its workspace to edit it.",
    )

    fun noLongerOpen(at: ICodeSnapshot, location: String) = notice(NoticeTone.HeadsUp, at, location, "${nameOf(location)} is no longer open")

    fun unsavedFirst(at: ICodeSnapshot, location: String) = notice(
        NoticeTone.HeadsUp, at, location, "Save or discard ${nameOf(location)} first",
        detail = "It has unsaved edits. Choose Save, Discard or Cancel to close it.",
    )

    fun unsavedTabsFirst(at: ICodeSnapshot, count: Int) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Editor, "Save or discard the unsaved tabs first", NoticePlaces.workspace(at),
        detail = "${countOf(count, "tab")} ${if (count == 1) "has" else "have"} unsaved edits. Choose Save, Discard or Cancel to close them.",
    )

    // Document Recovery.

    fun draftsUnread() = Notice(NoticeTone.Problem, NoticeSource.Editor, "Couldn't read the recovery drafts")

    fun draftNotKept(at: ICodeSnapshot, location: String) = notice(
        NoticeTone.Problem, at, location, "Couldn't keep a recovery draft of ${nameOf(location)}",
        detail = "Save it to keep its edits.",
    )

    fun newerEditsFirst(at: ICodeSnapshot, location: String) = notice(
        NoticeTone.HeadsUp, at, location, "Save or discard the newer edits of ${nameOf(location)} first",
        detail = "Then restore its draft.",
    )

    fun newEditsFirst(at: ICodeSnapshot, location: String) = notice(
        NoticeTone.HeadsUp, at, location, "Save or close ${nameOf(location)} first",
        detail = "It has new edits. Then discard its recovery draft.",
    )

    /** Saved, but its draft could not follow: kept as it was, it may be offered again. */
    fun draftNotUpdated(at: ICodeSnapshot, location: String) = notice(
        NoticeTone.Problem, at, location, "Couldn't update the recovery draft of ${nameOf(location)}",
        detail = "${nameOf(location)} was saved. Its old draft may be offered again.",
    )

    private fun nameOf(location: String) = File(location).name

    private fun notice(
        tone: NoticeTone,
        at: ICodeSnapshot,
        location: String,
        title: String,
        detail: String? = null,
        actions: List<NoticeAction> = emptyList(),
        repeatKey: String? = null,
    ) = Notice(tone, NoticeSource.Editor, title, NoticePlaces.of(at, location), detail, actions = actions, repeatKey = repeatKey)
}
