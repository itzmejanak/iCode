package dev.icode.mobile.ui.editor

import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.DiagnosticRules
import dev.icode.mobile.domain.LanguageCompletionItem
import dev.icode.mobile.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorCompletionRulesTest {
    @Test
    fun `the prefix is the identifier typed before the caret`() {
        assertEquals("pri", EditorCompletionRules.prefixOf("    pri", 7))
        assertEquals("", EditorCompletionRules.prefixOf("os.", 3))
        assertEquals("\$el_2", EditorCompletionRules.prefixOf("x = \$el_2", 9))
    }

    @Test
    fun `items holding the typed letters in order show, those starting with them first`() {
        val items = listOf("isinstance", "print", "sprintf", "pretty_int", "open").map { LanguageCompletionItem(it, sortText = it) }
        assertEquals(listOf("print", "pretty_int", "sprintf"), EditorCompletionRules.matching(items, "pri").map { it.label })
        assertEquals(5, EditorCompletionRules.matching(items, "").size)
    }
}

class SnippetTextTest {
    @Test
    fun `a snippet Sora cannot read is inserted as its text, tab stops gone and placeholders kept`() {
        assertEquals("print(value)", EditorCompletionRules.plain("print(\${1:value})\$0"))
        assertEquals("for  in :\n    ", EditorCompletionRules.plain("for \${1} in \$2:\n    \$0"))
    }
}

class DiagnosticRulesTest {
    private fun problem(file: String, severity: Severity) = Diagnostic(severity, file, "m", "1:1", "s")

    @Test
    fun `files count their errors and warnings, and folders those beneath them, the worst kept`() {
        val counts = DiagnosticRules.countsByLocation(
            listOf(problem("src/a.py", Severity.Warning), problem("src/a.py", Severity.Error), problem("src/lib/b.py", Severity.Warning), problem("c.py", Severity.Info)),
            "/w",
        )
        assertEquals(DiagnosticRules.Count(2, Severity.Error), counts["/w/src/a.py"])
        assertEquals(DiagnosticRules.Count(3, Severity.Error), counts["/w/src"])
        assertEquals(DiagnosticRules.Count(1, Severity.Warning), counts["/w/src/lib"])
        assertEquals(null, counts["/w/c.py"])
        assertEquals(null, counts["/w"])
    }

    @Test
    fun `a file's problems are its own`() {
        val problems = listOf(problem("a.py", Severity.Error), problem("b/a.py", Severity.Error))
        assertEquals(1, DiagnosticRules.forFile(problems, "/w", "/w/a.py").size)
        assertEquals(0, DiagnosticRules.forFile(problems, "/w", "/elsewhere/a.py").size)
    }
}
