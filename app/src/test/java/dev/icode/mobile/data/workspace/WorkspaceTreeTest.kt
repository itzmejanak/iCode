package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.git.GitWorkspaceSnapshot
import dev.icode.mobile.domain.FileKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTreeTest {
    private val root = "/workspace"
    private val folder = DocumentNode("src", "$root/src", FileKind.Folder)
    private val child = DocumentNode("Main.kt", "$root/src/Main.kt", FileKind.Code)

    @Test fun `folder tap expands and next tap collapses without gateway state`() = runBlocking {
        val workbench = WorkbenchStateHolder()
        workbench.update { it.copy(activeWorkspaceLocation = root) }
        val tree = WorkspaceTree(workbench, DirectFileWorkspaceSource()) { location ->
            if (location == folder.location) listOf(child) else emptyList()
        }
        tree.reset(root, listOf(folder))

        tree.toggle(root, folder.location)
        assertEquals(listOf(folder.location, child.location), workbench.current.files.map { it.path })
        assertTrue(workbench.current.files.first().expanded)

        tree.toggle(root, folder.location)
        assertEquals(listOf(folder.location), workbench.current.files.map { it.path })
        assertFalse(workbench.current.files.first().expanded)
    }

    @Test fun `late tap from previous workspace cannot replace current rows`() = runBlocking {
        val workbench = WorkbenchStateHolder()
        workbench.update { it.copy(activeWorkspaceLocation = "/new") }
        val tree = WorkspaceTree(workbench, DirectFileWorkspaceSource()) { listOf(child) }
        tree.reset("/new", emptyList())

        tree.toggle(root, folder.location)

        assertTrue(workbench.current.files.isEmpty())
    }
}
