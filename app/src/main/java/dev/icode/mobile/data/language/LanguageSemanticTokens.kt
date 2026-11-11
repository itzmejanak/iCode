package dev.icode.mobile.data.language

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.LanguageHighlights
import dev.icode.mobile.domain.SemanticTokens
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The editor's semantic tokens (docs/syntax-highlighting-plan.md D8): asked of the first ready server
 * that serves the open file, has it open and offers them, after the file's text has reached it. A
 * server that answers deltas is asked for the change since its last answer for the file. Nothing is
 * sent, not even the file's pending change, when no server offers them.
 */
internal class LanguageSemanticTokens(
    private val stateHolder: ICodeStateHolder,
    private val documents: LanguageDocumentSync,
    private val pool: LanguagePool,
) : LanguageHighlights {
    /** A server's last answer for a file: what its next delta is counted from. */
    private class Last(val serviceId: String, val resultId: String, val data: IntArray)

    private val last = ConcurrentHashMap<String, Last>()

    override suspend fun semanticTokens(location: String): SemanticTokens? {
        val open = stateHolder.current.openDocuments.map { it.location }.toSet()
        // A closed file's last answer is not needed again.
        last.keys.retainAll(open)
        if (location !in open) return null
        val session = documents.servingIds(location) { null }.firstNotNullOfOrNull { id ->
            pool.ready(id)?.takeIf { it.capabilities.semanticTokens != null && it.documents.isOpen(location) }
        } ?: return null
        val legend = session.capabilities.semanticTokens ?: return null
        documents.flush(location)
        val sent = session.documents.opened().firstOrNull { it.location == location } ?: return null
        val previous = last[location]?.takeIf { it.serviceId == session.config.id && legend.delta }
        val answer = ask(session, sent, previous) ?: previous?.let { ask(session, sent, null) } ?: return null
        // The text changed at the server while it answered: the request the change made follows.
        if (session.documents.versionOf(location) != sent.version) return null
        answer.resultId?.let { last[location] = Last(session.config.id, it, answer.data) } ?: last.remove(location)
        return SemanticTokens(sent.text, LspSemanticTokens.decode(answer.data, legend))
    }

    /** The whole file's tokens, or the delta since [previous]; null when the server did not answer with them. */
    private suspend fun ask(session: LanguageServerSession, document: LspDocuments.Document, previous: Last?): LspSemanticTokens.Data? {
        val params = buildJsonObject {
            put("textDocument", buildJsonObject { put("uri", document.uri) })
            previous?.let { put("previousResultId", it.resultId) }
        }
        val method = if (previous != null) "textDocument/semanticTokens/full/delta" else "textDocument/semanticTokens/full"
        val result = try {
            session.request(method, params, SEMANTIC_MILLIS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return null
        }
        return LspSemanticTokens.dataOf(result as? JsonObject, previous?.data)
    }

    companion object {
        /** A server slower than this leaves the grammar's colours as they are. */
        const val SEMANTIC_MILLIS = 5_000L
    }
}
