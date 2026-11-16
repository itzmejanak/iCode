package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.GitTransferLabel
import dev.icode.mobile.domain.countOf

/**
 * What Git's transfers and credentials tell (docs/engineering-rules.md): what a pull, a
 * push, a fetch or a clone did — counted where Git can say, less where it cannot, never more — or
 * could not do, with Git's lines as the output, where it ran.
 */
object GitNotices {
    /** What a transfer moved: the branch, where it goes, and the remote, as they were when it started. */
    data class Target(val branch: String?, val upstream: String?, val remote: String?) {
        val remoteName: String get() = remote ?: upstream?.substringBefore('/') ?: "the remote"
        val branchName: String get() = branch ?: "the branch"
    }

    /** [commits] HEAD moved by; null when Git could not say. */
    fun pulled(commits: Int?, target: Target, place: NoticePlace?, away: Boolean) = Notice(
        NoticeTone.Done, NoticeSource.Git,
        when (commits) {
            // A branch by its name, as it is: "main is already up to date".
            0 -> "${target.branch ?: "The branch"} is already up to date"
            null -> "Pulled into ${target.branchName}"
            else -> "Pulled ${countOf(commits, "commit")} into ${target.branchName}"
        },
        place, actions = listOf(NoticeAction.Show(NoticeTarget.SourceControl)), tellAway = away,
    )

    /** [commits] the branch was ahead by; 0 when Git said everything was up to date; null when it could not say. */
    fun pushed(commits: Int?, target: Target, place: NoticePlace?, away: Boolean): Notice {
        val to = target.upstream ?: target.remoteName
        return Notice(
            NoticeTone.Done, NoticeSource.Git,
            when (commits) {
                0 -> "Nothing to push to $to"
                null -> "Pushed ${target.branchName} to $to"
                else -> "Pushed ${countOf(commits, "commit")} to $to"
            },
            place, tellAway = away,
        )
    }

    /** [newCommits] the upstream has now that the branch has not, more than before; null when Git could not say. */
    fun fetched(newCommits: Int?, target: Target, place: NoticePlace?, away: Boolean) = Notice(
        NoticeTone.Done, NoticeSource.Git,
        if (newCommits != null && newCommits > 0) "Fetched ${target.remoteName}: ${countOf(newCommits, "new commit")} for ${target.branchName}" else "Fetched ${target.remoteName}",
        place, tellAway = away,
    )

    /**
     * A transfer that failed: what it could not do, why in a sentence where iCode knows Git's
     * words — a push behind its remote, a pull that cannot fast-forward, a sign-in refused — and
     * [output], Git's own. An [automatic] fetch is kept quietly, never posted.
     */
    fun transferFailed(label: String, reason: String, output: List<String>, target: Target, place: NoticePlace?, automatic: Boolean = false): Notice {
        val said = (output + reason).joinToString("\n")
        val rejected = "[rejected]" in said || "fetch first" in said || "non-fast-forward" in said
        val title = when (label) {
            GitTransferLabel.PULLING -> "Couldn't pull into ${target.branchName}"
            GitTransferLabel.PUSHING -> "Couldn't push ${target.branchName} to ${target.remoteName}"
            GitTransferLabel.COMMITTING -> "Couldn't commit to ${target.branchName}"
            else -> "Couldn't fetch ${target.remoteName}"
        }
        val detail = when {
            label == GitTransferLabel.PUSHING && rejected -> "${target.remoteName} has commits ${target.branchName} does not. Pull first."
            "Not possible to fast-forward" in said -> "${target.branchName} and ${target.upstream ?: target.remoteName} have both moved on. Merge or rebase them in the terminal."
            "Authentication failed" in said || "could not read Username" in said || "returned error: 403" in said ->
                "Git couldn't sign in to ${target.remoteName}. Check the token in Git credentials."
            "Could not resolve host" in said || "unable to access" in said -> "Git couldn't reach ${target.remoteName}. Check the connection."
            else -> null
        }
        val action = when {
            label == GitTransferLabel.PUSHING && rejected -> NoticeAction.Run(NoticeCommand.Pull, "Pull")
            label == GitTransferLabel.FETCHING -> NoticeAction.Run(NoticeCommand.Fetch, "Fetch Again")
            else -> NoticeAction.Show(NoticeTarget.SourceControl)
        }
        return Notice(
            if (automatic) NoticeTone.Info else NoticeTone.Problem,
            NoticeSource.Git,
            title,
            place,
            detail,
            output = gitSaid(output, reason),
            actions = listOf(action),
            keep = if (automatic) NoticeKeep.Done else NoticeKeep.NeedsYou,
        )
    }

    fun cloned(name: String, url: String, location: String, away: Boolean) = Notice(
        NoticeTone.Done, NoticeSource.Git, "Cloned $name", NoticePlace(name, location),
        detail = "From ${addressOf(url)}.", tellAway = away,
    )

