package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkspacePreferencesTest {
    @Test fun `fresh workspace inherits four spaces and does not fetch`() {
        val settings = WorkspacePreferences(workspaceLocation = "/storage/project")
        assertEquals(4, settings.effectiveIndentSize)
        assertEquals(IndentType.Spaces, settings.effectiveIndentType)
        assertFalse(settings.autoFetch)
    }

    @Test fun `override resolution does not change app default`() {
        val settings = WorkspacePreferences(
            globalIndentSize = 4,
            indentSizeOverride = 2,
            indentTypeOverride = IndentType.Tabs,
        )
        assertEquals(2, settings.effectiveIndentSize)
        assertEquals(IndentType.Tabs, settings.effectiveIndentType)
        assertEquals(4, settings.globalIndentSize)
        assertEquals(4, settings.copy(indentSizeOverride = null).effectiveIndentSize)
        assertEquals(IndentType.Spaces, settings.copy(indentTypeOverride = null).effectiveIndentType)
    }
}
