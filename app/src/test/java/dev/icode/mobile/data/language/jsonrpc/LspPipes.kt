package dev.icode.mobile.data.language.jsonrpc

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** A one-way byte pipe for tests, without the thread rules of java.io's piped streams. */
internal class BytePipe {
    private val chunks = LinkedBlockingQueue<ByteArray>()

    val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) {
            chunks.put(b.copyOfRange(off, off + len))
        }
        override fun close() = chunks.put(EOF)
    }

    val input: InputStream = object : InputStream() {
        private var current: ByteArray = ByteArray(0)
        private var position = 0
        private var finished = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (finished) return -1
            while (position >= current.size) {
                val next = chunks.poll(10, TimeUnit.SECONDS) ?: error("Nothing came through the pipe")
                if (next === EOF) {
                    finished = true
                    return -1
                }
                current = next
                position = 0
            }
            val count = minOf(len, current.size - position)
            System.arraycopy(current, position, b, off, count)
            position += count
            return count
        }
    }

    private companion object {
        val EOF = ByteArray(0)
    }
}

/** The server's side of a connection in a test: frames read and written by hand. */
internal class FakeServerEnd {
    /** What the client writes; the server reads it. */
    val toServer = BytePipe()

    /** What the server writes; the client reads it. */
    val toClient = BytePipe()

    fun receive(): JsonObject = Json.parseToJsonElement(checkNotNull(LspFraming.read(toServer.input))).jsonObject

    fun send(json: String) = LspFraming.write(toClient.output, json)

    fun hangUp() = toClient.output.close()
}
