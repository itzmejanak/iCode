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

    private fun labels(prefix: String, vararg names: String, sortText: String? = "15") =
        EditorCompletionRules.matching(names.map { LanguageCompletionItem(it, sortText = sortText) }, prefix).map { it.item.label }

    @Test
    fun `the first typed letter starts the word or a word in it, as VS Code matches`() {
        // The owner's screenshot: TypeScript gives every global one sortText.
        assertEquals(listOf("await", "AudioWorklet", "AudioWorkletNode"), labels("aw", "DataView", "HTMLTableRowElement", "AudioWorkletNode", "AudioWorklet", "await"))
        assertEquals(listOf("print", "pretty_int"), labels("pri", "isinstance", "print", "sprintf", "pretty_int", "open"))
        assertEquals(listOf("getElementById"), labels("gebi", "getElementById", "getElementsByTagName"))
        assertEquals(listOf("HTMLElement"), labels("el", "HTMLElement", "Model"))
        // Only the first letter must start a word: a later one may fall anywhere, and ranks lower there.
        assertEquals(listOf("get_value", "getValue", "give"), labels("gv", "give", "getValue", "get_value"))
    }

    @Test
    fun `a closer match comes first, then the server's order, then the shorter`() {
        // Letters in a row before scattered ones; the same case before another.
        assertEquals(listOf("useState", "useSyncExternalStore"), labels("useS", "useSyncExternalStore", "useState"))
        assertEquals(listOf("Map", "map"), labels("Map", "map", "Map"))
        val items = listOf(LanguageCompletionItem("valueB", sortText = "2"), LanguageCompletionItem("valueA", sortText = "1"))
        assertEquals(listOf("valueA", "valueB"), EditorCompletionRules.matching(items, "val").map { it.item.label })
        assertEquals(listOf("open", "print"), labels("", "print", "open", sortText = null))
    }

    @Test
    fun `the matched letters are kept for the row, on the label, and two servers' one item shows once`() {
        assertEquals(listOf(0, 5), CompletionMatcher().match("aw", "AudioWorklet")!!.positions.toList())
        assertEquals(listOf(0, 1), CompletionMatcher().match("aw", "await")!!.positions.toList())
        val filtered = LanguageCompletionItem("Array<T>", filterText = "Array")
        assertEquals(listOf(0, 1), EditorCompletionRules.matching(listOf(filtered), "Ar").single().matched.toList())
        val twice = List(2) { LanguageCompletionItem("console", kind = "Variable") }
        assertEquals(1, EditorCompletionRules.matching(twice, "con").size)
        assertEquals(null, CompletionMatcher().match("xyz", "await"))
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
