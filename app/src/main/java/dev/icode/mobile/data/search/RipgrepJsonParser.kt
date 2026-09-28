package dev.icode.mobile.data.search

import dev.icode.mobile.domain.ContentMatch
import dev.icode.mobile.domain.ContentSearchResult
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONObject

/** Parses ripgrep's stable `--json` JSON-Lines protocol into editor locations. */
internal class RipgrepJsonParser(
    private val rootLocation: String,
    private val maxMatchesPerFile: Int,
    private val maxTotalMatches: Int,
) {
    private val matchesByPath = linkedMapOf<String, MutableList<ContentMatch>>()
    private var totalMatches = 0
    private var completedMatchingFiles = 0
    private var summarySearches: Int? = null
    private var truncated = false

    /** Returns false once one extra match proves the bounded result is truncated. */
    fun accept(line: String): Boolean {
        if (line.isBlank()) return true
        val message = JSONObject(line)
        when (message.getString("type")) {
            "match" -> if (!acceptMatch(message.getJSONObject("data"))) return false
            "end" -> completedMatchingFiles += 1
            "summary" -> summarySearches = message
                .getJSONObject("data")
                .getJSONObject("stats")
                .getInt("searches")
        }
        return true
    }

    fun outcome(): RipgrepSearchOutcome = RipgrepSearchOutcome(
        results = matchesByPath.map { (relativePath, matches) ->
            val displayPath = relativePath.removePrefix("./")
            ContentSearchResult(
                path = File(rootLocation, displayPath).absolutePath,
                name = File(displayPath).name,
                matches = matches.toList(),
            )
        },
        searchedFiles = summarySearches ?: completedMatchingFiles.coerceAtLeast(matchesByPath.size),
        truncated = truncated,
    )

    private fun acceptMatch(data: JSONObject): Boolean {
        val relativePath = data.getJSONObject("path").decodedText()
        val lineText = data.getJSONObject("lines").decodedText().removeLineEnding()
        val lineNumber = data.getInt("line_number")
        val matches = matchesByPath.getOrPut(relativePath) { mutableListOf() }
        val submatches = data.getJSONArray("submatches")
        for (index in 0 until submatches.length()) {
            if (totalMatches >= maxTotalMatches || matches.size >= maxMatchesPerFile) {
                truncated = true
                // A per-file limit should not hide matches from later files.
                return totalMatches < maxTotalMatches
            }
            val byteOffset = submatches.getJSONObject(index).getInt("start")
            matches += ContentMatch(
                lineNumber = lineNumber,
                column = utf16Column(lineText, byteOffset),
                lineText = lineText,
            )
            totalMatches += 1
        }
        return true
    }

    private fun JSONObject.decodedText(): String {
        if (has("text") && !isNull("text")) return getString("text")
        return String(Base64.getDecoder().decode(getString("bytes")), StandardCharsets.UTF_8)
    }

    /** ripgrep offsets are UTF-8 bytes; Sora editor columns are UTF-16 string indices. */
    private fun utf16Column(text: String, byteOffset: Int): Int {
        if (byteOffset <= 0) return 0
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val bounded = byteOffset.coerceAtMost(bytes.size)
        return String(bytes, 0, bounded, StandardCharsets.UTF_8).length
    }

    private fun String.removeLineEnding(): String =
        removeSuffix("\n").removeSuffix("\r")
}

internal data class RipgrepSearchOutcome(
    val results: List<ContentSearchResult>,
    val searchedFiles: Int,
    val truncated: Boolean,
    val warning: String? = null,
)
