package dev.icode.mobile.data.io

import java.io.File
import java.security.MessageDigest

/**
 * SHA-256 as lowercase hex — the form of every digest iCode stores: file
 * fingerprints, draft file names, settings keys, the runtime archive check.
 * A change here would orphan stored drafts and settings, so the output is pinned by test.
 */
object Sha256 {
    fun hex(bytes: ByteArray): String = newDigest().digest(bytes).toHex()

    fun hex(text: String): String = hex(text.toByteArray(Charsets.UTF_8))

    /** A file of any size, read in chunks rather than whole. */
    fun hexOf(file: File): String {
        val digest = newDigest()
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun newDigest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
}
