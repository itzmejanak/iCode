package dev.icode.mobile.data.language

import dev.icode.mobile.domain.SemanticToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Semantic tokens as the protocol writes them: the legend, the relative integers, deltas (S7). */
class LspSemanticTokensTest {
    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private val legend = LspSemanticTokens.Legend(listOf("parameter", "property", "function"), listOf("declaration", "readonly"), delta = true)

    @Test
    fun `the legend is read from the server's capabilities, and a server answering only ranges is not asked`() {
        val full = LspCapabilities(json("""{"semanticTokensProvider":{"legend":{"tokenTypes":["parameter"],"tokenModifiers":["readonly"]},"full":true}}""")).semanticTokens!!
        assertEquals(listOf("parameter"), full.types)
        assertEquals(listOf("readonly"), full.modifiers)
        assertFalse(full.delta)
        val delta = LspCapabilities(json("""{"semanticTokensProvider":{"legend":{"tokenTypes":[],"tokenModifiers":[]},"full":{"delta":true}}}""")).semanticTokens!!
        assertTrue(delta.delta)
        assertNull(LspCapabilities(json("""{"semanticTokensProvider":{"legend":{"tokenTypes":[],"tokenModifiers":[]},"range":true}}""")).semanticTokens)
        assertNull(LspCapabilities(json("""{"hoverProvider":true}""")).semanticTokens)
        val registered = LspCapabilities(json("{}")).withRegistration("textDocument/semanticTokens", json("""{"legend":{"tokenTypes":["property"],"tokenModifiers":[]},"full":true}"""))
        assertEquals(listOf("property"), registered.semanticTokens!!.types)
        assertTrue("Semantic colouring" in registered.labels())
    }

    @Test
    fun `relative integers become tokens at their own lines and columns, named by the legend`() {
        val data = intArrayOf(
            // line 0, column 4: a parameter, declared
            0, 4, 3, 0, 1,
            // same line, 6 further: a property, readonly
            0, 6, 5, 1, 2,
            // two lines down, column 2: a function
            2, 2, 4, 2, 0,
            // a type the legend lacks: left out, but its place counts
            0, 5, 1, 9, 0,
            // same line again, counted from the left-out token
            0, 3, 2, 0, 3,
        )
        assertEquals(
            listOf(
                SemanticToken(0, 4, 3, "parameter", setOf("declaration")),
                SemanticToken(0, 10, 5, "property", setOf("readonly")),
                SemanticToken(2, 2, 4, "function", emptySet()),
                SemanticToken(2, 10, 2, "parameter", setOf("declaration", "readonly")),
            ),
            LspSemanticTokens.decode(data, legend),
        )
    }

    @Test
    fun `a delta's edits apply to the last answer's integers`() {
        val previous = intArrayOf(0, 4, 3, 0, 0, 1, 2, 5, 1, 0, 1, 0, 2, 2, 0)
        // The second token replaced, the third deleted, one added at the end.
        val edits = Json.parseToJsonElement("""[{"start":15,"deleteCount":0,"data":[2,0,1,0,0]},{"start":5,"deleteCount":10,"data":[1,2,6,1,0]}]""").jsonArray
        assertArrayEquals(intArrayOf(0, 4, 3, 0, 0, 1, 2, 6, 1, 0, 2, 0, 1, 0, 0), LspSemanticTokens.applied(previous, edits))
        assertNull(LspSemanticTokens.applied(previous, Json.parseToJsonElement("""[{"start":14,"deleteCount":5}]""").jsonArray))
    }

    @Test
    fun `an answer is a whole file's integers, or a delta over the last ones`() {
        val whole = LspSemanticTokens.dataOf(json("""{"resultId":"1","data":[0,0,1,0,0]}"""), null)!!
        assertEquals("1", whole.resultId)
        assertArrayEquals(intArrayOf(0, 0, 1, 0, 0), whole.data)
        val delta = LspSemanticTokens.dataOf(json("""{"resultId":"2","edits":[{"start":2,"deleteCount":1,"data":[4]}]}"""), whole.data)!!
        assertEquals("2", delta.resultId)
        assertArrayEquals(intArrayOf(0, 0, 4, 0, 0), delta.data)
        assertNull(LspSemanticTokens.dataOf(json("""{"edits":[]}"""), null))
        assertNull(LspSemanticTokens.dataOf(null, null))
    }
}
