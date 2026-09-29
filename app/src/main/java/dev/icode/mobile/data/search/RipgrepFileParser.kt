package dev.icode.mobile.data.search

import dev.icode.mobile.domain.IndexedFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Collects NUL-delimited `rg --files` path records without ever splitting on a filename newline. */
internal class RipgrepFileParser(
    private val rootLocation: String,
    private val maxFiles: Int,
) {
    private val files = mutableListOf<Pair<String, IndexedFile>>()
    private var truncated = false

    /** Returns false only when one extra path proves the index is truncated. */
    fun accept(record: ByteArray): Boolean {
        if (record.isEmpty()) return true
        if (files.size >= maxFiles) {
            truncated = true
            return false
        }
        val relative = decodeUtf8(record).removePrefix("./")
        files += relative to IndexedFile(
            name = File(relative).name,
            path = File(rootLocation, relative).absolutePath,
        )
        return true
    }

    fun outcome(warning: String? = null): RipgrepIndexOutcome = RipgrepIndexOutcome(
        files = files.sortedWith(compareBy({ it.first.lowercase() }, { it.first })).map { it.second },
        truncated = truncated,
        warning = warning,
    )

    private fun decodeUtf8(bytes: ByteArray): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}

internal data class RipgrepIndexOutcome(
    val files: List<IndexedFile>,
    val truncated: Boolean,
    val warning: String?,
)
