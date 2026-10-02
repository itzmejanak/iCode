package dev.icode.mobile.data.assistant.koog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A call's arguments as Koog can carry them (docs/runtime-foundation-plan.md §3.12). Koog reads
 * them as a JSON object, and its OpenAI client, reading a whole reply, throws on any that are not
 * one — losing the reply, its text and its other calls. A long call cut at the output limit is
 * such a call. Its arguments travel wrapped in an object of their own ([readable]): no reader
 * throws, no provider is sent broken JSON, [KoogTool] refuses the call with [refusal], and the
 * page keeps what the model wrote ([unreadable]).
 */
internal object KoogArguments {
    /** [arguments] as they are when they are a JSON object; else an object that carries them. Blank ones are none. */
    fun readable(arguments: String): String = when {
        arguments.isBlank() -> NONE
        parsed(arguments) is JsonObject -> arguments
        else -> JsonObject(mapOf(KEY to JsonPrimitive(arguments))).toString()
    }

    /** What the model wrote, where [arguments] carry it ([readable]); null where they are its own. */
    fun unreadable(arguments: String): String? {
        val carried = (parsed(arguments) as? JsonObject)?.takeIf { it.size == 1 }?.get(KEY)
        return (carried as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    }

    /** What the model reads of a call whose arguments were not a JSON object: it did not run, and what likely happened. */
    fun refusal(written: String): String =
        "The call did not run: its arguments were not a complete JSON object (${written.length} characters, ending " +
            "“${written.takeLast(SHOWN_END)}”). A long call is cut at the output limit: write long content in parts — " +
            "a file with write_file, then a short command that runs it."

    /**
     * [body], a chat completion as OpenAI's API sends it, with each call's arguments [readable]: what
     * Koog's reader takes whole. A body with nothing to change is returned as it came.
     */
    fun readableResponse(body: String): String {
        val root = parsed(body) as? JsonObject ?: return body
        val choices = root[CHOICES] as? JsonArray ?: return body
        var changed = false
        val fixed = choices.map { element ->
            val choice = element as? JsonObject ?: return@map element
            val message = choice[MESSAGE] as? JsonObject ?: return@map choice
            val calls = message[TOOL_CALLS] as? JsonArray ?: return@map choice
            val readableCalls = calls.map { callElement ->
                val call = callElement as? JsonObject ?: return@map callElement
                val function = call[FUNCTION] as? JsonObject ?: return@map call
                val written = (function[ARGUMENTS] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@map call
                // Blank ones Koog reads as none itself; only what it would throw on is wrapped.
                if (written.isEmpty() || parsed(written) is JsonObject) return@map call
                changed = true
                JsonObject(call + (FUNCTION to JsonObject(function + (ARGUMENTS to JsonPrimitive(readable(written))))))
            }
            JsonObject(choice + (MESSAGE to JsonObject(message + (TOOL_CALLS to JsonArray(readableCalls)))))
        }
        return if (changed) JsonObject(root + (CHOICES to JsonArray(fixed))).toString() else body
    }

    private fun parsed(text: String): JsonElement? = runCatching { Json.parseToJsonElement(text) }.getOrNull()

    /** The key that carries arguments that were not an object: no tool of iCode's has a parameter by that name. */
    private const val KEY = "icode_unreadable_arguments"
    private const val NONE = "{}"
    private const val SHOWN_END = 80
    private const val CHOICES = "choices"
    private const val MESSAGE = "message"
    private const val TOOL_CALLS = "tool_calls"
    private const val FUNCTION = "function"
    private const val ARGUMENTS = "arguments"
}
