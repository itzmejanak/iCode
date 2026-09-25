package dev.icode.mobile.domain

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceNameRulesTest {
    @Test
    fun `accepts ordinary project names`() {
        assertNull(WorkspaceNameRules.validationError("icode-mobile"))
    }

    @Test
    fun `rejects empty traversal and separators`() {
        assertNotNull(WorkspaceNameRules.validationError("  "))
        assertNotNull(WorkspaceNameRules.validationError(".."))
        assertNotNull(WorkspaceNameRules.validationError("parent/child"))
        assertNotNull(WorkspaceNameRules.validationError("parent\\child"))
    }

    @Test
    fun `limits encoded name length rather than character count`() {
        assertNull(WorkspaceNameRules.validationError("a".repeat(255)))
        assertNotNull(WorkspaceNameRules.validationError("é".repeat(128)))
    }

    @Test
    fun `file names keep extensions but reject traversal and git metadata`() {
        assertNull(WorkspaceNameRules.fileValidationError("App.tsx"))
        assertNull(WorkspaceNameRules.fileValidationError(".env"))
        assertNotNull(WorkspaceNameRules.fileValidationError("../App.tsx"))
        assertNotNull(WorkspaceNameRules.fileValidationError(".git"))
        assertNotNull(WorkspaceNameRules.fileValidationError("  "))
    }

    @Test
    fun `workspace subfolder cannot replace git metadata`() {
        assertNotNull(WorkspaceNameRules.workspaceFolderValidationError(".git"))
        assertNull(WorkspaceNameRules.workspaceFolderValidationError(".github"))
    }
}
