package dev.icode.mobile.data.runtime

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the root filesystem archive over HTTP, reporting progress often
 * enough that a slow link still visibly moves. Verifying what arrived is the
 * installer's (its checksum is in the manifest), as unpacking it is
 * [RootfsExtractor]'s.
 */
internal class RootfsDownloader {
    /** Writes [url] to [target], reporting bytes received of the total (-1 when the server does not say). */
    suspend fun download(url: String, target: File, onProgress: suspend (received: Long, total: Long) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Download failed with HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            var received = 0L
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
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        // Sparingly by volume, but also on a timer, so a slow
                        // link still visibly moves instead of looking hung.
                        val now = System.currentTimeMillis()
                        if (received - lastReported >= PROGRESS_STEP_BYTES ||
                            now - lastReportedAt >= PROGRESS_INTERVAL_MILLIS
                        ) {
                            lastReported = received
                            lastReportedAt = now
                            onProgress(received, total)
                        }
                    }
                }
            }
            onProgress(received, total)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val DOWNLOAD_BUFFER_BYTES = 128 * 1024
        const val PROGRESS_STEP_BYTES = 1024L * 1024L

        /** So a slow link still shows movement between megabytes. */
        const val PROGRESS_INTERVAL_MILLIS = 700L
    }
}
