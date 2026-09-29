package dev.icode.mobile.data.storage

import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.ProjectionBind
import dev.icode.mobile.domain.StorageClass
import dev.icode.mobile.domain.WorkspaceMount
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceStorageTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val originalWriter = BackendLog.writer
    private val logged = mutableListOf<String>()
    private val user = "/storage/emulated/0/Projects/RevPlayer"

    @Before fun captureLog() {
        BackendLog.writer = { _, _, message -> logged += message }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
    }

    @Test fun `a workspace without mounts is the user's folder alone`() {
        val storage = WorkspaceStorage(temporaryFolder.root)
        assertEquals(listOf(ProjectionBind(user, "/workspace/RevPlayer")), storage.of(user).binds("/workspace/RevPlayer"))
    }

    @Test fun `mounts are kept per workspace, and their private folders made ready`() {
        val storage = WorkspaceStorage(temporaryFolder.root)
        storage.setMounts(user, listOf(WorkspaceMount("node_modules", StorageClass.LINUX), WorkspaceMount(".next/cache", StorageClass.CACHE)))

        val workspace = storage.of(user)

        val private = File(temporaryFolder.root, "workspaces/${Sha256.hex(user)}")
        assertEquals(private.path, workspace.privateRoot)
        assertTrue(File(private, "linux/node_modules").isDirectory)
        assertTrue(File(private, "cache/.next/cache").isDirectory)
        assertEquals(StorageClass.USER, storage.of("/storage/emulated/0/Projects/Other").resolve("node_modules").storage)
    }

    @Test fun `a bad line is skipped and said, and the rest still holds`() {
        val storage = WorkspaceStorage(temporaryFolder.root)
        val file = File(storage.privateRoot(user), "mounts").apply { parentFile?.mkdirs() }
        file.writeText("LINUX node_modules\nLINUX ../escape\nWHATEVER x\nCACHE .git\n\n")

        assertEquals(listOf(WorkspaceMount("node_modules", StorageClass.LINUX)), storage.of(user).mounts)
        assertEquals(3, logged.size)
    }

    @Test fun `no invalid mount is ever written`() {
        val storage = WorkspaceStorage(temporaryFolder.root)
        assertThrows(IllegalArgumentException::class.java) {
            storage.setMounts(user, listOf(WorkspaceMount("../outside", StorageClass.LINUX)))
        }
        assertTrue(!File(storage.privateRoot(user), "mounts").exists())
    }
}
