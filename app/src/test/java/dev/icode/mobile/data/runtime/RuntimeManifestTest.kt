package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.io.DigestAlgorithm
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeManifestTest {
    private val shipped = RuntimeManifest.parse(File("src/main/assets/${RuntimeManifest.ASSET}").readText())

    @Test
    fun `the shipped manifest pins the root filesystem and each Android SDK download`() {
        assertEquals("arm64-v8a", shipped.architecture)
        assertEquals(64, shipped.rootfs.sha256.length)
        val tools = shipped.archive("android-cmdline-tools")
        assertEquals(DigestAlgorithm.Sha1, tools.checksum.algorithm)
        assertEquals(40, tools.checksum.hex.length)
        assertEquals(181_833_628L, tools.bytes)
        val hostTools = shipped.archive("android-host-tools-arm64")
        assertEquals(DigestAlgorithm.Sha256, hostTools.checksum.algorithm)
        assertEquals("db1cea2c4454d5f9c5a802646b2d1cf560b4ee7badbe23e51ab8e1881bb50fc2", hostTools.checksum.hex)
        assertEquals(setOf("platforms;android-36", "build-tools;36.0.0"), shipped.packageBytes.keys)
    }

    @Test
    fun `a pin missing from the manifest is said as this build's mistake`() {
        val missing = assertThrows(IllegalStateException::class.java) { shipped.archive("nothing") }
        assertEquals("The runtime manifest pins no nothing", missing.message)
    }

    @Test
    fun `a pin is read lowercased, without a size when none is given`() {
        val manifest = RuntimeManifest.parse(manifest(""""tool": {"url": "https://example.test/t.zip", "sha256": "ABCD"}"""))
        val pin = manifest.archive("tool")
        assertEquals(Checksum(DigestAlgorithm.Sha256, "abcd"), pin.checksum)
        assertNull(pin.bytes)
    }

    @Test
    fun `a pin without a checksum is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeManifest.parse(manifest(""""tool": {"url": "https://example.test/t.zip", "bytes": 4}"""))
        }
    }

    @Test
    fun `a manifest of another schema is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeManifest.parse(manifest("").replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
        }
    }

    private fun manifest(archives: String) = """
        {"schemaVersion": 1, "architecture": "arm64-v8a",
         "rootfs": {"id": "r", "base": "R", "url": "https://example.test/r.tar.gz", "sha256": "00", "minimumFreeBytes": 1},
         "proot": {"version": "1"}, "archives": {$archives}}
    """.trimIndent()
}
