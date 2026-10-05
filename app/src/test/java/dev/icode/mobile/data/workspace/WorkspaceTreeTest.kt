package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.git.GitWorkspaceSnapshot
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.domain.FileKind
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTreeTest {
    private val root = "/workspace"
    private val folder = DocumentNode("src", "$root/src", FileKind.Folder)
    private val child = DocumentNode("Main.kt", "$root/src/Main.kt", FileKind.File)

    @Test fun `folder tap expands and next tap collapses without gateway state`() = runBlocking {
        val stateHolder = ICodeStateHolder()
        stateHolder.update { it.copy(activeWorkspaceLocation = root) }
        val tree = WorkspaceTree(stateHolder, WorkspaceFileSource(WorkspacePaths(File("/workspaces")))) { location ->
            if (location == folder.location) listOf(child) else emptyList()
        }
        tree.reset(root, listOf(folder))

        tree.toggle(root, folder.location)
        assertEquals(listOf(folder.location, child.location), stateHolder.current.files.map { it.path })
        assertTrue(stateHolder.current.files.first().expanded)

        tree.toggle(root, folder.location)
        assertEquals(listOf(folder.location), stateHolder.current.files.map { it.path })
        assertFalse(stateHolder.current.files.first().expanded)
    }

    @Test fun `late tap from previous workspace cannot replace current rows`() = runBlocking {
        val stateHolder = ICodeStateHolder()
        stateHolder.update { it.copy(activeWorkspaceLocation = "/new") }
        val tree = WorkspaceTree(stateHolder, WorkspaceFileSource(WorkspacePaths(File("/workspaces")))) { listOf(child) }
        tree.reset("/new", emptyList())

        tree.toggle(root, folder.location)

        assertTrue(stateHolder.current.files.isEmpty())
    }
}
