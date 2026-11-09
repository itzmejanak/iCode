package dev.icode.mobile.data.language

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LspDocumentsTest {
    private val sent = mutableListOf<Pair<String, JsonObject>>()
    private val documents = LspDocuments { method, params -> sent += method to params }

    private fun capabilities(json: String) = LspCapabilities(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `an incremental server gets one range change however many keystrokes it took, versions growing`() = runBlocking {
        documents.capabilities = capabilities("""{"textDocumentSync":{"openClose":true,"change":2}}""")
        documents.open("/w/a.py", "file:///workspace/w/a.py", "python", "print(1)\r\n")
        documents.change("/w/a.py", "print(12)\r\n")
        documents.change("/w/a.py", "print(123)\r\nx\r\n")
        assertEquals(listOf("textDocument/didOpen", "textDocument/didChange", "textDocument/didChange"), sent.map { it.first })
        val last = sent.last().second
        assertEquals(3, last["textDocument"]!!.jsonObject["version"]!!.jsonPrimitive.int)
        val change = last["contentChanges"]!!.jsonArray.single().jsonObject
        // "print(12)\r\n" → "print(123)\r\nx\r\n": the ")" at column 8 becomes "3)\r\nx".
        assertEquals("""{"start":{"line":0,"character":8},"end":{"line":0,"character":9}}""", change["range"].toString())
        assertEquals("3)\r\nx", change["text"]!!.jsonPrimitive.content)
        assertEquals(3, documents.versionOf("/w/a.py"))
    }

    @Test
    fun `a full-sync server gets the whole text, and unchanged text sends nothing`() = runBlocking {
        documents.capabilities = capabilities("""{"textDocumentSync":1}""")
        documents.open("/w/a.go", "file:///workspace/w/a.go", "go", "package a")
        documents.change("/w/a.go", "package a")
        documents.change("/w/a.go", "package b")
        assertEquals(2, sent.size)
        val change = sent.last().second["contentChanges"]!!.jsonArray.single().jsonObject
        assertNull(change["range"])
        assertEquals("package b", change["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a server that syncs nothing gets no changes, and one without open and close gets no documents`() = runBlocking {
        documents.capabilities = capabilities("""{"textDocumentSync":{"openClose":true,"change":0}}""")
        documents.open("/w/a", "file:///workspace/w/a", "plaintext", "x")
        documents.change("/w/a", "y")
        assertEquals(listOf("textDocument/didOpen"), sent.map { it.first })
        val closed = LspDocuments { method, _ -> sent += method to JsonObject(emptyMap()) }
        closed.capabilities = capabilities("""{}""")
        closed.open("/w/b", "file:///workspace/w/b", "plaintext", "x")
        assertFalse(closed.isOpen("/w/b"))
    }

    @Test
    fun `save is sent as the server asked, with the text only when it asked for it, and close forgets the version`() = runBlocking {
        documents.capabilities = capabilities("""{"textDocumentSync":{"openClose":true,"change":2,"save":{"includeText":true}}}""")
        documents.open("/w/a.ts", "file:///workspace/w/a.ts", "typescript", "let a")
        documents.saved("/w/a.ts")
        assertEquals("let a", sent.last().second["text"]!!.jsonPrimitive.content)
        assertTrue(documents.isOpen("/w/a.ts"))
        documents.close("/w/a.ts")
        assertEquals("textDocument/didClose", sent.last().first)
        assertNull(documents.versionOf("/w/a.ts"))
    }
}
