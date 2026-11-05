package dev.icode.mobile.data.assistant

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException

/**
 * What a network failure reaching [host] was, in the words the pages show — a provider's (KoogFailures)
 * and an MCP server's (McpFailures) alike; null for anything that is not one.
 */
internal fun networkWords(cause: Throwable, host: String): String? = when (cause) {
    // OkHttp's refusal of plain http:// under Android's network security config.
    is UnknownServiceException -> "Android blocks plain http:// to $host — use https://, or a server on this phone (127.0.0.1)"
    is UnknownHostException -> "Unable to reach $host — check the address and the connection"
    // Ktor's timeouts: a ConnectException and an IOException, so they come first.
    is ConnectTimeoutException, is HttpRequestTimeoutException, is SocketTimeoutException -> "$host did not answer in time"
    is ConnectException -> "Unable to connect to $host — is the server running?"
    is SSLException -> "A secure connection to $host failed"
    else -> null
}
