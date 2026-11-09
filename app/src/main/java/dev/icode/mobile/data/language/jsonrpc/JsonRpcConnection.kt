package dev.icode.mobile.data.language.jsonrpc

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * JSON-RPC 2.0 over [LspFraming] (docs/lsp-plan.md §5.2): one writer, so every request and
 * notification goes out in the order it was sent; one reader, which hands the server's
 * notifications to [handler] in the order they came and answers its requests off the reader.
 * Every request is bounded by its own timeout, and a timed-out or cancelled one is cancelled at
 * the server too (`$/cancelRequest`).
 */
internal class JsonRpcConnection(
    input: InputStream,
    output: OutputStream,
    private val scope: CoroutineScope,
    private val handler: Handler,
) {
    /** What the server asks of iCode, and tells it. */
    interface Handler {
        /** A request from the server: its result (null for JSON null), or a [JsonRpcException] for its error. */
        suspend fun request(method: String, params: JsonElement?): JsonElement?

        /** A notification, on the reader: quick, and never throwing. */
        fun notification(method: String, params: JsonElement?)
    }

    /** The server answered with an error. */
    class JsonRpcException(val code: Int, message: String) : Exception(message)

    /** The server did not answer [method] within [millis]. */
    class TimeoutException(val method: String, val millis: Long) : Exception("$method had no answer in ${millis / 1000.0} s")

    /** The connection ended before an answer came. */
    class ClosedException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val reader = BufferedInputStream(input, BUFFER_BYTES)
    private val writer = BufferedOutputStream(output, BUFFER_BYTES)
    private val outbox = Channel<String>(Channel.UNLIMITED)
    private val ids = AtomicLong()
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val ended = CompletableDeferred<Throwable?>()

    /** Completes when the connection ends: null at the server's end of stream, else why. */
    val closed: Deferred<Throwable?> get() = ended

    fun start() {
        scope.launch(Dispatchers.IO) { write() }
        scope.launch(Dispatchers.IO) { read() }
    }

    /** [method]'s result; throws [JsonRpcException], [TimeoutException] or [ClosedException]. */
    suspend fun request(method: String, params: JsonElement?, timeoutMillis: Long): JsonElement {
        val id = ids.incrementAndGet()
        val answer = CompletableDeferred<JsonElement>()
        pending[id] = answer
        try {
            if (ended.isCompleted || !send(message(id, method, params))) throw ClosedException("The language server's connection has ended")
            return withTimeout(timeoutMillis) { answer.await() }
        } catch (timeout: TimeoutCancellationException) {
            cancelAtServer(id)
            throw TimeoutException(method, timeoutMillis)
        } catch (cancelled: CancellationException) {
            cancelAtServer(id)
            throw cancelled
        } finally {
            pending.remove(id)
        }
    }

    fun notify(method: String, params: JsonElement?) {
        send(buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            params?.let { put("params", it) }
        }.toString())
    }

    /** Ends the connection from iCode's side: what is queued is written, then the stream closes. */
    fun close() {
        outbox.close()
    }

    private fun send(text: String): Boolean = outbox.trySend(text).isSuccess

    private fun cancelAtServer(id: Long) {
        if (!ended.isCompleted) notify("\$/cancelRequest", buildJsonObject { put("id", id) })
    }

    private suspend fun write() {
        try {
            for (text in outbox) LspFraming.write(writer, text)
        } catch (failure: Exception) {
            end(failure)
        } finally {
            runCatching { writer.close() }
        }
    }

    private fun read() {
        try {
            while (true) {
                val text = LspFraming.read(reader) ?: break
                val message = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: continue
                take(message)
            }
            end(null)
        } catch (failure: Exception) {
            end(failure)
        }
    }

    private fun take(message: JsonObject) {
        val method = (message["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val id = message["id"]?.takeUnless { it is JsonNull }
        when {
            method == null -> answer(id, message)
            id != null -> scope.launch(Dispatchers.IO) { respond(id, method, message["params"]) }
            else -> runCatching { handler.notification(method, message["params"]) }
        }
    }

    private fun answer(id: JsonElement?, message: JsonObject) {
        val key = (id as? JsonPrimitive)?.longOrNull ?: return
        val waiting = pending.remove(key) ?: return
        val error = message["error"] as? JsonObject
        if (error != null) {
            val code = error["code"]?.jsonPrimitive?.intOrNull ?: INTERNAL_ERROR
            val words = (error["message"] as? JsonPrimitive)?.content ?: "The language server refused"
            waiting.completeExceptionally(JsonRpcException(code, words))
        } else {
            waiting.complete(message["result"] ?: JsonNull)
        }
    }

    private suspend fun respond(id: JsonElement, method: String, params: JsonElement?) {
        val reply = try {
            val result = handler.request(method, params) ?: JsonNull
            buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val code = (failure as? JsonRpcException)?.code ?: INTERNAL_ERROR
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("error", buildJsonObject { put("code", code); put("message", failure.message ?: method) })
            }
        }
        send(reply.toString())
    }

    private fun end(failure: Throwable?) {
        if (!ended.complete(failure)) return
        outbox.close()
        val closed = ClosedException(failure?.message?.let { "The language server's connection ended: $it" } ?: "The language server ended", failure)
        pending.values.forEach { it.completeExceptionally(closed) }
        pending.clear()
    }

    private fun message(id: Long, method: String, params: JsonElement?) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("method", method)
        params?.let { put("params", it) }
    }.toString()

    companion object {
        const val METHOD_NOT_FOUND = -32601
        const val INTERNAL_ERROR = -32603
        const val REQUEST_CANCELLED = -32800
        const val CONTENT_MODIFIED = -32801
        private const val BUFFER_BYTES = 64 * 1024
    }
}
