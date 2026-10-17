package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.assistant.koog.LocalHttpServer
import dev.icode.mobile.data.io.DigestAlgorithm
import dev.icode.mobile.data.io.hexDigest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedDownloadTest {
    private val body = "the pinned file's bytes\n".repeat(64)
    private val server = LocalHttpServer { LocalHttpServer.Response(200, "application/zip", body) }
    private val target = File(Files.createTempDirectory("pinned").toFile(), "file.zip")

    @After
    fun close() {
        server.close()
        target.parentFile?.deleteRecursively()
    }

    @Test
    fun `a download matching its pin is kept, verified, and reports its end`() = runBlocking {
        val pin = pin(DigestAlgorithm.Sha1, bytes = body.length.toLong())
        val reported = mutableListOf<Pair<Long, Long>>()
        PinnedDownload.download(pin, target) { received, total -> reported += received to total }
        assertEquals(body, target.readText())
        assertTrue(PinnedDownload.isVerified(target, pin))
        assertEquals(0L to body.length.toLong(), reported.first())
        assertEquals(body.length.toLong() to body.length.toLong(), reported.last())
    }

    @Test
    fun `a download with another checksum is deleted and refused`() {
        val pin = PinnedFile(server.address("/file.zip"), null, Checksum(DigestAlgorithm.Sha256, "0".repeat(64)))
        val refused = assertThrows(IOException::class.java) { runBlocking { PinnedDownload.download(pin, target) { _, _ -> } } }
        assertEquals("file.zip failed its checksum: the download is not the file iCode pins", refused.message)
        assertFalse(target.exists())
    }

    @Test
    fun `a download of another size is deleted and refused, though its digest matches`() {
        val pin = pin(DigestAlgorithm.Sha256, bytes = body.length + 1L)
        assertThrows(IOException::class.java) { runBlocking { PinnedDownload.download(pin, target) { _, _ -> } } }
        assertFalse(target.exists())
    }

    @Test
    fun `an answer other than success is a failure, and nothing is kept`() {
        LocalHttpServer { LocalHttpServer.Response(404, "text/plain", "gone") }.use { missing ->
            val pin = PinnedFile(missing.address("/file.zip"), null, Checksum(DigestAlgorithm.Sha1, "00"))
            val refused = assertThrows(IOException::class.java) { runBlocking { PinnedDownload.download(pin, target) { _, _ -> } } }
            assertEquals("Download failed with HTTP 404", refused.message)
        }
        assertFalse(target.exists())
    }

    @Test
    fun `a file on disk is verified by its size and then its digest`() {
        target.writeText(body)
        assertTrue(PinnedDownload.isVerified(target, pin(DigestAlgorithm.Sha256, bytes = null)))
        assertFalse(PinnedDownload.isVerified(target, pin(DigestAlgorithm.Sha256, bytes = 1)))
        target.writeText(body.uppercase())
        assertFalse(PinnedDownload.isVerified(target, pin(DigestAlgorithm.Sha256, bytes = null)))
    }

    private fun pin(algorithm: DigestAlgorithm, bytes: Long?): PinnedFile {
        val hex = hexDigest(body.toByteArray(), algorithm)
        return PinnedFile(server.address("/file.zip"), bytes, Checksum(algorithm, hex))
    }
}
