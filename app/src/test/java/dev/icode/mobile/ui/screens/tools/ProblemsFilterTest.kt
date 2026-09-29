package dev.icode.mobile.ui.screens.tools

import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemsFilterTest {
    private val diagnostics = listOf(
        Diagnostic(Severity.Error, "a.kt", "error", "1:1", "test"),
        Diagnostic(Severity.Warning, "a.kt", "warning", "2:1", "test"),
        Diagnostic(Severity.Error, "b.kt", "second error", "3:1", "test"),
    )

    @Test fun eachSeverityShowsOnlyItsOwnRows() {
        assertEquals(listOf("error", "second error"), visibleDiagnostics(diagnostics, Severity.Error).map { it.message })
        assertEquals(listOf("warning"), visibleDiagnostics(diagnostics, Severity.Warning).map { it.message })
        assertTrue(visibleDiagnostics(diagnostics, Severity.Info).isEmpty())
    }
}
