package dev.icode.mobile.data.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeHomeBackupTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `repair retry preserves the original home rather than extracted default home`() {
        val root = temporaryFolder.newFolder("runtime")
        val rootfs = File(root, "rootfs")
        val home = File(rootfs, "root").apply { mkdirs() }
        File(home, "user.txt").writeText("keep me")
        val backup = RuntimeHomeBackup(rootfs, root)

        assertEquals(backup.backup, backup.prepareForRepair())
        assertFalse(home.exists())
        File(home, "default.txt").apply { parentFile!!.mkdirs(); writeText("new base") }
        assertEquals(backup.backup, backup.prepareForRepair())
        assertEquals("keep me", File(backup.backup, "user.txt").readText())

        backup.restore(backup.backup)
        assertEquals("keep me", File(home, "user.txt").readText())
        assertFalse(File(home, "default.txt").exists())
        assertFalse(backup.backup.exists())
        assertTrue(backup.hasPreservedHome)
        assertEquals(backup.backup, backup.prepareForRepair())
        assertEquals("keep me", File(backup.backup, "user.txt").readText())
        backup.restore(backup.backup)
        backup.completeRepair()
        assertFalse(backup.hasPreservedHome)
    }

    @Test fun `reset only discards backup after explicit call`() {
        val root = temporaryFolder.newFolder("reset")
        val backup = RuntimeHomeBackup(File(root, "rootfs"), root)
        File(backup.backup, "user.txt").apply { parentFile!!.mkdirs(); writeText("keep until success") }
        assertTrue(backup.backup.exists())
        backup.discardAfterReset()
        assertFalse(backup.backup.exists())
        assertFalse(backup.hasPreservedHome)
    }
}
