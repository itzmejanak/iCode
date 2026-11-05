package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.assistant.tools.bounded
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceLink
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

/**
 * A server's prompt or resource as the composer's draft (docs/mcp-plan.md M8): what the user chose, in
 * words they read and may change before sending. Nothing goes to the model until they send it.
 */
internal object McpDrafts {
    /** A prompt's messages, one after another: their text, a resource's text, a link's address. */
    fun prompt(result: GetPromptResult): String = bounded(
        result.messages.mapNotNull { message ->
            when (val content = message.content) {
                is TextContent -> content.text
                is EmbeddedResource -> McpResults.resource(content.resource)
                is ResourceLink -> "${content.title ?: content.name}: ${content.uri}"
                else -> null
            }
        }.joinToString("\n\n"),
        MAX_CHARACTERS,
    )

    /** A resource's contents under its address. */
    fun resource(uri: String, result: ReadResourceResult): String =
        bounded("$uri\n\n" + result.contents.joinToString("\n\n", transform = McpResults::resource), MAX_CHARACTERS)

    /** As a tool's result is bounded. */
    private const val MAX_CHARACTERS = 32_000
}
