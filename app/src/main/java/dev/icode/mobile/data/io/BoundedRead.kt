package dev.icode.mobile.data.io

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * The whole stream, refused once it passes [maxBytes]: a file the app would
 * hold in memory must not be allowed to exhaust it.
 *
 * @throws IOException with [tooLargeMessage] when the stream is larger.
 */
fun InputStream.readAtMost(maxBytes: Int, tooLargeMessage: String): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) throw IOException(tooLargeMessage)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
