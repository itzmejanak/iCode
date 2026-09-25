package dev.icode.mobile.demo

import dev.icode.mobile.domain.IndentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DemoWorkspacePreferencesRepositoryTest {
    @Test fun `workspace overrides and reset stay isolated`() = runBlocking {
        val repository = DemoWorkspacePreferencesRepository()
        repository.setIndentSizeOverride("first", 6)
        repository.setIndentTypeOverride("first", IndentType.Tabs)
        repository.setAutoFetch("first", false)
        val first = repository.values("first").first()
        val second = repository.values("second").first()
        assertEquals(6, first.effectiveIndentSize)
        assertEquals(IndentType.Tabs, first.effectiveIndentType)
        assertFalse(first.autoFetch)
        assertEquals(2, second.effectiveIndentSize) // frame-40 fixture

        repository.setGlobalIndentSize(4)
        repository.setIndentSizeOverride("first", null)
        repository.setIndentTypeOverride("first", null)
        val reset = repository.values("first").first()
        assertNull(reset.indentSizeOverride)
        assertEquals(4, reset.effectiveIndentSize)
        assertEquals(IndentType.Spaces, reset.effectiveIndentType)
        assertEquals(2, repository.values("second").first().effectiveIndentSize)
    }

    @Test fun `terminal alerts are on until switched off`() = runBlocking {
        val repository = DemoWorkspacePreferencesRepository()
        val initial = repository.values(null).first().terminal
        assertEquals(true, initial.bellAlerts)
        assertEquals(true, initial.finishAlerts)
        repository.setTerminalBellAlerts(false)
        repository.setTerminalFinishAlerts(false)
        val off = repository.values(null).first().terminal
        assertFalse(off.bellAlerts)
        assertFalse(off.finishAlerts)
    }

    @Test fun `invalid indent size is rejected`() = runBlocking {
        val repository = DemoWorkspacePreferencesRepository()
        val failed = runCatching { repository.setIndentSizeOverride("first", 0) }.isFailure
        assertEquals(true, failed)
        assertEquals(2, repository.values("first").first().effectiveIndentSize)
    }
}
