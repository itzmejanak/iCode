package dev.icode.mobile.data.io

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedReadTest {
    @Test
    fun `a stream at the limit is read whole`() {
        val bytes = ByteArray(2 * DEFAULT_BUFFER_SIZE) { it.toByte() }
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readAtMost(bytes.size, "too large"))
    }

    @Test
    fun `one byte over the limit is refused with the caller's message`() {
        val bytes = ByteArray(101)
        val error = assertThrows(IOException::class.java) {
            ByteArrayInputStream(bytes).readAtMost(100, "Files larger than 100 B are not supported")
        }
        assertEquals("Files larger than 100 B are not supported", error.message)
    }

    @Test
    fun `an empty stream is an empty result`() {
        assertEquals(0, ByteArrayInputStream(ByteArray(0)).readAtMost(10, "too large").size)
    }
}
