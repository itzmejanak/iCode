package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.ContentMatch
import dev.icode.mobile.domain.ContentSearchResult
import dev.icode.mobile.domain.IndexedFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Pure JVM workspace scanner shared by Quick Open and workspace search.
 *
 * Bounded by design: file count, per-file size, match count, and depth are
 * capped so a huge workspace cannot stall the gateway or the UI. Binary,
 * malformed, and oversized files are skipped, never crashing the scan.
 */
internal object WorkspaceIndexer {
    const val MAX_FILES = 2000
    const val MAX_FILE_BYTES = 5 * 1024 * 1024
    private const val MAX_MATCHES_PER_FILE = 200
    private const val MAX_TOTAL_MATCHES = 1000
    private const val MAX_DEPTH = 24

    /** Recursively index files under [rootLocation]; folders before files per level. */
    fun indexFiles(rootLocation: String): Pair<List<IndexedFile>, Boolean> {
        val root = File(rootLocation)
        if (!root.isDirectory) return emptyList<IndexedFile>() to false
        val files = mutableListOf<IndexedFile>()
        var truncated = false
        val queue = ArrayDeque<List<File>>().apply { addLast(childrenOf(root)) }
        var depth = 0
        while (queue.isNotEmpty() && files.size < MAX_FILES && depth <= MAX_DEPTH) {
            val level = queue.removeFirst()
            level.asSequence().forEach { child ->
                when {
                    child.isDirectory -> queue.addLast(childrenOf(child))
                    child.isFile && files.size < MAX_FILES ->
                        files += IndexedFile(child.name, child.canonicalFile.path, fileKindFor(child.name, isDirectory = false))
                }
                if (files.size >= MAX_FILES) truncated = true
            }
            depth += 1
        }
        return files.toList() to (truncated || files.size >= MAX_FILES)
    }

    /**
     * Search text files under [rootLocation] for [query].
     *
     * Invalid regex throws [PatternSyntaxException]; callers translate that
     * into user feedback. Everything else degrades to a partial result.
     */
    fun search(
        rootLocation: String,
        query: String,
        matchCase: Boolean,
        wholeWord: Boolean,
        regex: Boolean,
    ): ContentSearchOutcome {
        if (query.isBlank()) return ContentSearchOutcome(emptyList(), 0, false)
        val pattern = query.toSearchPattern(matchCase, wholeWord, regex)
        val results = mutableListOf<ContentSearchResult>()
        var searchedFiles = 0
        var truncated = false
        var totalMatches = 0
        val root = File(rootLocation)
        if (!root.isDirectory) return ContentSearchOutcome(emptyList(), 0, false)
        val queue = ArrayDeque<List<File>>().apply { addLast(childrenOf(root)) }
        var depth = 0
        while (queue.isNotEmpty() && !truncated && depth <= MAX_DEPTH) {
            val level = queue.removeFirst()
            level.asSequence().forEach { child ->
                if (truncated) return@forEach
                when {
                    child.isDirectory -> queue.addLast(childrenOf(child))
                    child.isFile -> {
                        if (searchedFiles >= MAX_FILES) {
                            truncated = true
                            return@forEach
                        }
                        searchedFiles += 1
                        val matches = searchFile(child, pattern)
                        if (matches.isNotEmpty()) {
                            totalMatches += matches.size
                            results += ContentSearchResult(
                                path = child.canonicalFile.path,
                                name = child.name,
                                matches = matches,
                            )
                        }
                        if (matches.size >= MAX_MATCHES_PER_FILE || totalMatches >= MAX_TOTAL_MATCHES) truncated = true
                    }
                }
            }
            depth += 1
        }
        return ContentSearchOutcome(results, searchedFiles, truncated)
    }

    private fun childrenOf(directory: File): List<File> =
        runCatching {
            directory.listFiles()
                ?.asSequence()
                ?.filterNot { it.name == ".git" }
                ?.sortedWith(compareBy({ it.isDirectory.not() }, { it.name.lowercase() }))
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())

    private fun searchFile(file: File, pattern: Pattern): List<ContentMatch> {
        val bytes = runCatching { file.inputStream().use(::readBounded) }.getOrNull() ?: return emptyList()
        val decoded = runCatching { DocumentCodec.decode(bytes) }.getOrNull() ?: return emptyList()
        val matches = mutableListOf<ContentMatch>()
        decoded.text.lineSequence().forEachIndexed { index, line ->
            if (matches.size >= MAX_MATCHES_PER_FILE) return matches
            val matcher = pattern.matcher(line)
            while (matcher.find() && matches.size < MAX_MATCHES_PER_FILE) {
                matches += ContentMatch(lineNumber = index + 1, column = matcher.start(), lineText = line)
            }
        }
        return matches
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_FILE_BYTES) throw IOException("File too large for search")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun String.toSearchPattern(matchCase: Boolean, wholeWord: Boolean, regex: Boolean): Pattern {
        if (isEmpty()) return Pattern.compile("$", Pattern.LITERAL)
        val body = when {
            regex -> this
            wholeWord -> "\\b${Pattern.quote(this)}\\b"
            else -> Pattern.quote(this)
        }
        val flags = if (matchCase) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return Pattern.compile(body, flags)
    }

    data class ContentSearchOutcome(
        val results: List<ContentSearchResult>,
        val searchedFiles: Int,
        val truncated: Boolean,
    )
}
