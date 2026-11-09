package dev.icode.mobile.data.language.jsonrpc

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The base protocol's frames (LSP 3.17 "Base Protocol"): `Content-Length: n\r\n`, any other headers,
 * `\r\n`, then n bytes of UTF-8 JSON. The only place iCode reads or writes them (docs/lsp-plan.md §5.1).
 * Both bounds end the connection rather than buffer without limit (D7).
 */
internal object LspFraming {
    const val MAX_HEADER_BYTES = 8 * 1024
    const val MAX_BODY_BYTES = 64 * 1024 * 1024

    /** A frame the other side broke: the connection cannot go on. */
    class ProtocolException(message: String) : IOException(message)

    /** The next frame's body, or null at the end of the stream between frames. */
    fun read(input: InputStream): String? {
        val header = readHeader(input) ?: return null
        val length = contentLength(header)
        if (length < 0 || length > MAX_BODY_BYTES) throw ProtocolException("A message of $length bytes is outside 0–$MAX_BODY_BYTES")
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count < 0) throw ProtocolException("The stream ended inside a message")
            read += count
        }
        return String(body, Charsets.UTF_8)
    }

    /** [json] as one frame; flushed, so the other side has it whole. */
    fun write(output: OutputStream, json: String) {
        val body = json.toByteArray(Charsets.UTF_8)
        output.write("Content-Length: ${body.size}\r\n\r\n".toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    /** The header block up to its blank line; null when the stream ends before any byte of it. */
    private fun readHeader(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val byte = input.read()
            if (byte < 0) {
                if (bytes.size() == 0) return null
                throw ProtocolException("The stream ended inside a header")
            }
            bytes.write(byte)
            if (bytes.size() > MAX_HEADER_BYTES) throw ProtocolException("A header longer than $MAX_HEADER_BYTES bytes")
            last4 = (last4 shl 8) or byte
            if (last4 == CRLF_CRLF) return bytes.toString(Charsets.US_ASCII.name())
        }
    }

    private fun contentLength(header: String): Int {
        val line = header.split("\r\n").firstOrNull { it.substringBefore(':').trim().equals("Content-Length", ignoreCase = true) }
            ?: throw ProtocolException("A message without Content-Length")
        return line.substringAfter(':').trim().toIntOrNull() ?: throw ProtocolException("Content-Length is not a number: $line")
    }

    private const val CRLF_CRLF = 0x0D0A0D0A
}