    fun notCloned(name: String, url: String, reason: String, output: List<String>) = Notice(
        NoticeTone.Problem, NoticeSource.Git, "Couldn't clone ${name.ifEmpty { addressOf(url) }}",
        detail = "From ${addressOf(url)}. Nothing of it was kept.",
        output = gitSaid(output, reason),
    )

    /** What Git printed; its [reason] alone when it printed nothing — the reason is its last words. */
    private fun gitSaid(output: List<String>, reason: String): String? = output.ifEmpty { listOf(reason) }.joinToString("\n").ifBlank { null }

    /** One transfer at a time: what to do about the one asked for while [label]'s runs. */
    fun busy(label: String, place: NoticePlace?) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Git, "Wait for the ${nounOf(label)} to finish", place,
        detail = "Git runs one transfer at a time. Try again when it's done.",
    )

    /**
     * A transfer still running when iCode's process ended, at [target]: the workspace, or the folder a
     * clone was writing — and the [branch] it moved, to [upstream], when an earlier build did not
     * leave them out.
     */
    fun cutShort(label: String, target: String, branch: String? = null, upstream: String? = null): Notice {
        val clone = label == GitTransferLabel.CLONING
        return Notice(
            NoticeTone.HeadsUp,
            NoticeSource.Git,
            "The ${nounOf(label)}${branch?.takeIf { label == GitTransferLabel.PULLING }?.let { " into $it" }.orEmpty()} didn't finish",
            NoticePlaces.named(target).copy(branch = branch, remote = upstream),
            detail = if (clone) {
                "iCode closed while Git was cloning, and nothing of it was kept. Clone it again."
            } else {
                "iCode closed while Git was working. Run it again: Git goes on from what it has."
            },
            actions = listOfNotNull(commandOf(label)?.let { NoticeAction.Run(it, "${verbOf(label)} Again") }),
        )
    }

    // Git credentials: the identity, the token, signing and SSH.

    fun identityIncomplete() = Notice(NoticeTone.HeadsUp, NoticeSource.Git, "Enter a name and a valid email first", detail = "Git puts them on every commit.")

    fun needsGit() = Notice(NoticeTone.HeadsUp, NoticeSource.Git, "Install Git first", detail = "Install it from Source Control.")

    fun identityNotSaved() = Notice(NoticeTone.Problem, NoticeSource.Git, "Couldn't save the commit identity")

    fun identityNotReset(place: NoticePlace?) = Notice(NoticeTone.Problem, NoticeSource.Git, "Couldn't reset this repository's commit identity", place)

    fun tokenNotSaved(host: String) = Notice(NoticeTone.Problem, NoticeSource.Git, "Couldn't save the token for $host")

    fun tokenNotRemoved() = Notice(NoticeTone.Problem, NoticeSource.Git, "Couldn't remove the token")

    fun signingNotChanged() = Notice(NoticeTone.Problem, NoticeSource.Git, "Couldn't change commit signing")

    fun identityFirst() = Notice(NoticeTone.HeadsUp, NoticeSource.Git, "Save a default commit identity first", detail = "A signing key is made for it.")

    /** gpg's or apt's words, which say what failed themselves: the output, under what iCode was doing. */
    fun gnuPgNotInstalled(words: String) = Notice(NoticeTone.Problem, NoticeSource.Git, "Couldn't install GnuPG", output = words)

    fun signingKeyNotMade(words: String) = Notice(NoticeTone.Problem, NoticeSource.Git, "Couldn't create a signing key", output = words)

    fun passphraseChanged() = Notice(NoticeTone.Done, NoticeSource.Git, "Changed the signing key's passphrase")

    fun passphraseNotChanged(words: String) = Notice(NoticeTone.Problem, NoticeSource.Git, "Couldn't change the signing key's passphrase", output = words)

    fun sshKeyNotMade(words: String) = Notice(NoticeTone.Problem, NoticeSource.Git, "Couldn't create an SSH key", output = words)

    /** "pull", "push", "fetch", "clone": a transfer as a thing. */
    fun nounOf(label: String): String = when (label) {
        GitTransferLabel.CLONING -> "clone"
        GitTransferLabel.FETCHING -> "fetch"
        GitTransferLabel.PULLING -> "pull"
        GitTransferLabel.PUSHING -> "push"
        GitTransferLabel.COMMITTING -> "commit"
        else -> label.lowercase()
    }

    /** "github.com/acme/superpowers": an address as people say it, without its scheme or `.git`. */
    fun addressOf(url: String): String {
        val scp = "://" !in url
        val host = url.substringAfter("://").substringAfter('@')
        return (if (scp) host.replaceFirst(':', '/') else host).removeSuffix("/").removeSuffix(".git")
    }

    /** "Pull", "Push": what a button runs again. */
    private fun verbOf(label: String): String = nounOf(label).replaceFirstChar(Char::uppercaseChar)

    private fun commandOf(label: String): NoticeCommand? = when (label) {
        GitTransferLabel.FETCHING -> NoticeCommand.Fetch
        GitTransferLabel.PULLING -> NoticeCommand.Pull
        GitTransferLabel.PUSHING -> NoticeCommand.Push
        else -> null
    }
}
