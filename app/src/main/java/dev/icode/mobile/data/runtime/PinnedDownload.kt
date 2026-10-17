package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.io.hexDigestOf
import dev.icode.mobile.data.io.toHex
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Downloads a [PinnedFile] over HTTPS (docs/android-toolchain-plan.md §4.2): the root filesystem, and
 * every file a runtime tool fetches itself. The digest is computed as the bytes arrive, and what does
 * not match its pin — size or checksum — is deleted, never used. Progress is reported often enough
 * that a slow link still visibly moves; a cancelled coroutine stops between two reads.
 */
internal object PinnedDownload {
    /** [target] already holds exactly [file]: the size, then the digest, are what they must be. */
    fun isVerified(target: File, file: PinnedFile): Boolean =
        target.isFile && (file.bytes == null || target.length() == file.bytes) && hexDigestOf(target, file.checksum.algorithm) == file.checksum.hex

    /** Writes [file] to [target], reporting bytes received of the total (-1 when neither the pin nor the server says). */
    suspend fun download(file: PinnedFile, target: File, onProgress: suspend (received: Long, total: Long) -> Unit) {
        val digest = file.checksum.algorithm.newDigest()
        val connection = (URL(file.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
        }
        var received = 0L
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Download failed with HTTP ${connection.responseCode}")
            }
            val total = file.bytes ?: connection.contentLengthLong
            var lastReported = 0L
            var lastReportedAt = System.currentTimeMillis()
            // Reported before the first byte arrives. Without this the caller
            // stays in its previous state until a megabyte has landed, and on
            // a slow connection the screen claims nothing is happening for
            // minutes while the download is in fact running.
            onProgress(0L, total)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        received += count
                        // Sparingly by volume, but also on a timer, so a slow
                        // link still visibly moves instead of looking hung.
                        val now = System.currentTimeMillis()
                        if (received - lastReported >= PROGRESS_STEP_BYTES || now - lastReportedAt >= PROGRESS_INTERVAL_MILLIS) {
                            lastReported = received
                            lastReportedAt = now
                            onProgress(received, total)
                        }
                    }
                }
            }
            onProgress(received, total)
        } catch (stopped: Throwable) {
            target.delete()
            throw stopped
        } finally {
            connection.disconnect()
        }
        val actual = digest.digest().toHex()
        if ((file.bytes != null && received != file.bytes) || actual != file.checksum.hex) {
            target.delete()
            throw IOException("${file.url.substringAfterLast('/')} failed its checksum: the download is not the file iCode pins")
        }
    }

    private const val CONNECT_TIMEOUT_MILLIS = 30_000
    private const val READ_TIMEOUT_MILLIS = 60_000
    private const val DOWNLOAD_BUFFER_BYTES = 128 * 1024
    private const val PROGRESS_STEP_BYTES = 1024L * 1024L

    /** So a slow link still shows movement between megabytes. */
    private const val PROGRESS_INTERVAL_MILLIS = 700L
}
