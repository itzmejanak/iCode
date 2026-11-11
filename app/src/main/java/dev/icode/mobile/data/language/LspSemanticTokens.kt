package dev.icode.mobile.data.language

import dev.icode.mobile.domain.SemanticToken
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * A server's semantic tokens (spec 3.17 "Semantic Tokens"): its legend, read from its capabilities,
 * and its answers, which are integers five to a token, each place relative to the token before.
 */
internal object LspSemanticTokens {
    /** What a server's token numbers mean: [types] by index, [modifiers] by bit; [delta] when it answers `full/delta`. */
    class Legend(val types: List<String>, val modifiers: List<String>, val delta: Boolean)

    /** A whole answer's integers, and the id a delta asks from; null [resultId] when the server gives none. */
    class Data(val resultId: String?, val data: IntArray)

    /** `SemanticTokensOptions` (or its registration's options) as a legend; null when it does not answer `full`. */
    fun legendOf(options: JsonObject): Legend? {
        val legend = options["legend"] as? JsonObject ?: return null
        // `full: true`, or `full: { delta }`; a server answering only ranges is not asked.
        val delta = when (val full = options["full"]) {
            is JsonObject -> (full["delta"] as? JsonPrimitive)?.booleanOrNull == true
            is JsonPrimitive -> if (full.booleanOrNull == true) false else return null
            else -> return null
        }
        return Legend(strings(legend["tokenTypes"]), strings(legend["tokenModifiers"]), delta)
    }

    /**
     * The integers an answer stands for: a full answer's own (`SemanticTokens`), or [previous]'s with a
     * delta's edits applied (`SemanticTokensDelta`); null for no answer, or a delta with nothing to apply it to.
     */
    fun dataOf(result: JsonElement?, previous: IntArray?): Data? {
        val answer = result as? JsonObject ?: return null
        // `"resultId": null` is no id, not the id "null".
        val resultId = LanguageJson.string(answer, "resultId")
        (answer["data"] as? JsonArray)?.let { return Data(resultId, ints(it)) }
        val edits = answer["edits"] as? JsonArray ?: return null
        return Data(resultId, applied(previous ?: return null, edits) ?: return null)
    }

    /** `SemanticTokensEdit`s, each at a place in [previous], applied; null when one reaches past its end. */
    fun applied(previous: IntArray, edits: JsonArray): IntArray? {
        val sorted = edits.mapNotNull { it as? JsonObject }.map { edit ->
            Triple((edit["start"] as? JsonPrimitive)?.intOrNull ?: return null, (edit["deleteCount"] as? JsonPrimitive)?.intOrNull ?: 0, ints(edit["data"] as? JsonArray))
        }.sortedBy { it.first }
        val out = ArrayList<Int>(previous.size)
        var at = 0
        for ((start, deleteCount, data) in sorted) {
            if (start < at || start + deleteCount > previous.size) return null
            for (index in at until start) out += previous[index]
            data.forEach { out += it }
            at = start + deleteCount
        }
        for (index in at until previous.size) out += previous[index]
        return out.toIntArray()
    }

    /**
     * [data] as tokens at their own lines and columns, each type and modifier named by [legend]. A
     * token whose type the legend lacks is left out; its place still counts for the next one.
     */
    fun decode(data: IntArray, legend: Legend): List<SemanticToken> {
        val tokens = ArrayList<SemanticToken>(data.size / 5)
        // One set per combination of modifiers, shared by every token that has it.
        val modifierSets = HashMap<Int, Set<String>>()
        var line = 0
        var column = 0
        var index = 0
        while (index + 4 < data.size) {
            val deltaLine = data[index]
            line += deltaLine
            column = if (deltaLine == 0) column + data[index + 1] else data[index + 1]
            val length = data[index + 2]
            val type = legend.types.getOrNull(data[index + 3])
            val bits = data[index + 4]
            if (type != null && length > 0) {
                val modifiers = modifierSets.getOrPut(bits) { legend.modifiers.filterIndexed { bit, _ -> bits and (1 shl bit) != 0 }.toSet() }
                tokens += SemanticToken(line, column, length, type, modifiers)
            }
            index += 5
        }
        return tokens
    }

    private fun strings(array: JsonElement?): List<String> = (array as? JsonArray)?.map { (it as? JsonPrimitive)?.content.orEmpty() }.orEmpty()

    private fun ints(array: JsonArray?): IntArray = array?.let { values -> IntArray(values.size) { (values[it] as? JsonPrimitive)?.intOrNull ?: 0 } } ?: IntArray(0)
}
