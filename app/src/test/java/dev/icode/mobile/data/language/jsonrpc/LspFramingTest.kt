package dev.icode.mobile.data.language.jsonrpc

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LspFramingTest {
    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8)

    /** A stream that hands out one byte per read, as a slow pipe may. */
    private fun trickle(data: ByteArray) = object : InputStream() {
        private val inner = ByteArrayInputStream(data)
        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = if (len == 0) 0 else inner.read(b, off, 1)
    }

    @Test
    fun `a frame written is read back whole, its length counted in bytes`() {
        val out = ByteArrayOutputStream()
        LspFraming.write(out, """{"text":"a𐐀é"}""")
        val written = out.toString(Charsets.UTF_8.name())
        assertEquals("Content-Length: ${bytes("""{"text":"a𐐀é"}""").size}\r\n\r\n", written.substringBefore("{"))
        assertEquals("""{"text":"a𐐀é"}""", LspFraming.read(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun `frames split across reads and joined in one read both come through`() {
        val two = bytes("Content-Length: 2\r\n\r\n{}Content-Type: application/vscode-jsonrpc; charset=utf-8\r\nContent-Length: 4\r\n\r\nnull")
        val stream = trickle(two)
        assertEquals("{}", LspFraming.read(stream))
        assertEquals("null", LspFraming.read(stream))
        assertNull(LspFraming.read(stream))
    }

    @Test
    fun `the end of the stream inside a message is a protocol failure`() {
        assertThrows(LspFraming.ProtocolException::class.java) { LspFraming.read(ByteArrayInputStream(bytes("Content-Length: 10\r\n\r\n{}"))) }
        assertThrows(LspFraming.ProtocolException::class.java) { LspFraming.read(ByteArrayInputStream(bytes("Content-Len"))) }
    }

    @Test
    fun `a header without a length, or past the bounds, ends the connection`() {
        assertThrows(LspFraming.ProtocolException::class.java) { LspFraming.read(ByteArrayInputStream(bytes("X: 1\r\n\r\n{}"))) }
        val huge = "Content-Length: ${LspFraming.MAX_BODY_BYTES + 1}\r\n\r\n"
        assertThrows(LspFraming.ProtocolException::class.java) { LspFraming.read(ByteArrayInputStream(bytes(huge))) }
        val long = "X-Pad: " + "a".repeat(LspFraming.MAX_HEADER_BYTES) + "\r\n\r\n"
        assertThrows(LspFraming.ProtocolException::class.java) { LspFraming.read(ByteArrayInputStream(bytes(long))) }
    }
}
