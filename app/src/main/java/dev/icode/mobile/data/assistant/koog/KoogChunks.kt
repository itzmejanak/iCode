package dev.icode.mobile.data.assistant.koog

import dev.icode.mobile.data.assistant.AssistantTurnException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A streamed chunk as Koog's OpenAI client can read it (docs/runtime-foundation-plan.md §3.13). Koog
 * reads every event of a stream as a whole chunk of OpenAI's and throws on any that is not one,
 * ending the reply where it is: a chunk without `id`, `object`, `created` or `model`, a choice
 * without `index` or `delta`, a call without `index`, a blank or non-JSON event, an event of another
 * kind (a keep-alive) (F15). A compatible server need not be as strict as OpenAI (F14); the OpenAI SDK
 * reads them all. Such a chunk is completed with values Koog does not use and iCode never reads; an
 * event that carries no chunk is read as an empty one; one that says the provider failed is that
 * failure, in the provider's words.
 */
internal object KoogChunks {
    /** [data], one event of a stream, as a chunk Koog reads; as it came when it already is one. */
    fun readable(data: String): String {
        val root = runCatching { Json.parseToJsonElement(data) }.getOrNull() as? JsonObject ?: return EMPTY
        failure(root)?.let { throw AssistantTurnException(it) }
        // What Koog needs and is missing or of another type, with a value that says nothing.
        val envelope = ENVELOPE.filterNot { (key, _) ->
            val value = root[key] as? JsonPrimitive
            if (key == CREATED) value?.longOrNull != null else value?.isString == true
        }
        val choices = root[CHOICES] as? JsonArray
        var changed = envelope.isNotEmpty() || choices == null
        val fixed = choices.orEmpty().mapIndexed { position, element ->
            val choice = element as? JsonObject ?: JsonObject(emptyMap())
            var repaired = choice
            if ((choice[INDEX] as? JsonPrimitive)?.longOrNull == null) repaired = JsonObject(repaired + (INDEX to JsonPrimitive(position)))
            val delta = choice[DELTA] as? JsonObject
            repaired = when {
                delta == null -> JsonObject(repaired + (DELTA to JsonObject(emptyMap())))
                else -> readableCalls(delta)?.let { calls -> JsonObject(repaired + (DELTA to JsonObject(delta + (TOOL_CALLS to calls)))) } ?: repaired
            }
            if (repaired !== element) changed = true
            repaired
        }
        return if (changed) JsonObject(root + envelope + (CHOICES to JsonArray(fixed))).toString() else data
    }

    /**
     * [delta]'s calls, each with an `index` — 0 where it has none: a server that leaves it out streams
     * one call at a time, and Koog tells one call from the next by its `id`. Null when none lacked one.
     */
    private fun readableCalls(delta: JsonObject): JsonArray? {
        val calls = delta[TOOL_CALLS] as? JsonArray ?: return null
        fun indexed(call: JsonElement) = ((call as? JsonObject)?.get(INDEX) as? JsonPrimitive)?.longOrNull != null
        if (calls.all(::indexed)) return null
        return JsonArray(calls.map { call -> if (call is JsonObject && !indexed(call)) JsonObject(call + (INDEX to JsonPrimitive(0))) else call })
    }

    /**
     * Why the provider failed, as OpenAI's API says it — `{"error": {"message": …}}`, or the message
     * alone — in an event with no reply in it; null otherwise (`"error": null`, `false`, `""`).
     */
    private fun failure(root: JsonObject): String? {
        if ((root[CHOICES] as? JsonArray)?.isNotEmpty() == true) return null
        val said = when (val error = root[ERROR]) {
            is JsonObject -> (error[MESSAGE] as? JsonPrimitive)?.takeIf { it.isString }?.content
            is JsonPrimitive -> error.takeIf { it.isString && it.content.isNotBlank() }?.content ?: return null
            else -> return null
        }?.trim()?.take(MAX_MESSAGE)?.ifEmpty { null }
        return said?.let { "The model's provider failed while writing the reply: $it" } ?: KoogFailures.PROVIDER_FAILED
    }

    private const val CHOICES = "choices"
    private const val DELTA = "delta"
    private const val INDEX = "index"
    private const val TOOL_CALLS = "tool_calls"
    private const val CREATED = "created"
    private const val ERROR = "error"
    private const val MESSAGE = "message"
    private const val MAX_MESSAGE = 300

    /** What Koog requires of every chunk, each with a value that says nothing. */
    private val ENVELOPE: List<Pair<String, JsonElement>> = listOf(
        "id" to JsonPrimitive(""),
        "object" to JsonPrimitive("chat.completion.chunk"),
        CREATED to JsonPrimitive(0),
        "model" to JsonPrimitive(""),
    )

    /** A chunk with nothing of a reply in it. */
    private val EMPTY = JsonObject(ENVELOPE.toMap() + (CHOICES to JsonArray(emptyList()))).toString()
}
