package dev.icode.mobile.domain

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ByteSizeTest {
    private val original = Locale.getDefault()

    // The decimal separator follows the user's locale; these cases pin the numbers.
    @Before fun useUsLocale() = Locale.setDefault(Locale.US)

    @After fun restoreLocale() = Locale.setDefault(original)

    @Test fun `bytes below a kilobyte stay bytes`() {
        assertEquals("0 B", ByteSize.format(0))
        assertEquals("1023 B", ByteSize.format(1023))
    }

    @Test fun `one decimal below ten, whole numbers from ten`() {
        assertEquals("4.2 KB", ByteSize.format(4_301))
        assertEquals("1.8 KB", ByteSize.format(1_843))
        assertEquals("12 KB", ByteSize.format(12_288))
        assertEquals("5.5 MB", ByteSize.format(5_767_168))
        assertEquals("220 MB", ByteSize.format(220L * 1024 * 1024))
    }

    @Test fun `just under ten does not read as ten point zero`() {
        assertEquals("10 KB", ByteSize.format(10_200))
    }

    @Test fun `gigabytes always keep a decimal`() {
        assertEquals("1.1 GB", ByteSize.format((1.1 * 1024 * 1024 * 1024).toLong()))
        assertEquals("16.0 GB", ByteSize.format(16L * 1024 * 1024 * 1024))
        assertEquals("128.0 GB", ByteSize.format(137_438_953_472L))
    }
}
