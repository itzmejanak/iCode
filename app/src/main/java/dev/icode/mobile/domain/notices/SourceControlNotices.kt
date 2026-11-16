package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.countOf

/**
 * What Source Control's own operations tell (docs/engineering-rules.md): each that failed
 * by what it could not do — never "Git operation failed" — Git's words its output, where it ran; a
 * commit by what it committed; and a conflicted file's resolution.
 */
object SourceControlNotices {
    /** What was staged [before] went in, onto its branch: "Committed 3 files to main", the message's first line under it. */
    fun committed(before: ICodeSnapshot, message: String): Notice {
        val files = before.gitChanges.filter { it.staged }.map { it.path }.distinct().size
        return Notice(
            NoticeTone.Done, NoticeSource.Git,
            "Committed ${if (files > 0) countOf(files, "file") else "the staged changes"}${onto(before)}",
            NoticePlaces.git(before), detail = message.lineSequence().firstOrNull { it.isNotBlank() }?.trim(),
        )
    }

    fun notCommitted(before: ICodeSnapshot) = failed("commit${onto(before)}", NoticePlaces.git(before))

    /** " to main"; nothing when HEAD is detached. */
    private fun onto(snapshot: ICodeSnapshot) = snapshot.gitRepository?.takeUnless { it.detached }?.branch?.let { " to $it" }.orEmpty()

    fun notStaged(path: String?, place: NoticePlace?) = failed(path?.let { "stage ${nameOf(it)}" } ?: "stage all changes", place)

    fun notUnstaged(path: String?, place: NoticePlace?) = failed(path?.let { "unstage ${nameOf(it)}" } ?: "unstage all changes", place)

    fun changeNotStaged(place: NoticePlace?) = failed("stage the change", place)

    fun notSwitched(branch: String, place: NoticePlace?) = failed("switch to $branch", place)

    fun branchNotCreated(branch: String, place: NoticePlace?) = failed("create branch $branch", place)

    fun branchNotDeleted(branch: String, place: NoticePlace?) = failed("delete branch $branch", place)

    fun notCheckedOut(remoteBranch: String, place: NoticePlace?) = failed("check out ${remoteBranch.removePrefix("remotes/")}", place)

    fun remoteNotAdded(name: String, place: NoticePlace?) = failed("add remote $name", place)

    fun notDiscarded(name: String, place: NoticePlace?) = failed("discard the changes to $name", place)

    fun notAborted(inProgress: GitInProgress, place: NoticePlace?) = failed(
        when (inProgress) {
            GitInProgress.Merge -> "abort the merge"
            GitInProgress.Rebase -> "abort the rebase"
            GitInProgress.CherryPick -> "abort the cherry-pick"
            GitInProgress.Revert -> "abort the revert"
            GitInProgress.Bisect -> "end the bisect"
            GitInProgress.None -> "abort"
        },
        place,
    )

    fun notMarkedResolved(path: String, place: NoticePlace?) = failed("mark ${nameOf(path)} resolved", place)

    fun identityNotSet(place: NoticePlace?) = failed("set this repository's commit identity", place)

    /** A remote's name or address the rules refuse, in their words. */
    fun remoteRefused(words: String, place: NoticePlace?) = Notice(NoticeTone.HeadsUp, NoticeSource.Git, words, place)

    fun openWorkspaceFirst() = Notice(NoticeTone.HeadsUp, NoticeSource.Git, "Open a workspace first", detail = "Git works in the open workspace.")

    fun alreadyRepository(place: NoticePlace?) = Notice(NoticeTone.HeadsUp, NoticeSource.Git, "This folder is already in a Git repository", place)

    fun notInitialized(place: NoticePlace?) = failed("start a Git repository here", place)

    fun diffUnread(path: String, place: NoticePlace?) = failed("show the changes to ${nameOf(path)}", place)

    fun compareNeedsGit() = Notice(NoticeTone.HeadsUp, NoticeSource.Git, "Install Git to compare", detail = "Install it from Source Control.")

    fun notCompared(path: String, place: NoticePlace?) = failed("compare ${nameOf(path)} with the editor", place)

    fun notDiscardable(path: String, place: NoticePlace?) =
        Notice(NoticeTone.HeadsUp, NoticeSource.Git, "iCode can't discard ${nameOf(path)}", place, detail = "It is not a change in this repository.")

    // A conflicted file.

    fun conflictUnread(path: String, place: NoticePlace?) = failed("read ${nameOf(path)}", place)

    fun conflictUnsavedFirst(name: String, place: NoticePlace?) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Git, "Save or close $name first", place,
        detail = "It has unsaved edits in the editor.",
    )

    fun conflictChangedOnDisk(name: String, place: NoticePlace?) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Git, "$name changed on disk", place,
        detail = "It was read again. Choose again.",
    )

    fun conflictNotWritten(name: String, place: NoticePlace?) = failed("write $name", place)

    private fun failed(what: String, place: NoticePlace?) = Notice(NoticeTone.Problem, NoticeSource.Git, "Couldn't $what", place)

    private fun nameOf(path: String) = path.removeSuffix("/").substringAfterLast('/')
}
