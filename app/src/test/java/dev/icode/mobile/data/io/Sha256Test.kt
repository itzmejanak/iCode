package dev.icode.mobile.data.io

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256Test {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `the published test vector, as lowercase hex`() {
        // FIPS 180-2, appendix B.1. Stored drafts and settings keys depend on this exact form.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc"))
    }

    @Test
    fun `text is hashed as UTF-8`() {
        assertEquals(Sha256.hex("héllo ✓".toByteArray(Charsets.UTF_8)), Sha256.hex("héllo ✓"))
    }

    @Test
    fun `a file read in chunks hashes as its bytes do`() {
        val bytes = ByteArray(3 * DEFAULT_BUFFER_SIZE + 17) { (it * 31).toByte() }
        val file = File(temporaryFolder.root, "blob").apply { writeBytes(bytes) }
        assertEquals(Sha256.hex(bytes), Sha256.hexOf(file))
    }
}
