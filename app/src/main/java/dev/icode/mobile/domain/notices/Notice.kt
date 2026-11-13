package dev.icode.mobile.domain.notices

/**
 * Something iCode tells the user (docs/notices-redesign-plan.md D1): what happened, to what, and
 * where — on the bell, the Notifications page and Android's shade. Its words are written only by the
 * builders of this package, one per area; [id] and [at] are given by whoever tells it, unless the
 * notice brings its own (the website's, what the previous process left).
 */
data class Notice(
    val tone: NoticeTone,
    val source: NoticeSource,
    /** What happened, to what: one line (D8). */
    val title: String,
    /** Where it happened (D2), captured when the work began. */
    val place: NoticePlace? = null,
    /** Why, and what to do next: sentences. */
    val detail: String? = null,
    /** The tool's own words, as printed: Git's lines, a server's message, an error's. */
    val output: String? = null,
    /** At most two; the first is the main one. */
    val actions: List<NoticeAction> = emptyList(),
    val keep: NoticeKeep = NoticeKeep.of(tone),
    /** Work worth Android's shade when it ends while iCode is out of view (D7: Finished while you were away). */
    val tellAway: Boolean = false,
    /** The same event told again soon after folds into this one (D6). */
    val repeatKey: String? = null,
    val id: String = "",
    val at: Long = 0L,
    val read: Boolean = false,
    /** How many times it was told, folded. */
    val repeats: Int = 1,
    /** It came while iCode was out of view, and is posted to Finished. */
    val away: Boolean = false,
    /** Swiped from Android's shade: never posted again, still unread here. */
    val dismissed: Boolean = false,
) {
    /** Said by the iCode website (Admin › Notices), not by this phone. */
    val fromWebsite: Boolean get() = source == NoticeSource.Website

    companion object {
        /** A website notice's id here: `web:` and the website's id, so it is stored once and never twice. */
        const val WEBSITE_PREFIX = "web:"
    }
}

/** How serious it is, in order, the most serious first: its colour and icon (docs/notices-plan.md §2.1). */
enum class NoticeTone {
    /** Something failed. */
    Problem,

    /** What to do first, or what was refused. */
    HeadsUp,

    /** Something happened. */
    Info,

    /** Something the user did, done. */
    Done,
}

/** What a notice is kept as (D3). */
enum class NoticeKeep {
    /** Problems and heads-ups: they ring the bell, count, and are posted. */
    NeedsYou,

    /** What happened and what was done: kept apart, never counted, and it never rings the bell. */
    Done,

    /** A confirmation of a tap only: it rings, and is not kept. */
    Moment;

    companion object {
        fun of(tone: NoticeTone): NoticeKeep = when (tone) {
            NoticeTone.Problem, NoticeTone.HeadsUp -> NeedsYou
            NoticeTone.Info, NoticeTone.Done -> Done
        }
    }
}

/** The part of iCode it is from, as the sheet names it. */
enum class NoticeSource(val label: String) {
    Files("Files"),
    Editor("Editor"),
    Git("Git"),
    Terminal("Terminal"),
    Run("Run"),
    Languages("Language servers"),
    Search("Search"),
    Assistant("Assistant"),
    Mcp("MCP servers"),
    Phone("Phone control"),
    Voice("Voice"),
    Runtime("Linux runtime"),
    Workspaces("Workspaces"),
    Storage("Storage"),
    Settings("Settings"),
    Website("iCode website"),
    ICode("iCode"),
}

/**
 * Where it happened (D2): the workspace — or, outside one, the place: Phone storage, the Linux
 * runtime — the repository when the workspace holds several, the branch and where it goes, and what
 * it was about: a folder or file in the workspace, a terminal tab, a chat, a server, a model.
 */
data class NoticePlace(
    val workspace: String? = null,
    /** The workspace's location, when [workspace] is one. */
    val workspaceLocation: String? = null,
    val repository: String? = null,
    val branch: String? = null,
    /** Where the branch goes: `origin/main`. */
    val remote: String? = null,
    val subject: String? = null,
) {
    /** "phone · src", "phone · main → origin/main": the place in one line. */
    val line: String get() = listOfNotNull(workspace, repository, git, subject).joinToString(" · ")

    private val git: String? get() = when {
        branch != null && remote != null -> "$branch → $remote"
        else -> branch ?: remote
    }
}

/** What a notice offers to do (D1): a closed set, stored by name, done by the screens. */
sealed interface NoticeAction {
    val label: String

    data class OpenFile(val location: String, override val label: String = "Open") : NoticeAction

    data class Show(val target: NoticeTarget) : NoticeAction {
        override val label: String get() = target.label
    }

    data class OpenSession(val id: String, override val label: String = "Open") : NoticeAction

    data class OpenUrl(val url: String, override val label: String = "Open Link") : NoticeAction

    /** Undoes the change said as [change], while it is still what Undo would reverse. */
    data class Undo(val change: String) : NoticeAction {
        override val label: String get() = "Undo"
    }

    data class Run(val command: NoticeCommand, override val label: String) : NoticeAction
}

/** A page a notice leads to. */
enum class NoticeTarget(val label: String) {
    SourceControl("Open Source Control"),
    Terminal("Open Terminal"),
    Output("Show Output"),
    RuntimeTools("Open Runtime Tools"),
    Languages("Open Language Servers"),
    McpServers("Open MCP Servers"),
    Assistant("Open Assistant"),
    /** The Assistant outside the workspace, where a chat in no workspace is (docs/home-plan.md §2). */
    AssistantOutside("Open Assistant"),
}

/** Something a notice can do again, with nothing more to say. */
enum class NoticeCommand { Pull, Push, Fetch, InstallRuntime }
