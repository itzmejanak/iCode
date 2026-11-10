package dev.icode.mobile.data.language

import dev.icode.mobile.data.assistant.tools.TestRuntime
import dev.icode.mobile.domain.LanguageTextEdit
import dev.icode.mobile.domain.TextPosition
import dev.icode.mobile.domain.TextRange
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LspResultsTest {
    private val runtime = TestRuntime(Files.createTempDirectory("results").toFile())
    private val uris = LspUris(runtime.paths)
    private val results = LspResults(uris, runtime.root.path)
    private fun json(text: String) = Json.parseToJsonElement(text)
    private fun range(l1: Int, c1: Int, l2: Int, c2: Int) = TextRange(TextPosition(l1, c1), TextPosition(l2, c2))

    @Test
    fun `locations and location links become places in the workspace, with their line`() {
        File(runtime.root, "src").mkdirs()
        File(runtime.root, "src/a b.py").writeText("first\n  def go():\n")
        val places = results.places(json("""[
            {"uri":"file:///workspace/app/src/a%20b.py","range":{"start":{"line":1,"character":6},"end":{"line":1,"character":8}}},
            {"targetUri":"file:///usr/lib/python3/os.py","targetRange":{"start":{"line":9,"character":0},"end":{"line":9,"character":3}},"targetSelectionRange":{"start":{"line":9,"character":4},"end":{"line":9,"character":6}}}
        ]"""))
        assertEquals(File(runtime.root, "src/a b.py").path, places[0].location)
        assertEquals("src/a b.py", places[0].file)
        assertEquals("def go():", places[0].preview)
        assertEquals(range(9, 4, 9, 6), places[1].range)
        assertEquals("/usr/lib/python3/os.py", places[1].file)
        assertTrue(results.places(json("null")).isEmpty())
    }

    @Test
    fun `a symbol tree reads in order with its depth, and flat symbols keep their files`() {
        val tree = results.symbols(json("""[{"name":"App","kind":5,"range":{"start":{"line":0,"character":0},"end":{"line":9,"character":0}},"selectionRange":{"start":{"line":0,"character":6},"end":{"line":0,"character":9}},
            "children":[{"name":"run","kind":6,"detail":"()","range":{"start":{"line":1,"character":2},"end":{"line":2,"character":0}},"selectionRange":{"start":{"line":1,"character":6},"end":{"line":1,"character":9}}}]}]"""), File(runtime.root, "x.py").path)
        assertEquals(listOf("App · class" to 0, "run · method · ()" to 1), tree.map { it.preview to it.depth })
        val flat = results.symbols(json("""[{"name":"main","kind":12,"location":{"uri":"file:///workspace/app/m.go","range":{"start":{"line":3,"character":5},"end":{"line":3,"character":9}}}}]"""), null)
        assertEquals("m.go", flat.single().file)
        assertEquals("main · function", flat.single().preview)
    }

    @Test
    fun `completions read their edit, snippet, kind, imports and defaults`() {
        val items = results.completions(json("""{"isIncomplete":false,"itemDefaults":{"insertTextFormat":2},"items":[
            {"label":"print","kind":3,"detail":"(value)","textEdit":{"range":{"start":{"line":0,"character":0},"end":{"line":0,"character":2}},"newText":"print(${'$'}1)"}},
            {"label":"os","kind":9,"insertText":"os","insertTextFormat":1,"additionalTextEdits":[{"range":{"start":{"line":0,"character":0},"end":{"line":0,"character":0}},"newText":"import os\n"}],"tags":[1]},
            {"label":"odd","kind":99}
        ]}"""), resolvable = true)
        assertEquals(LanguageTextEdit(range(0, 0, 0, 2), "print(${'$'}1)"), items[0].edit)
        assertTrue(items[0].snippet)
        assertEquals("Function", items[0].kind)
        assertFalse(items[1].snippet)
        assertTrue(items[1].deprecated)
        assertEquals("import os\n", items[1].additionalEdits.single().newText)
        assertNull(items[2].kind)
        assertTrue(items.all { it.resolvable })
    }

    @Test
    fun `a label's signature and its type are both kept`() {
        val item = results.completions(json("""[{"label":"useState","kind":3,"labelDetails":{"detail":"(initialState)","description":"react"}}]"""), resolvable = false).single()
        assertEquals("(initialState)", item.labelDetail)
        assertEquals("react", item.detail)
    }

    @Test
    fun `markup, marked strings and signatures read as text`() {
        assertEquals("```python\nx: int\n```\n\nThe x.", LanguageJson.markup(json("""[{"language":"python","value":"x: int"},"The x."]""")))
        assertEquals("**bold**", LanguageJson.markup(json("""{"kind":"markdown","value":"**bold**"}""")))
        val signature = results.signature(json("""{"signatures":[{"label":"go(a: int, b: str)","parameters":[{"label":[3,9]},{"label":"b: str"}]}],"activeSignature":0,"activeParameter":0}"""))
        assertEquals("```\ngo(a: int, b: str)\n```\n\nNow: `a: int`", signature)
    }

    @Test
    fun `edits apply in the text's own places, inserts at one place in order, and overlapping ones are refused`() {
        val text = "ab\r\ncd"
        assertEquals("aXb\r\ncdY", LanguageEdits.applyTo(text, listOf(LanguageTextEdit(range(1, 2, 1, 2), "Y"), LanguageTextEdit(range(0, 1, 0, 1), "X"))))
        assertEquals("12ab\r\ncd", LanguageEdits.applyTo(text, listOf(LanguageTextEdit(range(0, 0, 0, 0), "1"), LanguageTextEdit(range(0, 0, 0, 0), "2"))))
        assertNull(LanguageEdits.applyTo(text, listOf(LanguageTextEdit(range(0, 0, 0, 2), "x"), LanguageTextEdit(range(0, 1, 1, 1), "y"))))
    }

    @Test
    fun `capabilities read static options and dynamic registrations`() {
        val static = LspCapabilities(json("""{"textDocumentSync":2,"completionProvider":{"triggerCharacters":[".",":"]},"renameProvider":{"prepareProvider":true}}""").jsonObject)
        assertEquals(LspCapabilities.Sync.Incremental, static.sync)
        assertTrue(static.openClose)
        assertFalse(static.save)
        assertEquals(setOf(".", ":"), static.completionTriggers)
        assertTrue(static.renamePrepares)
        assertFalse(static.formatting)
        val registered = static.withRegistration("textDocument/formatting", json("{}").jsonObject)
        assertTrue(registered.formatting)
        assertFalse(registered.withoutRegistration("textDocument/formatting").formatting)
        assertEquals(listOf("Completion", "Formatting", "Rename"), registered.labels())
    }

    @Test
    fun `workspace configuration is answered by section`() {
        val settings = json("""{"python":{"analysis":{"typeCheckingMode":"strict"}}}""").jsonObject
        val answer = LspMessages.configuration(settings, json("""{"items":[{"section":"python.analysis"},{"section":"missing"},{}]}"""))
        assertEquals("""[{"typeCheckingMode":"strict"},null,{"python":{"analysis":{"typeCheckingMode":"strict"}}}]""", answer.toString())
    }
}
