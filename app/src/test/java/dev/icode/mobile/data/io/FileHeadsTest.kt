package dev.icode.mobile.data.io

import dev.icode.mobile.domain.FileTypes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A file's kind where it lies (docs/file-kinds-icons-plan.md D2): its first bytes read only where its name cannot say. */
class FileHeadsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `at most the first bytes, fewer for a small file, none for one that cannot be read`() {
        val big = temp.newFile("big.bin").apply { writeBytes(ByteArray(4096) { 7 }) }
        assertEquals(FileTypes.HEAD_BYTES, FileHeads.head(big.toPath()).size)
        assertEquals(3, FileHeads.head(temp.newFile("small").apply { writeText("abc") }.toPath()).size)
        assertEquals(0, FileHeads.head(File(temp.root, "gone").toPath()).size)
    }

    @Test fun `a name that says what it is is what it is, read or not`() {
        val stream = ByteArray(600).also { (0 until 3).forEach { packet -> it[packet * 188] = 0x47 } }
        assertEquals(FileTypes.Video, FileHeads.typeOf(temp.newFile("news.ts").apply { writeBytes(stream) }))
        assertEquals("typescript", FileHeads.typeOf(temp.newFile("app.ts").apply { writeText("let a = 1") }).id)
        // Never read: a declaration file is TypeScript, and so is a missing one named so.
        assertEquals("typescript", FileHeads.typeOf(temp.newFile("lib.d.ts").apply { writeBytes(stream) }).id)
        assertEquals("typescript", FileHeads.typeOf(File(temp.root, "gone.ts")).id)
        assertEquals(FileTypes.Pdf, FileHeads.typeOf(temp.newFile("download").apply { writeText("%PDF-1.4\n") }))
    }
}
