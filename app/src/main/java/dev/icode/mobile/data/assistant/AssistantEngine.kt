package dev.icode.mobile.data.assistant

import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantToolKind
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

    /** [id] as this engine knows it with [kind]: the catalogue's limits, or the user's where set. */
    fun model(kind: AssistantProviderKind, id: String, contextWindow: Long?, maxOutput: Long?): AssistantModel

    /**
     * Answers [turn]'s input: the model is asked, the tools it calls are run (one at a time, in
     * order), and it is asked again with their results, until a reply calls none or the step limit is
     * reached. Cancelling the caller stops it.
     */
    suspend fun run(turn: AssistantTurnRequest, events: AssistantEvents)

    /** A summary of [history] by the model, for the chat to go on from in fewer tokens (§12 R4). */
    suspend fun compact(connection: AssistantConnection, model: AssistantModel, instructions: String, history: List<AssistantMessage>): String
}

/** Where the model is: the user's settings with the key, decrypted only for the turn. */
internal data class AssistantConnection(
    val kind: AssistantProviderKind,
    val baseUrl: String?,
    val model: String,
    val apiKey: String?,
) {
    override fun toString(): String = "AssistantConnection($kind, $baseUrl, $model)"
}

internal data class AssistantTurnRequest(
    val connection: AssistantConnection,
    /** The model with its limits: an Anthropic request, or a typed model's, carries its output limit. */
    val model: AssistantModel,
    /** Null: the provider's default. */
    val effort: AssistantEffort?,
    /** The system prompt: who the assistant is, where it works, and a compacted chat's summary. */
    val instructions: String,
    /** The chat since its last compaction, every call answered ([dev.icode.mobile.domain.AssistantRules.forModel]). */
    val history: List<AssistantMessage>,
    val input: AssistantMessage.User,
    val tools: List<AssistantTool>,
    val maxSteps: Int,
)

/** What a turn reports as it goes; called on the turn's coroutine, in order. */
internal interface AssistantEvents {
    /** A reply: its text, and the calls about to run. */
    fun replied(reply: AssistantMessage.Reply)

    /** [call] of the last reply runs now; its tool learns which call it serves from this. */
    fun calling(call: AssistantCall)

    /** One call of the last reply has its result. */
    fun answered(result: AssistantResult)

    /** The tokens the last request used, read and written: how full the context is. */
    fun used(tokens: Long)
}

/** A refusal the model reads as the tool's result: it can correct itself, ask, or explain. */
internal class AssistantToolException(message: String) : Exception(message)

/** A turn that could not go on, in the words the page shows. */
internal class AssistantTurnException(message: String, cause: Throwable? = null) : Exception(message, cause)

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

    /** What the model reads back. A refusal is an [AssistantToolException]. */
    suspend fun run(arguments: JSONObject): String
}

internal enum class AssistantParameterType { Text, Integer, Boolean }

internal data class AssistantParameter(
    val name: String,
    val description: String,
    val type: AssistantParameterType = AssistantParameterType.Text,
    val required: Boolean = true,
)
