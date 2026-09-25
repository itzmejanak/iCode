package dev.icode.mobile.data.workspace

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageInspectorTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `measurement is bounded and does not follow symbolic links`() {
        val root = temp.newFolder("workspace")
        File(root, "one.txt").writeText("abc")
        val outside = temp.newFile("outside.txt").apply { writeText("outside data") }
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        assertEquals(3L, StorageInspector.measure(root).bytes)
        assertTrue(StorageInspector.measure(root, maxEntries = 1).truncated)
    }

    @Test fun `cache cleanup removes only selected cache paths without following links`() {
        val cache = temp.newFolder("cache")
        val prootTemp = File(cache, "proot-tmp").apply { mkdirs() }
        File(cache, "rootfs.tar.gz").writeText("archive")
        val keep = File(cache, "keep.txt").apply { writeText("keep") }
        val outside = temp.newFile("precious.txt").apply { writeText("safe") }
        Files.createSymbolicLink(File(prootTemp, "outside-link").toPath(), outside.toPath())
        File(prootTemp, "scratch").writeText("tmp")
        StorageInspector.clearRuntimeCache(cache, prootTemp)
        assertFalse(File(cache, "rootfs.tar.gz").exists())
        assertFalse(File(prootTemp, "scratch").exists())
        assertTrue(keep.exists())
        assertTrue(outside.exists())
    }
}
