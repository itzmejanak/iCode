package dev.icode.mobile.data.assistant.mcp

import io.modelcontextprotocol.kotlin.sdk.types.Annotations
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Progress
import io.modelcontextprotocol.kotlin.sdk.types.ResourceLink
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import java.util.Base64
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/mcp-plan.md §6.3: what a tool answered, as the model reads it. */
class McpResultsTest {
    private val png = byteArrayOf(1, 2, 3, 4)
    private val image = ImageContent(Base64.getEncoder().encodeToString(png), "image/png")

    @Test
    fun `text, links and embedded resources are read in order`() {
        val read = McpResults.read(
            CallToolResult(
                listOf(
                    TextContent("Created #12"),
                    ResourceLink("issue", "https://github.com/o/r/issues/12", title = "Issue 12"),
                    EmbeddedResource(TextResourceContents("body text", "file:///a.txt", "text/plain")),
                ),
            ),
            seesPictures = false,
        )
        assertEquals("Created #12\nResource: Issue 12 — https://github.com/o/r/issues/12\nbody text", read.text)
        assertFalse(read.failed)
        assertNull(read.picture)
    }

    @Test
    fun `an image goes with the result to a model that sees, and is said to one that does not`() {
        val seen = McpResults.read(CallToolResult(listOf(image, image)), seesPictures = true)
        assertArrayEquals(png, seen.picture!!.bytes)
        assertEquals("image/png", seen.picture!!.mimeType)
        assertEquals("[image: image/png, 4 B — shown with this result]\n[image: image/png, 4 B — one picture goes with a step; this one is not shown]", seen.text)
        val blind = McpResults.read(CallToolResult(listOf(image)), seesPictures = false)
        assertNull(blind.picture)
        assertEquals("[image: image/png, 4 B — this model does not read images]", blind.text)
    }

    @Test
    fun `what is for the user alone never reaches the model`() {
        val read = McpResults.read(
            CallToolResult(listOf(TextContent("for the user", Annotations(audience = listOf(Role.User))), TextContent("for both", Annotations(audience = listOf(Role.User, Role.Assistant))))),
            seesPictures = false,
        )
        assertEquals("for both", read.text)
    }

    @Test
    fun `a failure is the server's own, and nothing said is said so`() {
        assertTrue(McpResults.read(CallToolResult(listOf(TextContent("Not found")), isError = true), false).failed)
        assertEquals("The tool failed and said nothing", McpResults.read(CallToolResult(emptyList(), isError = true), false).text)
        assertEquals("The tool answered nothing", McpResults.read(CallToolResult(emptyList()), false).text)
        // Structured content alone is its JSON.
        assertEquals("{\"n\":1}", McpResults.read(CallToolResult(emptyList(), structuredContent = buildJsonObject { put("n", 1) }), false).text)
    }

    @Test
    fun `a long answer is bounded as a command's output is`() {
        val read = McpResults.read(CallToolResult(listOf(TextContent("x".repeat(40_000)))), false)
        assertTrue(read.text.length < 33_000)
        assertTrue(read.text.contains("characters not shown"))
    }

    @Test
    fun `progress reads as the step says it`() {
        assertEquals("Uploading — 3 of 10", McpResults.progress(Progress(3.0, 10.0, "Uploading")))
        assertEquals("0.5", McpResults.progress(Progress(0.5)))
        assertEquals("2", McpResults.progress(Progress(2.0, message = " ")))
    }
}
