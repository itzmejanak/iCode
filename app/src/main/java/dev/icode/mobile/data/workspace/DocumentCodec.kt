package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.TextEncoding
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal data class DecodedText(
    val text: String,
    val encoding: TextEncoding,
    val hasByteOrderMark: Boolean,
    val lineEnding: LineEnding,
)

internal object DocumentCodec {
    fun decode(bytes: ByteArray): DecodedText {
        val (encoding, bomSize) = detectEncoding(bytes)
        val content = bytes.copyOfRange(bomSize, bytes.size)
        if (encoding == TextEncoding.Utf8 && content.any { it == 0.toByte() }) {
            throw IOException("Binary files cannot be opened in the text editor")
        }
        val charset = when (encoding) {
            TextEncoding.Utf8 -> StandardCharsets.UTF_8
            TextEncoding.Utf16LittleEndian -> StandardCharsets.UTF_16LE
            TextEncoding.Utf16BigEndian -> StandardCharsets.UTF_16BE
        }
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = runCatching { decoder.decode(ByteBuffer.wrap(content)).toString() }
            .getOrElse { throw IOException("Unsupported or malformed text encoding", it) }
        return DecodedText(
            text = text,
            encoding = encoding,
            hasByteOrderMark = bomSize > 0,
            lineEnding = detectLineEnding(text),
        )
    }

    fun encode(text: String, encoding: TextEncoding, includeByteOrderMark: Boolean): ByteArray {
        val charset = when (encoding) {
            TextEncoding.Utf8 -> StandardCharsets.UTF_8
            TextEncoding.Utf16LittleEndian -> StandardCharsets.UTF_16LE
            TextEncoding.Utf16BigEndian -> StandardCharsets.UTF_16BE
        }
        val content = text.toByteArray(charset)
        if (!includeByteOrderMark) return content
        val mark = when (encoding) {
            TextEncoding.Utf8 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            TextEncoding.Utf16LittleEndian -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            TextEncoding.Utf16BigEndian -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        }
        return mark + content
    }

    private fun detectEncoding(bytes: ByteArray): Pair<TextEncoding, Int> = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> TextEncoding.Utf8 to 3
        bytes.startsWith(0xFF, 0xFE) -> TextEncoding.Utf16LittleEndian to 2
        bytes.startsWith(0xFE, 0xFF) -> TextEncoding.Utf16BigEndian to 2
        looksLikeUtf16(bytes, nullIndex = 1) -> TextEncoding.Utf16LittleEndian to 0
        looksLikeUtf16(bytes, nullIndex = 0) -> TextEncoding.Utf16BigEndian to 0
        else -> TextEncoding.Utf8 to 0
    }

    private fun looksLikeUtf16(bytes: ByteArray, nullIndex: Int): Boolean {
        if (bytes.size < 4) return false
        var pairs = 0
        var expectedNulls = 0
        var oppositeNulls = 0
        for (index in bytes.indices step 2) {
            if (index + 1 >= bytes.size) break
            pairs += 1
            if (bytes[index + nullIndex] == 0.toByte()) expectedNulls += 1
            if (bytes[index + (1 - nullIndex)] == 0.toByte()) oppositeNulls += 1
        }
        return expectedNulls * 10 >= pairs * 6 && oppositeNulls * 10 <= pairs
    }

    private fun detectLineEnding(text: String): LineEnding = when {
        "\r\n" in text -> LineEnding.CrLf
        '\n' in text -> LineEnding.Lf
        '\r' in text -> LineEnding.Cr
        else -> LineEnding.None
    }

    private fun ByteArray.startsWith(vararg values: Int): Boolean =
        size >= values.size && values.indices.all { index -> this[index] == values[index].toByte() }
}
