package dev.icode.mobile.data.io

import java.io.File
import java.security.MessageDigest

/**
 * SHA-256 as lowercase hex — the form of every digest iCode stores: file
 * fingerprints, draft file names, settings keys. A change here would orphan
 * stored drafts and settings, so the output is pinned by test.
 */
object Sha256 {
    fun hex(bytes: ByteArray): String = hexDigest(bytes, DigestAlgorithm.Sha256)

    fun hex(text: String): String = hex(text.toByteArray(Charsets.UTF_8))
}

/**
 * The digests iCode computes, here and nowhere else: SHA-256, its own; SHA-1 only where another's
 * pin is SHA-1 — Google's SDK repository, and sdkmanager's record of an accepted licence.
 */
enum class DigestAlgorithm(private val standardName: String) {
    Sha256("SHA-256"),
    Sha1("SHA-1");

    /** A digest to feed as bytes arrive. */
    fun newDigest(): MessageDigest = MessageDigest.getInstance(standardName)
}

fun hexDigest(bytes: ByteArray, algorithm: DigestAlgorithm): String = algorithm.newDigest().digest(bytes).toHex()

/** [file]'s digest by [algorithm], read in chunks rather than whole, as lowercase hex. */
fun hexDigestOf(file: File, algorithm: DigestAlgorithm): String {
    val digest = algorithm.newDigest()
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

/** Lowercase hex, the one form a digest is stored or compared in. */
fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
