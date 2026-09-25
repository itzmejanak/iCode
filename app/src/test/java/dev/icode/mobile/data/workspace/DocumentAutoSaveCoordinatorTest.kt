package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.TextEncoding
import dev.icode.mobile.domain.WorkbenchSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentAutoSaveCoordinatorTest {
    private val draft = OpenDocument(
        name = "file.kt",
        location = "/project/file.kt",
        text = "new text",
        encoding = TextEncoding.Utf8,
        lineEnding = LineEnding.Lf,
        dirty = true,
        readOnly = false,
    )

    private fun snapshot(document: OpenDocument = draft) = WorkbenchSnapshot(
        workspaces = emptyList(),
        files = emptyList(),
        gitChanges = emptyList(),
        diagnostics = emptyList(),
        languageServices = emptyList(),
        activeWorkspace = "project",
        activePath = "/project",
        activeWorkspaceLocation = "/project",
        activeBranch = "",
        runtimeHealthy = false,
        openDocuments = listOf(document),
    )

    @Test fun `only the captured dirty revision can be saved`() {
        val current = snapshot()
        assertTrue(current.isCurrentAutoSaveRequest(draft.location, "/project", "new text"))
        assertFalse(current.isCurrentAutoSaveRequest(draft.location, "/project", "old text"))
        assertFalse(current.isCurrentAutoSaveRequest(draft.location, "/other", "new text"))
        assertFalse(current.isCurrentAutoSaveRequest(draft.location, null, "new text"))
        assertFalse(current.isCurrentAutoSaveRequest(draft.location, "/project", null))
    }

    @Test fun `clean or read only documents cannot be auto saved`() {
        assertFalse(snapshot(draft.copy(dirty = false)).isCurrentAutoSaveRequest(draft.location, "/project", "new text"))
        assertFalse(snapshot(draft.copy(readOnly = true)).isCurrentAutoSaveRequest(draft.location, "/project", "new text"))
    }
}
