package dev.icode.mobile.data.assistant

import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.LineChanges
import java.io.File
import org.json.JSONObject

/**
 * What runs the assistant's turns (docs/assistant-koog-plan.md §4.1): Koog today
 * ([dev.icode.mobile.data.assistant.koog.KoogAssistantEngine]); an external agent or another
 * framework could be another. Nothing but this boundary's own types crosses it, so the coordinator,
 * the tools, the stores and the screens never change with the engine.
 */
internal interface AssistantEngine {
    /** The models this engine knows per provider — those that chat and call tools — with their limits. */
    val catalogue: Map<AssistantProviderKind, List<AssistantModel>>

    /** [spec] as this engine knows it with [kind]: the catalogue's facts, or what the user said of it. */
    fun model(kind: AssistantProviderKind, spec: AssistantModelSpec): AssistantModel

    /**
     * The models [connection]'s server offers, as it lists them (§13 P2): the engine's facts where it
     * knows a model, the server's where it says (Ollama). Asking proves the address and the key.
     */
    suspend fun available(connection: AssistantConnection): List<AssistantModel>

    /**
     * Answers [turn]'s input: the model is asked, the tools it calls are run (one at a time, in
     * order), and it is asked again with their results, until a reply calls none or the step limit is
     * reached. Cancelling the caller stops it.
     */
    suspend fun run(turn: AssistantTurnRequest, events: AssistantEvents)

    /** A summary of [history] by the model, for the chat to go on from in fewer tokens (§12 R4). */
    suspend fun compact(connection: AssistantConnection, model: AssistantModel, instructions: String, history: List<AssistantMessage>): String

    /** [request] answered by the model in one reply, with no tools and nothing streamed: a commit's message. */
    suspend fun answer(connection: AssistantConnection, model: AssistantModel, instructions: String, request: String): String
}

/** Where a provider's server is, with its key, decrypted only for the request. */
internal data class AssistantConnection(
    val kind: AssistantProviderKind,
    val baseUrl: String?,
    val apiKey: String?,
) {
    override fun toString(): String = "AssistantConnection($kind, $baseUrl)"
}

internal data class AssistantTurnRequest(
    val connection: AssistantConnection,
    /** The model, with its limits and what it reads: an Anthropic request, or a typed model's, carries its output limit. */
    val model: AssistantModel,
    /** Null: the provider's default. */
    val effort: AssistantEffort?,
    /** The system prompt: who the assistant is, where it works, and a compacted chat's summary. */
    val instructions: String,
    /** The chat since its last compaction, every call answered ([dev.icode.mobile.domain.AssistantRules.forModel]). */
    val history: List<AssistantMessage>,
    /** The new message; null when the turn goes on from [history] as it is, past a pause (docs/phone-agent-plan.md L5). */
    val input: AssistantMessage.User?,
    val tools: List<AssistantTool>,
    /** Requests at most; then [AssistantStepLimitException]. */
    val maxSteps: Int,
    /**
     * The picture a step's calls left for the model to see — the screen after them — taken once their
     * results are sent, and sent with them where the provider takes one (docs/phone-agent-plan.md L2).
     */
    val picture: () -> AssistantPicture? = { null },
)

/** A picture a step sends the model, with its results: a JPEG of the phone's screen. Never kept in the chat. */
internal class AssistantPicture(val bytes: ByteArray, val mimeType: String = "image/jpeg") {
    override fun toString(): String = "AssistantPicture(${bytes.size} bytes, $mimeType)"
}

/** What a turn reports as it goes; called on the turn's coroutine, in order. */
internal interface AssistantEvents {
    /** A reply being written, as far as it has come: its reasoning and its answer. Only a streamed one is. */
    fun writing(reasoning: String, text: String)

    /** A reply: its text, and the calls about to run. */
    fun replied(reply: AssistantMessage.Reply)

    /** [call] of the last reply runs now; its tool learns which call it serves from this. */
    fun calling(call: AssistantCall)

    /** One call of the last reply has its result. */
    fun answered(result: AssistantResult)

    /** The tokens the last request used, read and written: how full the context is. */
    fun used(tokens: Long)

    /** The server refused a picture to a model iCode was told sees them: the turn went on without it. */
    fun picturesRefused() = Unit
}

/** A refusal the model reads as the tool's result: it can correct itself, ask, or explain. */
internal class AssistantToolException(message: String) : Exception(message)

/** A turn that could not go on, in the words the page shows. */
internal open class AssistantTurnException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The turn reached its step limit: the user says whether it goes on (docs/phone-agent-plan.md L5). */
internal class AssistantStepLimitException : AssistantTurnException("Stopped at the step limit — send a message to continue")

/** One tool the model may call: iCode code over one of iCode's owners (§4.3). */
internal interface AssistantTool {
    /** As the model calls it: `read_file`. */
    val name: String
    val description: String
    val parameters: List<AssistantParameter>
    val kind: AssistantToolKind

    /** "Read src/App.kt": its step's words for [arguments]. Never throws. */
    fun summary(arguments: JSONObject): String

    /** What an approval shows before a change or a command; a read is never asked about. */
    fun detail(arguments: JSONObject): String = summary(arguments)

    /**
     * The workspace file [arguments] read or change, relative to its top, for the page to open;
     * null when they touch none, or name one outside the workspace. Never throws.
     */
    fun file(arguments: JSONObject): String? = null

    /** The command line [arguments] run, whole, for the page to show; null for a tool that runs none. Never throws. */
    fun command(arguments: JSONObject): String? = null

    /**
     * The files and folders [arguments] reach, as the runtime follows them: what the gate checks
     * against the chat's workspace (docs/assistant-permissions-plan.md §4.2). Never throws.
     */
    fun reach(arguments: JSONObject): List<File> = emptyList()

    /** What the model reads back. A refusal is an [AssistantToolException]. */
    suspend fun run(arguments: JSONObject): String
}

/** A tool that changes a file: by how many lines its last [AssistantTool.run] changed it, or null when it changed none. */
internal interface AssistantFileChanger {
    val lastChanges: LineChanges?
}

/**
 * A parameter's type: the JSON Schema shapes Koog carries to every provider (docs/mcp-plan.md §6.2).
 * iCode's own tools use the first three; an MCP server's tool may use any of them, nested.
 */
internal sealed interface AssistantParameterType {
    data object Text : AssistantParameterType
    data object Integer : AssistantParameterType
    data object Boolean : AssistantParameterType
    data object Number : AssistantParameterType
    data object Null : AssistantParameterType

    /** One of [values], as text. */
    data class Choice(val values: List<String>) : AssistantParameterType

    /** A list of [of]. */
    data class Items(val of: AssistantParameterType) : AssistantParameterType

    /** An object of [fields]; [more] the type of any other field it takes, null when it names them all. */
    data class Fields(val fields: List<AssistantParameter>, val more: AssistantParameterType? = null) : AssistantParameterType

    /** Any one of [options]. */
    data class OneOf(val options: List<AssistantParameter>) : AssistantParameterType
}

internal data class AssistantParameter(
    val name: String,
    val description: String,
    val type: AssistantParameterType = AssistantParameterType.Text,
    val required: Boolean = true,
)
