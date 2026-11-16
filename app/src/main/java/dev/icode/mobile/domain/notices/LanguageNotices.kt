package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.countOf
import java.io.File

/**
 * What the language servers tell (docs/engineering-rules.md): by the server's name, in
 * the open workspace. Their words ([LanguageFailure]'s, a server's own) are said as they are.
 */
object LanguageNotices {
    fun formatted(at: ICodeSnapshot, location: String) =
        Notice(NoticeTone.Done, NoticeSource.Languages, "Formatted ${File(location).name}", NoticePlaces.of(at, location))

    fun renamed(at: ICodeSnapshot, to: String, files: Int) =
        Notice(NoticeTone.Done, NoticeSource.Languages, "Renamed to $to in ${countOf(files, "file")}", NoticePlaces.workspace(at))

    fun notApplied(at: ICodeSnapshot, words: String) =
        Notice(NoticeTone.Problem, NoticeSource.Languages, "Couldn't apply the change", NoticePlaces.workspace(at), detail = words)

    /** What the user asked of a server, by what it is: save, remove, start, stop, restart, install. */
    enum class Asked(val verb: String) { Save("save"), Remove("remove"), Start("start"), Stop("stop"), Restart("restart"), Install("install") }

    fun notDone(at: ICodeSnapshot, asked: Asked, server: String?, words: String) = Notice(
        NoticeTone.Problem, NoticeSource.Languages, "Couldn't ${asked.verb} ${server ?: "the language server"}", serverPlace(at, server),
        detail = words, actions = listOf(NoticeAction.Show(NoticeTarget.Languages)),
    )

    fun notLoaded() = Notice(NoticeTone.Problem, NoticeSource.Languages, "Couldn't read the saved language servers")

    /** Not installed, said when someone asked to start it: what to do, in [words]. */
    fun missing(at: ICodeSnapshot, server: String, words: String) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Languages, "Install $server first", serverPlace(at, server),
        detail = words, actions = listOf(NoticeAction.Show(NoticeTarget.Languages)),
    )

    /** It could not start, or it stopped: its last [words] the output. */
    fun stopped(at: ICodeSnapshot, server: String, started: Boolean, words: String) = Notice(
        NoticeTone.Problem, NoticeSource.Languages, if (started) "$server stopped" else "$server couldn't start", serverPlace(at, server),
        output = words, actions = listOf(NoticeAction.Show(NoticeTarget.Languages)),
    )

    /** The server's own message — an error or a warning — in its words; the same one again folds. */
    fun said(at: ICodeSnapshot, server: String, error: Boolean, words: String) = Notice(
        if (error) NoticeTone.Problem else NoticeTone.HeadsUp, NoticeSource.Languages,
        if (error) "$server reported an error" else "$server reported a warning", serverPlace(at, server),
        detail = words, repeatKey = "language:$server:$words",
    )

    private fun serverPlace(at: ICodeSnapshot, server: String?) = NoticePlaces.workspace(at, subject = server) ?: server?.let { NoticePlace(subject = it) }
}
