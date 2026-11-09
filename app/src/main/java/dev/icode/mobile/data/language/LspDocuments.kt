package dev.icode.mobile.data.language

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One server's view of the documents it has open (docs/lsp-plan.md §5.6, D3): each one's version
 * and the text last sent, so a change is one splice from that text — however many keystrokes it
 * took — and versions only grow. Every message goes into the connection's one queue as it is made, so an
 * open, a change and the request after it reach the server in that order.
 */
internal class LspDocuments(private val send: (method: String, params: JsonObject) -> Unit) {
    class Document(val location: String, val uri: String, val languageId: String, val version: Int, val text: String)

    private val lock = Mutex()
    private val open = HashMap<String, Document>()
    /** Each open document's version, read without the lock: what a diagnostic's version is checked against. */
    private val versions = ConcurrentHashMap<String, Int>()

    /** Set from the server's capabilities once it has started. */
    @Volatile var capabilities: LspCapabilities? = null

    suspend fun opened(): List<Document> = lock.withLock { open.values.toList() }

    fun versionOf(location: String): Int? = versions[location]

    suspend fun isOpen(location: String): Boolean = lock.withLock { location in open }

    suspend fun open(location: String, uri: String, languageId: String, text: String) = lock.withLock {
        if (location in open || capabilities?.openClose != true) return@withLock
        open[location] = Document(location, uri, languageId, 1, text)
        versions[location] = 1
        send("textDocument/didOpen", buildJsonObject {
            put("textDocument", buildJsonObject {
                put("uri", uri)
                put("languageId", languageId)
                put("version", 1)
                put("text", text)
            })
        })
    }

    /** [text] is the document now: one change from what was sent, if it differs. Returns the version the server has. */
    suspend fun change(location: String, text: String): Int? = lock.withLock {
        val document = open[location] ?: return@withLock null
        if (document.text == text) return@withLock document.version
        val sync = capabilities?.sync ?: LspCapabilities.Sync.None
        if (sync == LspCapabilities.Sync.None) {
            open[location] = Document(location, document.uri, document.languageId, document.version, text)
            return@withLock document.version
        }
        val version = document.version + 1
        val change = if (sync == LspCapabilities.Sync.Incremental) LspText.change(document.text, text) else null
        send("textDocument/didChange", buildJsonObject {
            put("textDocument", buildJsonObject { put("uri", document.uri); put("version", version) })
            put("contentChanges", buildJsonArray {
                add(if (change != null) {
                    buildJsonObject { put("range", LspText.json(change.first)); put("text", change.second) }
                } else {
                    buildJsonObject { put("text", text) }
                })
            })
        })
        open[location] = Document(location, document.uri, document.languageId, version, text)
        versions[location] = version
        version
    }

    suspend fun saved(location: String) = lock.withLock {
        val document = open[location] ?: return@withLock
        val capabilities = capabilities ?: return@withLock
        if (!capabilities.save) return@withLock
        send("textDocument/didSave", buildJsonObject {
            put("textDocument", buildJsonObject { put("uri", document.uri) })
            if (capabilities.saveIncludesText) put("text", document.text)
        })
    }

    suspend fun close(location: String) = lock.withLock {
        val document = open.remove(location) ?: return@withLock
        versions.remove(location)
        send("textDocument/didClose", buildJsonObject { put("textDocument", buildJsonObject { put("uri", document.uri) }) })
    }

    /** The server ended: nothing is open at it any more. */
    suspend fun clear() = lock.withLock {
        open.clear()
        versions.clear()
    }
}
