package dev.icode.mobile.data.git

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * The line Git's `store` helper reads for a saved token, in a file that exists
 * only while one command needs it.
 */
internal object GitCredentialFile {

    fun line(host: String, username: String, token: String): String =
        "https://${encode(username)}:${encode(token)}@$host"

    /**
     * An owner-only file in [directory] holding [line]: `Files.createTempFile`
     * makes it read-write for its owner alone before a byte is written.
     */
    fun create(directory: File, line: String): File {
        directory.mkdirs()
        val path = Files.createTempFile(directory.toPath(), "icode-credential-", "")
        Files.write(path, (line + "\n").toByteArray(StandardCharsets.UTF_8))
        return path.toFile()
    }

    /** Percent-encodes every UTF-8 byte outside RFC 3986's unreserved characters. */
    fun encode(value: String): String = buildString {
        value.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
            val code = byte.toInt() and 0xFF
            val character = code.toChar()
            if (code < 0x80 && (character.isLetterOrDigit() || character in UNRESERVED)) {
                append(character)
            } else {
                append('%').append("%02X".format(code))
            }
        }
    }

    private const val UNRESERVED = "-._~"
}
