package dev.icode.mobile.data.assistant.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import org.json.JSONObject

/**
 * Remote servers' HTTP (docs/assistant-and-mcp.md): one client for MCP in the process, with SSE. Streamable
 * HTTP comes first; an address that answers 400, 404 or 405 with no JSON-RPC error of its own speaks
 * the older HTTP+SSE, as the spec's backward compatibility says.
 */
internal class McpRemote(val http: HttpClient = HttpClient(OkHttp) { install(SSE) }) {
    fun streamable(url: String, headers: Map<String, String>): Transport =
        StreamableHttpClientTransport(http, url, requestBuilder = { headers.forEach { (name, value) -> header(name, value) } })

    @Suppress("DEPRECATION")
    fun older(url: String, headers: Map<String, String>): Transport =
        SseClientTransport(http, url, requestBuilder = { headers.forEach { (name, value) -> header(name, value) } })

    /** [failure] says the address speaks the older transport, not that it refused. */
    fun speaksOlder(failure: Throwable): Boolean {
        val http = generateSequence(failure) { it.cause }.firstNotNullOfOrNull { it as? StreamableHttpError } ?: return false
        return http.code in FALLBACK && !jsonRpcError(http.message)
    }

    private fun jsonRpcError(body: String?): Boolean =
        runCatching { JSONObject(body.orEmpty()).has("error") }.getOrDefault(false)

    private companion object {
        val FALLBACK = setOf(400, 404, 405)
    }
}
