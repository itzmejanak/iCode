package dev.icode.mobile.domain

import dev.icode.mobile.domain.AssistantAsk.Change
import dev.icode.mobile.domain.AssistantAsk.Command
import dev.icode.mobile.domain.AssistantAsk.Outside
import dev.icode.mobile.domain.AssistantPermission.AskFirst
import dev.icode.mobile.domain.AssistantPermission.AutoInWorkspace
import dev.icode.mobile.domain.AssistantPermission.FullAccess
import dev.icode.mobile.domain.AssistantToolKind.Read
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/assistant-and-mcp.md, row by row: what each call asks in each mode. */
class AssistantPermissionsTest {
    private fun asks(kind: AssistantToolKind, outside: Boolean) =
        listOf(AskFirst, AutoInWorkspace, FullAccess).map { AssistantPermissions.ask(it, kind, outside) }

    @Test fun `a read inside runs in every mode`() {
        assertEquals(listOf(null, null, null), asks(Read, outside = false))
    }

    @Test fun `a read outside asks but in Full access`() {
        assertEquals(listOf(Outside, Outside, null), asks(Read, outside = true))
    }

    @Test fun `a change inside asks only in Ask first`() {
        assertEquals(listOf(Change, null, null), asks(AssistantToolKind.Change, outside = false))
    }

    @Test fun `a change outside asks once, as outside, but in Full access`() {
        assertEquals(listOf(Outside, Outside, null), asks(AssistantToolKind.Change, outside = true))
    }

    @Test fun `a command inside asks only in Ask first, one reaching outside asks once but in Full access`() {
        assertEquals(listOf(Command, null, null), asks(AssistantToolKind.Command, outside = false))
        assertEquals(listOf(Outside, Outside, null), asks(AssistantToolKind.Command, outside = true))
    }

    @Test fun `a denial is told to the model in what it asked`() {
        assertEquals("The user did not allow reaching /etc/hosts, /root", AssistantPermissions.denied(Outside, listOf("/etc/hosts", "/root")))
        assertEquals("The user did not allow this command", AssistantPermissions.denied(Command, emptyList()))
        assertEquals("The user did not allow this change", AssistantPermissions.denied(Change, emptyList()))
    }

    @Test fun `allowing in the workspace raises Ask first to Auto in workspace, and nothing else`() {
        assertEquals(AutoInWorkspace, AssistantPermissions.effective(AskFirst, allowedInWorkspace = true))
        assertEquals(AskFirst, AssistantPermissions.effective(AskFirst, allowedInWorkspace = false))
        assertEquals(AutoInWorkspace, AssistantPermissions.effective(AutoInWorkspace, allowedInWorkspace = true))
        assertEquals(FullAccess, AssistantPermissions.effective(FullAccess, allowedInWorkspace = false))
        // In no workspace there is none to run in without asking: only Full access asks nothing (docs/design-and-navigation.md).
        assertEquals(AskFirst, AssistantPermissions.effective(AutoInWorkspace, allowedInWorkspace = false, inWorkspace = false))
        assertEquals(AskFirst, AssistantPermissions.effective(AskFirst, allowedInWorkspace = true, inWorkspace = false))
        assertEquals(FullAccess, AssistantPermissions.effective(FullAccess, allowedInWorkspace = false, inWorkspace = false))
    }

    @Test fun `allowing in this chat opens another workspace whole, a repository at its top, else the folder, never the root`() {
        val folders = setOf("/workspace", "/workspace/portofolio", "/workspace/portofolio/src", "/root/projects/api", "/root/projects/api/lib", "/etc", "/root")
        val repositories = setOf("/root/projects/api")
        fun root(path: String) = AssistantPermissions.rootOf(path, { it in folders }, { it in repositories })
        assertEquals("/workspace/portofolio", root("/workspace/portofolio/src/App.tsx"))
        assertEquals("/workspace/portofolio", root("/workspace/portofolio"))
        assertEquals("/workspace", root("/workspace"))
        assertEquals("/root/projects/api", root("/root/projects/api/lib/x.py"))
        assertEquals("/etc", root("/etc/hosts"))
        assertEquals("/etc", root("/etc"))
        assertEquals("/root", root("/root/notes.txt"))
        assertNull(root("/notes.txt"))
        assertNull(root("/"))
    }

    @Test fun `within is whole segments`() {
        assertTrue(AssistantPermissions.within("/workspace/portofolio/src", "/workspace/portofolio"))
        assertTrue(AssistantPermissions.within("/etc", "/etc"))
        assertFalse(AssistantPermissions.within("/workspace/portofolio2", "/workspace/portofolio"))
    }

    @Test fun `the chat shown's mode is the setting, raised by its own answer`() {
        val state = AssistantState(settings = AssistantSettings(permission = AskFirst), allowedInWorkspace = true)
        assertEquals(AutoInWorkspace, state.permission)
        assertEquals(AskFirst, state.copy(allowedInWorkspace = false).permission)
    }
}
