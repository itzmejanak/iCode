package dev.icode.mobile.data.io

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TreeDeletionTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `a link to a workspace is deleted, never the workspace`() {
        val runtime = temp.newFolder("rootfs")
        val workspace = temp.newFolder("workspace")
        val source = File(workspace, "precious.txt").apply { writeText("safe") }
        File(runtime, "ordinary.txt").writeText("remove")
        Files.createSymbolicLink(File(runtime, "linked-workspace").toPath(), workspace.toPath())
        deleteTree(runtime)
        assertFalse(runtime.exists())
        assertTrue(source.exists())
    }

    @Test fun `a relative link climbing out of the tree is not followed either`() {
        val workspaces = File(temp.root, "files/workspaces/keep").apply { mkdirs() }
        val precious = File(workspaces, "precious.txt").apply { writeText("user work") }
        val home = File(temp.root, "files/runtime/rootfs/root").apply { mkdirs() }
        Files.createSymbolicLink(File(home, "w").toPath(), Paths.get("../../../workspaces"))
        deleteTree(File(temp.root, "files/runtime/rootfs"))
        assertFalse(File(temp.root, "files/runtime/rootfs").exists())
        assertEquals("user work", precious.readText())
    }

    @Test fun `each entry is reported once it is gone, a folder after its contents`() {
        val top = File(temp.root, "top").apply { mkdirs() }
        File(top, "a").mkdirs()
        File(top, "a/b.txt").writeText("b")
        val reported = mutableListOf<String>()
        deleteTree(top.toPath()) { reported += top.toPath().relativize(it).toString() }
        assertEquals(listOf("a/b.txt", "a", ""), reported)
    }
}
