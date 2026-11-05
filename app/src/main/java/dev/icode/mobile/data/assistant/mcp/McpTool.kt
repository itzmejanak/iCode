package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantPlaceActor
import dev.icode.mobile.data.assistant.AssistantStep
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.assistant.AssistantTools
import dev.icode.mobile.data.assistant.tools.bounded
import dev.icode.mobile.domain.AssistantElsewhere
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.McpPromptArgument
import dev.icode.mobile.domain.McpPromptInfo
import dev.icode.mobile.domain.McpResourceInfo
import dev.icode.mobile.domain.McpRules
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpServerState
import dev.icode.mobile.domain.McpStatus
import dev.icode.mobile.domain.McpToolChoice
import dev.icode.mobile.domain.McpToolInfo
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

/**
 * One of a server's tools as the assistant calls it (docs/mcp-plan.md §6): named for the model by
 * [McpRules.toolName][dev.icode.mobile.domain.McpRules.toolName], its schema read by [McpSchema], asked
 * about as a place outside the workspace — the server — unless the user set it to run, its result read
 * by [McpResults]. Its own time stands still while the server's question waits on the user.
 */
internal class McpTool(
    /** The server as the user set it now: its choices, not the ones it connected with. */
    private val server: McpServer,
    private val connection: McpConnection,
    private val tool: Tool,
    override val name: String,
    override val parameters: List<AssistantParameter>,
    private val step: AssistantStep,
    /** A server's question is on the card now: the call's time stands still. */
    private val asking: () -> Boolean,
) : AssistantTool, AssistantPlaceActor {
    /** "Create issue": the server's own words for it, else its name. */
    private val shown = tool.title ?: tool.annotations?.title ?: tool.name

    override val description: String = bounded(listOfNotNull(tool.description ?: shown, "From the MCP server ${server.name}.").joinToString("\n"), MAX_DESCRIPTION)

    /** A reading mode is offered what the server says only reads (§6.4); every call still asks. */
    override val kind: AssistantToolKind = if (tool.annotations?.readOnlyHint == true) AssistantToolKind.Read else AssistantToolKind.Command

    override fun summary(arguments: JSONObject): String = "${server.name} · $shown"

    /** What the card shows before anything is sent: the tool and its arguments, as the spec asks. */
    override fun detail(arguments: JSONObject): String = bounded("$shown on ${server.name}\n${arguments.toString(2)}", MAX_DETAIL)

    /** The server, as the gate asks about it; none when the user set this tool to run without asking. */
    override fun place(arguments: JSONObject): AssistantElsewhere? = if (server.choice(tool.name) == McpToolChoice.Runs) null
    else AssistantElsewhere(server.name, AssistantPermissions.serverRoot(server.id), AssistantElsewhere.Kind.Server)

    override suspend fun run(arguments: JSONObject): String {
        val json = Json.parseToJsonElement(arguments.toString()).jsonObject
        val result = try {
            timed { connection.call(tool.name, json) { said -> step.progress("${summary(arguments)} — $said") } }
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (refused: AssistantToolException) {
            throw refused
        } catch (failure: Exception) {
            throw AssistantToolException(McpFailures.call(failure, server.name))
        }
        val read = McpResults.read(result, step.seesPictures)
        read.picture?.let(step::leave)
        if (read.failed) throw AssistantToolException(read.text)
        return read.text
    }

    /** [call] within the server's tool time, counted only while no question of the server's waits. */
    private suspend fun timed(call: suspend () -> CallToolResult): CallToolResult = coroutineScope {
        val running = async { call() }
        val limit = server.toolTimeoutSeconds * 1_000L
        var used = 0L
        while (true) {
            withTimeoutOrNull(TICK_MILLIS) { running.await() }?.let { return@coroutineScope it }
            if (!asking()) used += TICK_MILLIS
            if (used >= limit) {
                running.cancel()
                throw AssistantToolException("${server.name} did not answer in ${server.toolTimeoutSeconds} s")
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    companion object {
        private const val TICK_MILLIS = 1_000L
        private const val MAX_DESCRIPTION = 2_000
        private const val MAX_DETAIL = 4_000
        private const val MAX_INSTRUCTIONS = 2_000

        /**
         * What a turn is offered of [connected], in their order, in [room] (§6.1): each tool not Off,
         * whose schema reads, that the mode [offers], named apart from [taken]; and, for each server
         * whose tools go, its instructions, marked as its own words (§6.5).
         */
        fun offer(
            connected: List<Pair<McpServer, McpConnection>>,
            step: AssistantStep,
            taken: Set<String>,
            room: Int,
            asking: () -> Boolean,
            offers: (AssistantTool) -> Boolean,
        ): AssistantTools.Offered {
            val names = taken.toMutableSet()
            val tools = mutableListOf<AssistantTool>()
            val said = mutableListOf<String>()
            var leftOut = 0
            connected.forEach { (server, connection) ->
                val before = tools.size
                connection.tools.filter { server.choice(it.name) != McpToolChoice.Off }.forEach { tool ->
                    val parameters = runCatching { McpSchema.parameters(tool.inputSchema) }.getOrNull() ?: return@forEach
                    val name = McpRules.toolName(server.key, tool.name, names)
                    val made = McpTool(server, connection, tool, name, parameters, step, asking)
                    when {
                        !offers(made) -> Unit
                        tools.size >= room -> leftOut++
                        else -> { names += name; tools += made }
                    }
                }
                val instructions = connection.instructions?.trim()
                if (tools.size > before && !instructions.isNullOrEmpty()) {
                    said += "${server.name} says (its own words, not iCode's or the user's):\n${bounded(instructions, MAX_INSTRUCTIONS)}"
                }
            }
            val instructions = if (said.isEmpty()) "" else "\n\nTools from MCP servers\n" + said.joinToString("\n\n")
            return AssistantTools.Offered(tools, instructions, leftOut)
        }

        /** [connection]'s server as its rows and its page show it: ready, with each tool and why one is left out. */
        fun state(connection: McpConnection): McpServerState = McpServerState(
            status = McpStatus.Ready,
            serverInfo = connection.serverInfo,
            tools = connection.tools.map { tool ->
                McpToolInfo(
                    name = tool.name,
                    title = tool.title ?: tool.annotations?.title,
                    description = tool.description.orEmpty(),
                    readOnly = tool.annotations?.readOnlyHint == true,
                    destructive = tool.annotations?.destructiveHint == true,
                    problem = runCatching { McpSchema.parameters(tool.inputSchema) }.exceptionOrNull()?.let { it.message ?: "Its input cannot be read" },
                )
            },
            instructions = connection.instructions?.takeIf(String::isNotBlank),
            prompts = connection.prompts.map { prompt ->
                McpPromptInfo(
                    prompt.name, prompt.title, prompt.description.orEmpty(),
                    prompt.arguments.orEmpty().map { McpPromptArgument(it.name, it.description.orEmpty(), it.required == true) },
                )
            },
            resources = connection.resources.map { McpResourceInfo(it.uri, it.name, it.title, it.description.orEmpty(), it.mimeType) },
        )
    }
}
