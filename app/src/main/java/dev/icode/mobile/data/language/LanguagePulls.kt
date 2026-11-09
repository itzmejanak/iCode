package dev.icode.mobile.data.language

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Problems asked for (`textDocument/diagnostic`, LSP 3.17 pull diagnostics) from the servers that
 * offer it, after each change of a file they have open: one request a file at a time, a newer one
 * cancelling the older, and the server's `resultId` sent back so an unchanged report costs nothing.
 */
internal class LanguagePulls(private val scope: CoroutineScope, private val diagnostics: LanguageDiagnostics) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val resultIds = ConcurrentHashMap<String, String>()

    fun pull(session: LanguageServerSession, location: String, uri: String) {
        if (!session.capabilities.pullDiagnostics) return
        val key = session.config.id + "\u0000" + location
        jobs.remove(key)?.cancel()
        jobs[key] = scope.launch {
            try {
                val report = session.request("textDocument/diagnostic", buildJsonObject {
                    put("textDocument", buildJsonObject { put("uri", uri) })
                    resultIds[key]?.let { put("previousResultId", it) }
                }, PULL_MILLIS) as? JsonObject ?: return@launch
                LanguageJson.string(report, "resultId")?.let { resultIds[key] = it } ?: resultIds.remove(key)
                if (LanguageJson.string(report, "kind") == "full") diagnostics.pulled(session, location, report["items"])
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Refused (ServerCancelled, ContentModified), timed out or ended: the next change asks again.
                Unit
            } finally {
                jobs.remove(key, coroutineContext[Job])
            }
        }
    }

    /** [session] ended: what it was asked goes, and its result IDs with it. */
    fun forget(session: LanguageServerSession) {
        val prefix = session.config.id + "\u0000"
        jobs.keys.filter { it.startsWith(prefix) }.forEach { jobs.remove(it)?.cancel() }
        resultIds.keys.removeAll { it.startsWith(prefix) }
    }

    private companion object {
        const val PULL_MILLIS = 15_000L
    }
}
