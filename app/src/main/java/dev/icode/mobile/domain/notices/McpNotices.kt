package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.ICodeSnapshot

/**
 * What the MCP servers tell (docs/engineering-rules.md): by the server's name, said once
 * — in the place, not again in the detail — with the words of [McpFailures] or the server's.
 */
object McpNotices {
    fun notConnected(at: ICodeSnapshot, server: String) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Mcp, "Connect $server first", placeOf(at, server),
        actions = listOf(NoticeAction.Show(NoticeTarget.McpServers)),
    )

    /** A prompt's or a resource's text that could not be read from [server]. */
    fun notRead(at: ICodeSnapshot, server: String, words: String) =
        Notice(NoticeTone.Problem, NoticeSource.Mcp, "Couldn't get the text from $server", placeOf(at, server), detail = words)

    fun notSignedIn(at: ICodeSnapshot, server: String?, words: String) = Notice(
        NoticeTone.Problem, NoticeSource.Mcp, "Couldn't sign in to ${server ?: "the server"}", server?.let { placeOf(at, it) },
        detail = words, actions = listOf(NoticeAction.Show(NoticeTarget.McpServers)),
    )

    /** A change on the MCP servers' page refused: what to do, in the rules' words — "Name the server". */
    fun notChanged(at: ICodeSnapshot, server: String?, words: String?) = Notice(
        NoticeTone.HeadsUp, NoticeSource.Mcp, words ?: "Couldn't change the MCP servers", server?.let { placeOf(at, it) },
    )

    /** What an import left out: kept, quietly. */
    fun notImported(ignored: List<String>) =
        Notice(NoticeTone.Info, NoticeSource.Mcp, "Some servers were not imported", detail = "iCode couldn't read ${ignored.joinToString()}.")

    private fun placeOf(at: ICodeSnapshot, server: String) = NoticePlaces.workspace(at, subject = server) ?: NoticePlace(subject = server)
}
