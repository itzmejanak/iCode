package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.TextEncoding
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentCodecTest {
    @Test
    fun `utf8 bom and crlf survive a round trip`() {
        val source = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "one\r\ntwo\r\n".toByteArray()

        val decoded = DocumentCodec.decode(source)

        assertEquals(TextEncoding.Utf8, decoded.encoding)
        assertEquals(LineEnding.CrLf, decoded.lineEnding)
        assertEquals(true, decoded.hasByteOrderMark)
        assertArrayEquals(source, DocumentCodec.encode(decoded.text, decoded.encoding, decoded.hasByteOrderMark))
    }

    @Test
    fun `bomless utf16 little endian is detected and preserved`() {
        val source = "hello\nworld".toByteArray(Charsets.UTF_16LE)

        val decoded = DocumentCodec.decode(source)

        assertEquals(TextEncoding.Utf16LittleEndian, decoded.encoding)
        assertEquals(LineEnding.Lf, decoded.lineEnding)
        assertEquals(source.toList(), DocumentCodec.encode(decoded.text, decoded.encoding, false).toList())
    }

    // Not text: the editor hands it to another app (docs/workspaces-and-files.md).
    @Test(expected = NotTextException::class)
    fun `binary data is rejected`() {
        DocumentCodec.decode(byteArrayOf(1, 0, 2, 3, 0, 4))
    }

    @Test(expected = NotTextException::class)
    fun `bytes no encoding reads are rejected`() {
        DocumentCodec.decode(byteArrayOf(0xC3.toByte(), 0x28))
    }
}
