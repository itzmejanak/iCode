package dev.icode.mobile.demo

import dev.icode.mobile.domain.WorkbenchIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoWorkbenchGatewayTest {
    @Test
    fun `author identity starts unset and global default can be overridden per repository`() {
        val gateway = DemoWorkbenchGateway()
        assertNull(gateway.snapshot.value.globalGitAuthorName)
        assertNull(gateway.snapshot.value.gitRepository?.authorName)

        gateway.dispatch(WorkbenchIntent.ConfigureGlobalGitIdentity("Global User", "global@example.com"))
        assertEquals("Global User", gateway.snapshot.value.gitRepository?.authorName)
        gateway.dispatch(WorkbenchIntent.ConfigureGitIdentity("Local User", "local@example.com"))
        assertEquals("Local User", gateway.snapshot.value.gitRepository?.authorName)
        gateway.dispatch(WorkbenchIntent.ClearRepositoryGitIdentity)
        assertEquals("Global User", gateway.snapshot.value.gitRepository?.authorName)
    }

    @Test
    fun `removing a recent workspace only changes the recent list`() {
        val gateway = DemoWorkbenchGateway()
        val before = gateway.snapshot.value
        val item = before.workspaces.first()

        gateway.dispatch(WorkbenchIntent.RemoveRecentWorkspace(item.location))

        val after = gateway.snapshot.value
        assertFalse(after.workspaces.any { it.location == item.location })
        assertEquals(before.workspaces.size - 1, after.workspaces.size)
        assertEquals(before.activeWorkspace, after.activeWorkspace)
        assertEquals(before.files, after.files)
    }

    @Test
    fun `terminal action switches to selected recent workspace`() {
        val gateway = DemoWorkbenchGateway()
        val selected = gateway.snapshot.value.workspaces.first { it.available }

        gateway.dispatch(WorkbenchIntent.OpenWorkspaceTerminal(selected.location))

        assertEquals(selected.name, gateway.snapshot.value.activeWorkspace)
        assertEquals(selected.path, gateway.snapshot.value.activePath)
    }

    @Test
    fun `document tabs select reorder and protect unsaved edits`() {
        val gateway = DemoWorkbenchGateway()
        val initial = gateway.snapshot.value.openDocuments
        assertEquals(3, initial.size)
        val first = initial[0]
        val second = initial[1]

        gateway.dispatch(WorkbenchIntent.SelectDocument(second.location))
        assertEquals(second.location, gateway.snapshot.value.activeDocument?.location)
        gateway.dispatch(WorkbenchIntent.UpdateDocumentText(second.location, "changed"))
        assertEquals(true, gateway.snapshot.value.openDocuments.first { it.location == second.location }.dirty)
        gateway.dispatch(WorkbenchIntent.ReorderDocument(second.location, first.location))
        assertEquals(second.location, gateway.snapshot.value.openDocuments.first().location)

        gateway.dispatch(WorkbenchIntent.CloseDocument(second.location))
        assertEquals(3, gateway.snapshot.value.openDocuments.size)
        gateway.dispatch(WorkbenchIntent.CloseDocument(second.location, saveChanges = false))
        assertEquals(2, gateway.snapshot.value.openDocuments.size)
        assertFalse(gateway.snapshot.value.openDocuments.any { it.location == second.location })
    }

    @Test
    fun `opening same file focuses existing tab without duplication`() {
        val gateway = DemoWorkbenchGateway()
        val first = gateway.snapshot.value.openDocuments.first()
        gateway.dispatch(WorkbenchIntent.SelectDocument(gateway.snapshot.value.openDocuments.last().location))
        gateway.dispatch(WorkbenchIntent.OpenFile(first.location))
        assertEquals(first.location, gateway.snapshot.value.activeDocument?.location)
        assertEquals(3, gateway.snapshot.value.openDocuments.size)
    }

    @Test
    fun `new file opens as a tab and remains in recent files after closing`() {
        val gateway = DemoWorkbenchGateway()
        gateway.dispatch(WorkbenchIntent.CreateFile("notes.md"))
        val created = gateway.snapshot.value.activeDocument ?: error("New file did not open")
        assertEquals("notes.md", created.name)
        assertEquals("", created.text)
        assertEquals(created.location, gateway.snapshot.value.recentDocuments.first().location)

        gateway.dispatch(WorkbenchIntent.CloseDocument(created.location))
        assertFalse(gateway.snapshot.value.openDocuments.any { it.location == created.location })
        gateway.dispatch(WorkbenchIntent.OpenFile(gateway.snapshot.value.recentDocuments.first().location))
        assertEquals(created.location, gateway.snapshot.value.activeDocument?.location)
    }

    @Test
    fun `subfolder is created inside workspace without opening another workspace`() {
        val gateway = DemoWorkbenchGateway()
        val root = gateway.snapshot.value.activeWorkspaceLocation ?: error("Missing demo workspace")
        val parent = gateway.snapshot.value.files.first { it.kind == dev.icode.mobile.domain.FileKind.Folder }.path
        gateway.dispatch(WorkbenchIntent.CreateWorkspaceFolder("new-ui", parent, root, "folder-request"))

        val folder = gateway.snapshot.value.files.first { it.path == "$parent/new-ui" }
        assertEquals(dev.icode.mobile.domain.FileKind.Folder, folder.kind)
        assertTrue(folder.depth > 0)
        assertEquals(root, gateway.snapshot.value.activeWorkspaceLocation)

        gateway.dispatch(WorkbenchIntent.CreateFile("notes.md", folder.path, root, "file-request"))
        assertEquals("$parent/new-ui/notes.md", gateway.snapshot.value.activeDocument?.location)
        assertEquals(folder.depth + 1, gateway.snapshot.value.files.first { it.path == "$parent/new-ui/notes.md" }.depth)
    }

    @Test
    fun `workspace item creation rejects a changed workspace and duplicates`() {
        val gateway = DemoWorkbenchGateway()
        val root = gateway.snapshot.value.activeWorkspaceLocation ?: error("Missing demo workspace")
        val before = gateway.snapshot.value.files
        gateway.dispatch(WorkbenchIntent.CreateWorkspaceFolder("safe", root, "other-workspace", "stale"))
        assertEquals(before, gateway.snapshot.value.files)
        gateway.dispatch(WorkbenchIntent.CreateWorkspaceFolder("safe", root, root, "first"))
        gateway.dispatch(WorkbenchIntent.CreateWorkspaceFolder("safe", root, root, "duplicate"))
        assertEquals(1, gateway.snapshot.value.files.count { it.path == "$root/safe" })
    }
}
