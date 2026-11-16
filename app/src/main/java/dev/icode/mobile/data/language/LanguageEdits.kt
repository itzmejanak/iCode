package dev.icode.mobile.data.language

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceFileSource
import dev.icode.mobile.domain.LanguageTextEdit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * What iCode needs from the rest of the backend to apply a server's edit (docs/editor-and-language-services.md):
 * an open file's text goes through the editor's own path, a closed one's through the file source.
 */
internal class LanguageHosts(
    val files: WorkspaceFileSource,
    /** An open file's new text, as typing would give it: it is one undo step in the editor. */
    val editOpen: (location: String, text: String) -> Unit,
    /** Files changed on disk: the tree, Git and search refresh. */
    val changedOnDisk: () -> Unit,
    /** The workspace's indentation, for formatting: its size and whether it is spaces. */
    val indentation: suspend () -> Pair<Int, Boolean>,
    /** [command] typed in a new terminal tab in the open workspace; false when no terminal could open. */
    val typeInTerminal: suspend (command: String) -> Boolean = { false },
)

/**
 * A `WorkspaceEdit` applied whole or not at all: text edits only, every file inside the open
 * workspace, and each open file still holding exactly the text the server was sent — else nothing
 * changes and the reason is returned.
 */
internal class LanguageEdits(private val stateHolder: ICodeStateHolder, private val uris: LspUris, private val hosts: LanguageHosts) {
    /** Null when applied; otherwise why not, in the user's words. */
    suspend fun apply(session: LanguageServerSession?, edit: JsonObject): String? {
        val byUri = editsOf(edit) ?: return "The language server's change creates, renames or deletes files, which iCode does not apply"
        if (byUri.isEmpty()) return null
        val workspace = stateHolder.current.activeWorkspaceLocation ?: return "No workspace is open"
        val planned = mutableListOf<Triple<String, String, Boolean>>()
        for ((uri, change) in byUri) {
            val location = uris.locationOf(uri) ?: return "The change is to a file iCode cannot reach: $uri"
            if (uris.relative(workspace, location) == null) return "The change reaches outside the workspace: $location"
            val open = stateHolder.current.openDocuments.firstOrNull { it.location == location }
            val text = open?.text ?: runCatching { hosts.files.readDocument(location).text }.getOrElse { return "Could not read ${location.substringAfterLast('/')}" }
            if (open != null && session != null) {
                val sent = session.documents.opened().firstOrNull { it.location == location }
                val stale = (change.first != null && sent?.version != change.first) || (sent != null && sent.text != open.text)
                if (stale) return "${open.name} changed since the language server answered; try again"
            }
            val result = applyTo(text, change.second) ?: return "The language server's edits to ${location.substringAfterLast('/')} overlap"
            if (result != text) planned += Triple(location, result, open != null)
        }
        // Every file read and every edit made before anything is written.
        planned.filter { !it.third }.forEach { (location, text) ->
            runCatching { hosts.files.writeDocument(hosts.files.readDocument(location), text) }.onFailure { return "Could not write ${location.substringAfterLast('/')}: ${it.message}" }
        }
        planned.filter { it.third }.forEach { (location, text) -> hosts.editOpen(location, text) }
        if (planned.any { !it.third }) hosts.changedOnDisk()
        return null
    }

    /** How many files [edit] changes; for an answer's words. */
    fun filesOf(edit: JsonObject): Int = editsOf(edit)?.size ?: 0

    /** Each file's version (when the server gave one) and edits; null when it holds a file operation. */
    private fun editsOf(edit: JsonObject): Map<String, Pair<Int?, List<LanguageTextEdit>>>? {
        val results = LspResults(uris, "")
        val documentChanges = LanguageJson.objects(edit["documentChanges"])
        if (documentChanges.isNotEmpty()) {
            if (documentChanges.any { it.containsKey("kind") }) return null
            return documentChanges.mapNotNull { change ->
                val document = change["textDocument"] as? JsonObject ?: return@mapNotNull null
                val uri = LanguageJson.string(document, "uri") ?: return@mapNotNull null
                uri to (((document["version"] as? JsonPrimitive)?.intOrNull) to results.edits(change["edits"]))
            }.groupBy({ it.first }, { it.second }).mapValues { (_, all) -> all.first().first to all.flatMap { it.second } }
        }
        val changes = edit["changes"] as? JsonObject ?: return emptyMap()
        return changes.mapValues { (_, edits) -> null to results.edits(edits) }
    }

    companion object {
        /** [edits] applied to [text], each in [text]'s own places; null when two overlap. */
        fun applyTo(text: String, edits: List<LanguageTextEdit>): String? {
            val spans = edits.map { Triple(LspText.offsetOf(text, it.range.start), LspText.offsetOf(text, it.range.end), it.newText) }
                .sortedWith(compareBy({ it.first }, { it.second }))
            spans.zipWithNext().forEach { (a, b) -> if (b.first < a.second) return null }
            val out = StringBuilder(text.length)
            var at = 0
            spans.forEach { (start, end, newText) ->
                out.append(text, at, start).append(newText)
                at = maxOf(at, end)
            }
            return out.append(text, at, text.length).toString()
        }
    }
}
