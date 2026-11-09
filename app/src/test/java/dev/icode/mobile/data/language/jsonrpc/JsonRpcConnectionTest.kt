package dev.icode.mobile.data.language.jsonrpc

import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonRpcConnectionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = FakeServerEnd()
    private val notifications = Collections.synchronizedList(mutableListOf<String>())
    private val handler = object : JsonRpcConnection.Handler {
        override suspend fun request(method: String, params: JsonElement?): JsonElement? = when (method) {
            "workspace/configuration" -> JsonPrimitive("configured")
            else -> throw JsonRpcConnection.JsonRpcException(JsonRpcConnection.METHOD_NOT_FOUND, "No $method")
        }

        override fun notification(method: String, params: JsonElement?) {
            notifications += method + ":" + params?.jsonObject?.get("n")
        }
    }
    private val connection = JsonRpcConnection(server.toClient.input, server.toServer.output, scope, handler).also { it.start() }

    @After
    fun stop() {
        server.hangUp()
        scope.cancel()
    }

    @Test
    fun `answers that come out of order reach the request that asked`() = runBlocking {
        val first = async(Dispatchers.IO) { connection.request("first", null, 5_000) }
        val second = async(Dispatchers.IO) { connection.request("second", null, 5_000) }
        val asked = listOf(server.receive(), server.receive())
        val byMethod = asked.associateBy { it["method"]!!.jsonPrimitive.content }
        server.send("""{"jsonrpc":"2.0","id":${byMethod.getValue("second")["id"]},"result":2}""")
        server.send("""{"jsonrpc":"2.0","id":${byMethod.getValue("first")["id"]},"result":1}""")
        assertEquals(1, first.await().jsonPrimitive.int)
        assertEquals(2, second.await().jsonPrimitive.int)
    }

    @Test
    fun `an error answer is the server's code and words`() = runBlocking {
        val asked = async(Dispatchers.IO) { runCatching { connection.request("textDocument/hover", null, 5_000) } }
        val id = server.receive()["id"]!!.jsonPrimitive.long
        server.send("""{"jsonrpc":"2.0","id":$id,"error":{"code":-32801,"message":"content modified"}}""")
        val failure = asked.await().exceptionOrNull() as JsonRpcConnection.JsonRpcException
        assertEquals(JsonRpcConnection.CONTENT_MODIFIED, failure.code)
        assertEquals("content modified", failure.message)
    }

    @Test
    fun `a request without an answer in time is cancelled at the server`() = runBlocking {
        val failure = runCatching { connection.request("slow", null, 200) }.exceptionOrNull()
        assertTrue(failure is JsonRpcConnection.TimeoutException)
        val id = server.receive()["id"]!!.jsonPrimitive.long
        val cancel = server.receive()
        assertEquals("\$/cancelRequest", cancel["method"]!!.jsonPrimitive.content)
        assertEquals(id, cancel["params"]!!.jsonObject["id"]!!.jsonPrimitive.long)
    }

    @Test
    fun `the server's requests are answered, an unknown one with MethodNotFound, keeping its id`() = runBlocking {
        server.send("""{"jsonrpc":"2.0","id":"s1","method":"workspace/configuration","params":{}}""")
        server.send("""{"jsonrpc":"2.0","id":7,"method":"unknown/thing"}""")
        val replies = listOf(server.receive(), server.receive()).associateBy { it["id"].toString() }
        assertEquals("configured", replies.getValue("\"s1\"")["result"]!!.jsonPrimitive.content)
        assertEquals(JsonRpcConnection.METHOD_NOT_FOUND, replies.getValue("7")["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
    }

    @Test
    fun `notifications reach the handler in the order they came, and iCode's go out in order`() = runBlocking {
        (1..20).forEach { server.send("""{"jsonrpc":"2.0","method":"note","params":{"n":$it}}""") }
        withTimeout(5_000) { while (notifications.size < 20) Thread.sleep(5) }
        assertEquals((1..20).map { "note:$it" }, notifications.toList())
        (1..20).forEach { connection.notify("textDocument/didChange", buildJsonObject { put("n", it) }) }
        assertEquals((1..20).toList(), (1..20).map { server.receive()["params"]!!.jsonObject["n"]!!.jsonPrimitive.int })
    }

    @Test
    fun `the server's end fails what waits, and later requests at once`() = runBlocking {
        val waiting = async(Dispatchers.IO) { runCatching { connection.request("never", null, 5_000) } }
        server.receive()
        server.hangUp()
        assertTrue(waiting.await().exceptionOrNull() is JsonRpcConnection.ClosedException)
        assertEquals(null, withTimeout(5_000) { connection.closed.await() })
        try {
            connection.request("after", null, 5_000)
            fail("A closed connection must refuse a request")
        } catch (closed: JsonRpcConnection.ClosedException) {
            Unit
        }
    }
}
