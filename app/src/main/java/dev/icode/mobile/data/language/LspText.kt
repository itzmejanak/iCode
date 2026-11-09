package dev.icode.mobile.data.language

import dev.icode.mobile.domain.EditorTextRules
import dev.icode.mobile.domain.TextPosition
import dev.icode.mobile.domain.TextRange
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Places in a text as LSP counts them (spec 3.17, "Text Documents"): zero-based lines split at
 * `\n`, `\r\n` and `\r`, and columns in UTF-16 code units — Kotlin's and Sora's own (D2), so an
 * offset needs no conversion of characters, only of lines.
 */
internal object LspText {
    fun positionOf(text: CharSequence, offset: Int): TextPosition {
        val end = offset.coerceIn(0, text.length)
        var line = 0
        var lineStart = 0
        var index = 0
        while (index < end) {
            when (text[index]) {
                '\n' -> { line++; lineStart = index + 1 }
                '\r' -> if (index + 1 < text.length && text[index + 1] == '\n') {
                    if (index + 1 == end) break
                    line++; index++; lineStart = index + 1
                } else {
                    line++; lineStart = index + 1
                }
            }
            index++
        }
        return TextPosition(line, end - lineStart)
    }

    /** [position]'s offset; past a line's end is its end, past the last line the text's end (spec 3.17 "Position"). */
    fun offsetOf(text: CharSequence, position: TextPosition): Int {
        var line = 0
        var index = 0
        while (line < position.line) {
            if (index >= text.length) return text.length
            when (text[index]) {
                '\n' -> line++
                '\r' -> { line++; if (index + 1 < text.length && text[index + 1] == '\n') index++ }
            }
            index++
        }
        var lineEnd = index
        while (lineEnd < text.length && text[lineEnd] != '\n' && text[lineEnd] != '\r') lineEnd++
        return (index + position.column.coerceAtLeast(0)).coerceAtMost(lineEnd)
    }

    /** One incremental change that turns [old] into [new], in [old]'s places; null when they are equal. */
    fun change(old: String, new: String): Pair<TextRange, String>? {
        val edit = EditorTextRules.splice(old, new) ?: return null
        return TextRange(positionOf(old, edit.start), positionOf(old, edit.end)) to edit.replacement
    }

    fun json(position: TextPosition): JsonObject = buildJsonObject { put("line", position.line); put("character", position.column) }

    fun json(range: TextRange): JsonObject = buildJsonObject { put("start", json(range.start)); put("end", json(range.end)) }

    fun positionFrom(json: JsonElement?): TextPosition? {
        val position = json as? JsonObject ?: return null
        val line = position["line"]?.jsonPrimitive?.intOrNull ?: return null
        val column = position["character"]?.jsonPrimitive?.intOrNull ?: return null
        return TextPosition(line.coerceAtLeast(0), column.coerceAtLeast(0))
    }

    fun rangeFrom(json: JsonElement?): TextRange? {
        val range = json as? JsonObject ?: return null
        val start = positionFrom(range["start"]) ?: return null
        val end = positionFrom(range["end"]) ?: return null
        return TextRange(start, end)
    }
}
