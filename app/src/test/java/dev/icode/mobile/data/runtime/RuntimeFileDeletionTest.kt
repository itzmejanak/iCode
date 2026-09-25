package dev.icode.mobile.data.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeFileDeletionTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `runtime tree deletion cannot follow link into workspace`() {
        val runtime = temp.newFolder("rootfs")
        val workspace = temp.newFolder("workspace")
        val source = File(workspace, "precious.txt").apply { writeText("safe") }
        File(runtime, "ordinary.txt").writeText("remove")
        Files.createSymbolicLink(File(runtime, "linked-workspace").toPath(), workspace.toPath())
        deleteRuntimeTree(runtime)
        assertFalse(runtime.exists())
        assertTrue(source.exists())
    }
}
