package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.assistant.AssistantPicture
import dev.icode.mobile.data.assistant.tools.bounded
import dev.icode.mobile.domain.ByteSize
import io.modelcontextprotocol.kotlin.sdk.types.Annotations
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Progress
import io.modelcontextprotocol.kotlin.sdk.types.ResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ResourceLink
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents

/**
 * What a tool answered, as the model reads it (docs/assistant-and-mcp.md): its text; an image as the
 * step's picture to a model that sees pictures, said in words to one that does not; what is meant for
 * the user alone left out; bounded as a command's output is.
 */
internal object McpResults {
    /** [text] for the model; [picture] the first image, for a model that sees; [failed] the server's own failure. */
    class Read(val text: String, val picture: AssistantPicture?, val failed: Boolean)

    fun read(result: CallToolResult, seesPictures: Boolean): Read {
        val forModel = result.content.filterNot { userOnly(it.annotations()) }
        var picture: AssistantPicture? = null
        val parts = forModel.map { block ->
            when (block) {
                is TextContent -> block.text
                is ImageContent -> {
                    val bytes = decoded(block.data)
                    when {
                        bytes == null -> "[an image the server sent could not be read]"
                        seesPictures && picture == null && block.mimeType in PICTURES -> {
                            picture = AssistantPicture(bytes, block.mimeType)
                            "[image: ${block.mimeType}, ${ByteSize.format(bytes.size.toLong())} — shown with this result]"
                        }
                        seesPictures -> "[image: ${block.mimeType}, ${ByteSize.format(bytes.size.toLong())} — one picture goes with a step; this one is not shown]"
                        else -> "[image: ${block.mimeType}, ${ByteSize.format(bytes.size.toLong())} — this model does not read images]"
                    }
                }
                is AudioContent -> "[audio: ${block.mimeType}, ${size(block.data)} — not passed to the model]"
                is ResourceLink -> "Resource: ${block.title ?: block.name} — ${block.uri}"
                is EmbeddedResource -> resource(block.resource)
                else -> "[content iCode does not read: ${block.type}]"
            }
        }
        val text = parts.joinToString("\n").ifBlank { result.structuredContent?.toString().orEmpty() }
            .ifBlank { if (result.isError == true) "The tool failed and said nothing" else "The tool answered nothing" }
        return Read(bounded(text, MAX_CHARACTERS), picture, result.isError == true)
    }

    /** A call's progress as its step says it: "Uploading — 3 of 10". */
    fun progress(progress: Progress): String {
        fun number(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)
        val done = progress.total?.let { "${number(progress.progress)} of ${number(it)}" } ?: number(progress.progress)
        return listOfNotNull(progress.message?.takeIf(String::isNotBlank), done).joinToString(" — ")
    }

    /** An embedded resource's text, or what its bytes are. */
    fun resource(contents: ResourceContents): String = when (contents) {
        is TextResourceContents -> contents.text
        is BlobResourceContents -> "[resource: ${contents.uri}, ${contents.mimeType ?: "binary"}, ${size(contents.blob)}]"
        else -> "[resource: ${contents.uri}]"
    }

    /** Content whose audience is the user alone: on the page, never sent to the model. */
    private fun userOnly(annotations: Annotations?): Boolean = annotations?.audience?.let { it.isNotEmpty() && Role.Assistant !in it } == true

    private fun ContentBlock.annotations(): Annotations? = when (this) {
        is TextContent -> annotations
        is ImageContent -> annotations
        is AudioContent -> annotations
        is ResourceLink -> annotations
        is EmbeddedResource -> annotations
        else -> null
    }

    private fun decoded(base64: String): ByteArray? = runCatching { java.util.Base64.getMimeDecoder().decode(base64) }.getOrNull()

    private fun size(base64: String): String = ByteSize.format(base64.length * 3L / 4)

    /** As `run_command`'s output is bounded. */
    private const val MAX_CHARACTERS = 32_000

    /** What every provider iCode talks to takes as a picture. */
    private val PICTURES = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
}
