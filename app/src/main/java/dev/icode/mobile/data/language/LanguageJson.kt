package dev.icode.mobile.data.language

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Small readers over the protocol's JSON, where a server's odd value is skipped, never thrown on. */
internal object LanguageJson {
    /** [text] as a JSON object: a configuration's options or settings; empty for blank text. Throws for anything else. */
    fun objectOf(text: String): JsonObject {
        if (text.isBlank()) return JsonObject(emptyMap())
        return Json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("Enter a JSON object, in braces")
    }

    fun string(element: JsonElement?, key: String): String? = ((element as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun objects(element: JsonElement?): List<JsonObject> = (element as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

    /** A `string | MarkupContent | MarkedString | MarkedString[]` as plain text, for a list or a sheet. */
    fun markup(element: JsonElement?): String? = when (element) {
        null -> null
        is JsonPrimitive -> element.content.takeIf { element.isString }
        is JsonArray -> element.mapNotNull(::markup).joinToString("\n\n").ifBlank { null }
        is JsonObject -> string(element, "value")?.let { value ->
            // A MarkedString's code is fenced, as MarkupContent's markdown would have it.
            string(element, "language")?.let { "```$it\n$value\n```" } ?: value
        }
    }?.trim()?.ifEmpty { null }
}

/** Work a server reports doing (`$/progress`): its title, kept by token, then its latest message. */
internal class LspProgress {
    private val titles = HashMap<String, String>()

    /** What to show now; null when the work ended or nothing is said. */
    @Synchronized
    fun take(params: JsonElement?): String? {
        val token = ((params as? JsonObject)?.get("token") as? JsonPrimitive)?.content ?: return null
        val value = (params as JsonObject)["value"] as? JsonObject ?: return null
        when (LanguageJson.string(value, "kind")) {
            "begin" -> LanguageJson.string(value, "title")?.let { titles[token] = it }
            "end" -> {
                titles.remove(token)
                return null
            }
        }
        val percent = (value["percentage"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
        return listOfNotNull(titles[token], LanguageJson.string(value, "message"), percent?.let { "$it%" }).joinToString(" · ").ifEmpty { null }
    }
}
