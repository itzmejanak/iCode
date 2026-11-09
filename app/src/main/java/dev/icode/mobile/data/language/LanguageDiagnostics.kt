package dev.icode.mobile.data.language

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.DiagnosticRules
import dev.icode.mobile.domain.Severity
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The language servers' problems in the open workspace (docs/lsp-plan.md §5.4): each server's list
 * for each file, replaced whole by its next one, dropped when it is about text older than what the
 * server has, and gone with the server or the workspace. Into the snapshot at most every
 * [THROTTLE_MILLIS], errors first, at most [MAX_SHOWN].
 */
internal class LanguageDiagnostics(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val uris: LspUris,
) {
    /** Service, then file, then its problems. */
    private val lists = ConcurrentHashMap<String, ConcurrentHashMap<String, List<Diagnostic>>>()
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var pending: Job? = null

    /** `textDocument/publishDiagnostics` from [session]. */
    fun published(session: LanguageServerSession, params: JsonObject) {
        val location = LanguageJson.string(params, "uri")?.let(uris::locationOf) ?: return
        val version = (params["version"] as? JsonPrimitive)?.intOrNull
        val current = session.documents.versionOf(location)
        // About text the server has since been sent a change to.
        if (version != null && current != null && version < current) return
        put(session, location, params["diagnostics"])
    }

    /** A `textDocument/diagnostic` report's items for [location]. */
    fun pulled(session: LanguageServerSession, location: String, items: JsonElement?) = put(session, location, items)

    fun clear(serviceId: String) {
        if (lists.remove(serviceId) != null) schedule()
    }

    fun clearAll() {
        lists.clear()
        pending?.cancel()
        pending = null
        stateHolder.update { it.copy(languageDiagnostics = emptyList()) }
    }

    /** What [serviceId] reported for [location] now. */
    fun of(serviceId: String, location: String): List<Diagnostic> = lists[serviceId]?.get(location).orEmpty()

    /** [serviceId]'s next report on [location], to wait for once what it reports on has been sent ([await]). */
    fun expect(serviceId: String, location: String): CompletableDeferred<Unit> = waiting.getOrPut(key(serviceId, location)) { CompletableDeferred() }

    /** Waits up to [timeoutMillis] for [next]; true when it came. */
    suspend fun await(next: CompletableDeferred<Unit>, timeoutMillis: Long): Boolean = withTimeoutOrNull(timeoutMillis) { next.await() } != null

    private fun put(session: LanguageServerSession, location: String, items: JsonElement?) {
        val file = uris.relative(session.workspace, location)
        val id = session.config.id
        if (file != null) {
            val problems = LanguageJson.objects(items).mapNotNull { diagnosticOf(it, file, session) }.take(MAX_PER_FILE)
            val files = lists.getOrPut(id) { ConcurrentHashMap() }
            if (problems.isEmpty()) files.remove(location) else files[location] = problems
            schedule()
        }
        waiting.remove(key(id, location))?.complete(Unit)
    }

    private fun diagnosticOf(item: JsonObject, file: String, session: LanguageServerSession): Diagnostic? {
        val range = LspText.rangeFrom(item["range"]) ?: return null
        val message = LanguageJson.markup(item["message"]) ?: return null
        return Diagnostic(
            severity = when ((item["severity"] as? JsonPrimitive)?.intOrNull) {
                2 -> Severity.Warning
                3, 4 -> Severity.Info
                // Absent: the client decides; editors show it as an error.
                else -> Severity.Error
            },
            file = file,
            message = message,
            location = DiagnosticRules.locationOf(range),
            source = LanguageJson.string(item, "source") ?: session.config.name,
            range = range,
            code = (item["code"] as? JsonPrimitive)?.content,
            serviceId = session.config.id,
        )
    }

    private fun schedule() {
        synchronized(this) {
            if (pending?.isActive == true) return
            pending = scope.launch {
                delay(THROTTLE_MILLIS)
                // A report from now on schedules the next publish: this one reads the lists after this line.
                synchronized(this@LanguageDiagnostics) { pending = null }
                val all = lists.values.flatMap { files -> files.values.flatten() }.sortedWith(DiagnosticRules.order).take(MAX_SHOWN)
                stateHolder.update { it.copy(languageDiagnostics = all) }
            }
        }
    }

    private fun key(serviceId: String, location: String) = "$serviceId\u0000$location"

    companion object {
        const val THROTTLE_MILLIS = 250L
        const val MAX_SHOWN = 2_000
        const val MAX_PER_FILE = 1_000
    }
}
