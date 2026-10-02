package dev.icode.mobile.data.assistant.koog

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

/**
 * A server on this machine for Koog's real clients — on Ktor and OkHttp, as on the phone — to talk
 * to. On plain sockets: Android's unit tests have no `com.sun.net.httpserver`. One request per
 * connection, which it closes after answering; each request is kept, in order.
 */
internal class LocalHttpServer(private val answer: (Request) -> Response) : AutoCloseable {
    data class Request(val method: String, val path: String, val body: String)

    data class Response(val status: Int, val contentType: String, val body: String)

    private val socket = ServerSocket(0, BACKLOG, InetAddress.getByName("127.0.0.1"))
    val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())

    /** `http://127.0.0.1:<port>` and then [path]. */
    fun address(path: String): String = "http://127.0.0.1:${socket.localPort}$path"

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val connection = runCatching { socket.accept() }.getOrNull() ?: break
                connection.use(::serve)
            }
        }
    }

    private fun serve(connection: Socket) {
        val input = connection.getInputStream()
        val start = line(input)?.split(' ') ?: return
        val headers = generateSequence { line(input)?.takeIf(String::isNotEmpty) }
            .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val body = when {
            headers["transfer-encoding"].equals("chunked", ignoreCase = true) -> chunked(input)
            else -> input.readNBytes(headers["content-length"]?.toIntOrNull() ?: 0)
        }
        val request = Request(start[0], start.getOrElse(1) { "/" }, body.decodeToString())
        requests += request
        val response = answer(request)
        val bytes = response.body.toByteArray()
        val head = "HTTP/1.1 ${response.status} Answered\r\nContent-Type: ${response.contentType}\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        connection.getOutputStream().run {
            write(head.toByteArray())
            write(bytes)
            flush()
        }
    }

    /** One header line, in bytes as sent, without its CRLF; null at the end of the stream. */
    private fun line(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte == -1) return if (bytes.size() == 0) null else bytes.toString(Charsets.ISO_8859_1.name())
            if (byte == '\n'.code) return bytes.toString(Charsets.ISO_8859_1.name()).removeSuffix("\r")
            bytes.write(byte)
        }
    }

    private fun chunked(input: InputStream): ByteArray {
        val body = ByteArrayOutputStream()
        while (true) {
            val size = line(input)?.substringBefore(';')?.trim()?.toIntOrNull(HEX) ?: break
            if (size == 0) break
            body.write(input.readNBytes(size))
            line(input)
        }
        return body.toByteArray()
    }

    override fun close() = socket.close()

    private companion object {
        const val BACKLOG = 8
        const val HEX = 16
    }
}
