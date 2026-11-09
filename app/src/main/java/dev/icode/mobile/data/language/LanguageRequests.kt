package dev.icode.mobile.data.language

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.LanguageAction
import dev.icode.mobile.domain.LanguageCompletionItem
import dev.icode.mobile.domain.LanguageCompletions
import dev.icode.mobile.domain.LanguagePlace
import dev.icode.mobile.domain.Severity
import dev.icode.mobile.domain.TextPosition
import dev.icode.mobile.domain.TextRange
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What the editor, the pages and the assistant ask of the servers (docs/lsp-plan.md §5.5): each request
 * goes to the ready servers that serve the file and said they offer it, after the file's text has
 * reached them, bounded by its own time (D7).
 */
internal class LanguageRequests(
    private val stateHolder: ICodeStateHolder,
    private val documents: LanguageDocumentSync,
    private val diagnostics: LanguageDiagnostics,
    private val pool: LanguagePool,
    val uris: LspUris,
    private val hosts: LanguageHosts,
) : LanguageCompletions {
    val edits = LanguageEdits(stateHolder, uris, hosts)

    /** A server's item, with the service that gave it, for resolving it there. */
    private class Token(val serviceId: String, val item: JsonObject)

    override suspend fun complete(location: String, position: TextPosition): List<LanguageCompletionItem> {
        val text = stateHolder.current.openDocuments.firstOrNull { it.location == location }?.text.orEmpty()
        val before = text.getOrNull(LspText.offsetOf(text, position) - 1)?.toString()
        return serving(location, { it.completion }) { sessions -> sessions.flatMap { session ->
            val trigger = before?.takeIf { it in session.capabilities.completionTriggers }
            val result = runCatching {
                session.request("textDocument/completion", at(location, position).with("context" to buildJsonObject {
                    put("triggerKind", if (trigger != null) 2 else 1)
                    trigger?.let { put("triggerCharacter", it) }
                }), COMPLETION_MILLIS)
            }.getOrNull()
            results(session).completions(result, session.capabilities.completionResolves).map { it.copy(token = Token(session.config.id, it.token as JsonObject)) }
        } }
    }

    override suspend fun resolve(location: String, item: LanguageCompletionItem): LanguageCompletionItem {
        val token = item.token as? Token ?: return item
        val session = pool.ready(token.serviceId)?.takeIf { it.capabilities.completionResolves } ?: return item
        val resolved = runCatching { session.request("completionItem/resolve", token.item, RESOLVE_MILLIS) as? JsonObject }.getOrNull() ?: return item
        val full = results(session).completion(resolved, resolvable = false) ?: return item
        return item.copy(
            detail = full.detail ?: item.detail,
            documentation = full.documentation ?: item.documentation,
            additionalEdits = full.additionalEdits.ifEmpty { item.additionalEdits },
            resolvable = false,
        )
    }

    /** Hover's text at [position]; null when nothing is there or no server offers it. */
    suspend fun hover(location: String, position: TextPosition): String? = serving(location, { it.hover }) { sessions ->
        sessions.firstNotNullOfOrNull { session ->
            val hover = session.request("textDocument/hover", at(location, position), HOVER_MILLIS) as? JsonObject
            LanguageJson.markup(hover?.get("contents"))
        }
    }

    suspend fun signature(location: String, position: TextPosition): String? = serving(location, { it.signatureHelp }) { sessions ->
        sessions.firstNotNullOfOrNull { results(it).signature(it.request("textDocument/signatureHelp", at(location, position), HOVER_MILLIS)) }
    }

    suspend fun definition(location: String, position: TextPosition): List<LanguagePlace> = serving(location, { it.definition }) { sessions ->
        sessions.flatMap { results(it).places(it.request("textDocument/definition", at(location, position), NAVIGATION_MILLIS)) }.distinct()
    }

    suspend fun references(location: String, position: TextPosition): List<LanguagePlace> = serving(location, { it.references }) { sessions ->
        val params = at(location, position).with("context" to buildJsonObject { put("includeDeclaration", true) })
        sessions.flatMap { results(it).places(it.request("textDocument/references", params, NAVIGATION_MILLIS)) }.distinct()
    }

    suspend fun documentSymbols(location: String): List<LanguagePlace> = serving(location, { it.documentSymbols }) { sessions ->
        sessions.firstNotNullOfOrNull { results(it).symbols(it.request("textDocument/documentSymbol", document(location), NAVIGATION_MILLIS), location) }.orEmpty()
    }

    /** Symbols matching [query] in the workspace, from every ready server that offers them. */
    suspend fun workspaceSymbols(query: String): List<LanguagePlace> = pool.running.filter { pool.ready(it.config.id) === it && it.capabilities.workspaceSymbols }.flatMap { session ->
        results(session).symbols(session.request("workspace/symbol", buildJsonObject { put("query", query) }, NAVIGATION_MILLIS), null)
    }.take(MAX_SYMBOLS)

    /** Formats the file; the number of files changed, or why not. */
    suspend fun format(location: String): Result<Int> = runCatching { serving(location, { it.formatting }) { sessions ->
        val session = sessions.firstOrNull() ?: throw LanguageFailure.NoServer("No running language server formats this file")
        val (size, spaces) = hosts.indentation()
        val result = session.request("textDocument/formatting", document(location).with("options" to buildJsonObject {
            put("tabSize", size)
            put("insertSpaces", spaces)
        }), EDIT_MILLIS)
        val uri = uris.uriOf(location) ?: throw LanguageFailure.NoServer("The runtime cannot see this file")
        val edit = buildJsonObject { put("changes", buildJsonObject { put(uri, result as? JsonArray ?: JsonArray(emptyList())) }) }
        edits.apply(session, edit)?.let { throw LanguageFailure.NotStarted(it) }
        if ((result as? JsonArray).isNullOrEmpty()) 0 else 1
    } }

    /** Renames the symbol at [position] to [newName] in every file the server changes; how many, or why not. */
    suspend fun rename(location: String, position: TextPosition, newName: String): Result<Int> = runCatching { serving(location, { it.rename }) { sessions ->
        val session = sessions.firstOrNull() ?: throw LanguageFailure.NoServer("No running language server renames in this file")
        if (session.capabilities.renamePrepares) {
            val prepared = session.request("textDocument/prepareRename", at(location, position), NAVIGATION_MILLIS)
            if (prepared is JsonNull) throw LanguageFailure.NoServer("Nothing here can be renamed")
        }
        val edit = session.request("textDocument/rename", at(location, position).with("newName" to JsonPrimitive(newName)), EDIT_MILLIS) as? JsonObject
            ?: throw LanguageFailure.NoServer("Nothing here can be renamed")
        edits.apply(session, edit)?.let { throw LanguageFailure.NotStarted(it) }
        edits.filesOf(edit)
    } }

    /** The code actions at [range], with what each needs to run. */
    suspend fun codeActions(location: String, range: TextRange): List<Triple<LanguageServerSession, LanguageAction, JsonObject>> =
        serving(location, { it.codeActions }) { sessions -> sessions.flatMap { session ->
            val context = buildJsonObject {
                put("diagnostics", buildJsonArray {
                    diagnostics.of(session.config.id, location).filter { overlaps(it.range, range) }.forEach { add(lspDiagnostic(it)) }
                })
                put("triggerKind", 1)
            }
            val params = document(location) + mapOf("range" to LspText.json(range), "context" to context)
            results(session).actions(session.request("textDocument/codeAction", JsonObject(params), NAVIGATION_MILLIS)).map { Triple(session, it.first, it.second) }
        } }

    /** Runs a code action or command from [codeActions]: its edit applied, then its command executed. */
    suspend fun run(session: LanguageServerSession, chosen: JsonObject): Result<Unit> = runCatching {
        var action = chosen
        if (action["edit"] == null && action["command"] !is JsonPrimitive && session.capabilities.codeActionsResolve) {
            action = session.request("codeAction/resolve", action, EDIT_MILLIS) as? JsonObject ?: action
        }
        (action["edit"] as? JsonObject)?.let { edit -> edits.apply(session, edit)?.let { throw LanguageFailure.NotStarted(it) } }
        // A Command itself, or a CodeAction's command.
        val command = if (action["command"] is JsonPrimitive) action else action["command"] as? JsonObject
        val name = command?.get("command") as? JsonPrimitive
        if (command != null && name != null) {
            session.request("workspace/executeCommand", buildJsonObject {
                put("command", name)
                command["arguments"]?.let { put("arguments", it) }
            }, EDIT_MILLIS)
        }
    }

    /**
     * The servers' problems in [location] once they have reported on its text now: the file opened
     * at them first if it was not (read from disk), waiting up to [waitMillis] for their report.
     */
    suspend fun diagnose(location: String, waitMillis: Long): List<Diagnostic> {
        val disk = disk(location)
        // Each serving server's next report is expected before anything is sent, so none is missed.
        val ids = documents.servingIds(location, disk)
        val expected = ids.associateWith { diagnostics.expect(it, location) }
        val wasOpen = ids.filter { pool.ready(it)?.documents?.isOpen(location) == true }.toSet()
        val sessions = documents.sessionsFor(location, disk, waitMillis)
        try {
            sessions.forEach { session ->
                val id = session.config.id
                val sent = documents.openOrFlush(session, location, disk) || id !in wasOpen
                if (sent) expected[id]?.let { diagnostics.await(it, waitMillis) }
            }
            return sessions.flatMap { diagnostics.of(it.config.id, location) }.sortedWith(compareBy({ it.severity.ordinal }, { it.range?.start?.line ?: 0 }))
        } finally {
            documents.release(location)
        }
    }

    /**
     * [block] with the ready servers serving [location] that [offers] the feature, after its text has
     * reached them. A file not open in the editor is opened at them from disk for [block] alone; a
     * server still starting is waited for when the file is not open (an assistant's question).
     */
    private suspend fun <T> serving(location: String, offers: (LspCapabilities) -> Boolean, block: suspend (List<LanguageServerSession>) -> T): T {
        documents.flush(location)
        val open = stateHolder.current.openDocuments.any { it.location == location }
        val sessions = documents.sessionsFor(location, disk(location), if (open) 0 else START_WAIT_MILLIS).filter { offers(it.capabilities) }
        try {
            return block(sessions)
        } finally {
            if (!open) documents.release(location)
        }
    }

    private fun disk(location: String): () -> String? = { runCatching { hosts.files.readDocument(location).text }.getOrNull() }

    private fun results(session: LanguageServerSession) = LspResults(uris, session.workspace)

    private fun document(location: String): JsonObject = buildJsonObject { put("textDocument", buildJsonObject { put("uri", uris.uriOf(location) ?: "") }) }

    private fun at(location: String, position: TextPosition): JsonObject = document(location).with("position" to LspText.json(position))

    private fun Map<String, JsonElement>.with(pair: Pair<String, JsonElement>): JsonObject = JsonObject(this + mapOf(pair))

    private fun overlaps(a: TextRange?, b: TextRange): Boolean = a != null && a.start.line <= b.end.line && b.start.line <= a.end.line

    private fun lspDiagnostic(problem: Diagnostic) = buildJsonObject {
        problem.range?.let { put("range", LspText.json(it)) }
        put("message", problem.message)
        put("severity", when (problem.severity) { Severity.Error -> 1; Severity.Warning -> 2; Severity.Info -> 3 })
        put("source", problem.source)
        problem.code?.let { put("code", it) }
    }

    /** A server's `workspace/applyEdit`, applied as iCode's own edits are (§5.7). */
    suspend fun applyServerEdit(session: LanguageServerSession, edit: JsonObject): Boolean = edits.apply(session, edit) == null

    companion object {
        const val COMPLETION_MILLIS = 3_000L
        const val RESOLVE_MILLIS = 1_500L
        const val HOVER_MILLIS = 2_000L
        const val NAVIGATION_MILLIS = 10_000L
        const val EDIT_MILLIS = 15_000L
        private const val MAX_SYMBOLS = 200
        /** How long an assistant's question waits for a server that serves the file to start. */
        const val START_WAIT_MILLIS = 30_000L
    }
}
