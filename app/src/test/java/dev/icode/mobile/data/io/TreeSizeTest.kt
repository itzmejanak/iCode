package dev.icode.mobile.data.io

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TreeSizeTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `regular files are counted and symbolic links are not followed`() {
        val root = temp.newFolder("tree")
        File(root, "one.txt").writeText("abc")
        File(root, "sub").mkdir()
        File(root, "sub/two.txt").writeText("de")
        val outside = temp.newFile("outside.txt").apply { writeText("outside data") }
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        assertEquals(5L, measureTree(root, maxEntries = 100).bytes)
    }

    @Test fun `passing the bound is truncated, not a smaller number`() {
        val root = temp.newFolder("tree")
        repeat(3) { File(root, "f$it").writeText("x") }
        val measured = measureTree(root, maxEntries = 2)
        assertTrue(measured.truncated)
        assertNull(measured.bytes)
    }

    @Test fun `a pipe holds nothing and does not make the size unknown`() {
        val root = temp.newFolder("tree")
        File(root, "a.txt").writeText("abcd")
        val pipe = File(root, "events.fifo")
        assertEquals(0, ProcessBuilder("mkfifo", pipe.path).start().waitFor())
        assertEquals(4L, measureTree(root, maxEntries = 100).bytes)
    }

    @Test fun `a directory that cannot be listed makes the size unknown`() {
        val root = temp.newFolder("tree")
        File(root, "a.txt").writeText("abcd")
        val locked = File(root, "locked").apply { mkdir() }
        locked.setReadable(false)
        try {
            assertNull(measureTree(root, maxEntries = 100).bytes)
        } finally {
            locked.setReadable(true)
        }
    }

    @Test fun `a missing root has no size`() {
        assertNull(measureTree(File(temp.root, "missing"), maxEntries = 10).bytes)
    }
}
