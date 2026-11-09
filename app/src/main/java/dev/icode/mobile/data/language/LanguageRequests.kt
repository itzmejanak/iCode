package dev.icode.mobile.data.language

import kotlinx.serialization.json.JsonObject

/**
 * What the editor, the pages and the assistant ask of the servers (docs/lsp-plan.md §5.5): each
 * request goes to the server that serves the file and offers it, after the file's text reaches it.
 */
internal class LanguageRequests(private val owner: LanguageServices) {
    /** A server's `workspace/applyEdit`: refused until edits are applied through their owners (§5.7). */
    suspend fun applyServerEdit(session: LanguageServerSession, edit: JsonObject): Boolean = false
}
