package dev.icode.mobile.data.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DpkgStatusTest {
    @get:Rule val temp = TemporaryFolder()

    /** Stanzas as dpkg writes them (the phone's status file, 2026-09-30), and the states it records. */
    private val status = """
        Package: git
        Status: install ok installed
        Priority: optional
        Version: 1:2.43.0-1ubuntu7.3

        Package: libfoo
        Status: hold ok installed
        Architecture: arm64

        Package: curl
        Status: deinstall ok installed

        Package: ripgrep
        Status: deinstall ok config-files

        Package: python3
        Status: install ok half-installed
    """.trimIndent() + "\n"

    @Test fun `what is on disk is installed, whatever is wanted of it`() {
        assertEquals(setOf("git", "libfoo", "curl"), DpkgStatus.parse(status))
    }

    @Test fun `the file is read again once it changes, and a missing one means nothing installed`() {
        val rootfs = temp.newFolder("rootfs")
        val dpkg = DpkgStatus(rootfs)
        assertEquals(emptySet<String>(), dpkg.installed())
        val file = File(rootfs, DpkgStatus.STATUS_RELATIVE).apply { parentFile!!.mkdirs(); writeText(status) }
        assertEquals(setOf("git", "libfoo", "curl"), dpkg.installed())
        file.writeText("Package: ripgrep\nStatus: install ok installed\n")
        file.setLastModified(file.lastModified() + 2_000)
        assertEquals(setOf("ripgrep"), dpkg.installed())
    }
}
