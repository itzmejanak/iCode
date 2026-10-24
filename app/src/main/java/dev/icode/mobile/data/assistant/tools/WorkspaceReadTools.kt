package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.search.RipgrepAvailability
import dev.icode.mobile.data.search.WorkspaceSearchEngine
import dev.icode.mobile.data.workspace.WorkspaceFileSource
import dev.icode.mobile.domain.AssistantToolKind
import java.io.File
import java.io.IOException
import org.json.JSONObject

// What the assistant reads (§4.3): never asked about, never written. Any path in the runtime
// (docs/runtime-contract-plan.md §4.3), each answered as the model writes it back ([AssistantWorkspace.shown]).

/** A folder's entries, or with `recursive` every file under it, ignored ones left out unless asked for. */
internal class ListFilesTool(
    private val workspace: AssistantWorkspace,
    private val search: WorkspaceSearchEngine,
) : AssistantTool {
    override val name = "list_files"
    override val description = "Lists a folder: its folders (ending in /) and files, Git's .git included. With recursive, " +
        "every file under it instead, leaving out .git and what .gitignore ignores (such as node_modules) unless ignored."
    override val parameters = listOf(
        AssistantParameter("path", "The folder; the workspace's top when absent", required = false),
        AssistantParameter("recursive", "List every file under the folder", AssistantParameterType.Boolean, required = false),
        AssistantParameter("ignored", "With recursive, also what .gitignore ignores", AssistantParameterType.Boolean, required = false),
    )
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String =
        "List ${arguments.textOrNull("path") ?: "."}" + if (arguments.flag("recursive")) ", all files" else ""

    override suspend fun run(arguments: JSONObject): String {
        val folder = workspace.existingFolder(arguments.textOrNull("path") ?: ".")
        val (names, cut) = if (arguments.flag("recursive")) everything(folder, arguments.flag("ignored")) else entries(folder) to false
        if (names.isEmpty()) return "(empty)"
        val shown = names.take(MAX_ENTRIES).joinToString("\n")
        val more = names.size - MAX_ENTRIES
        return when {
            more > 0 && cut -> "$shown\n(more than $more not shown; list a folder under it)"
            more > 0 -> "$shown\n($more more not shown)"
            else -> shown
        }
    }

    private fun entries(folder: File): List<String> = folder.listFiles().orEmpty()
        .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        .map { if (it.isDirectory) "${it.name}/" else it.name }

    /** Every file under [folder], and whether ripgrep stopped before the last. */
    private suspend fun everything(folder: File, ignored: Boolean): Pair<List<String>, Boolean> {
        (search.ensureAvailable() as? RipgrepAvailability.Failed)?.let { throw AssistantToolException(it.reason) }
        val index = search.index(folder.path, ignored)
        index.warning?.let { if (index.files.isEmpty()) throw AssistantToolException(it) }
        return index.files.map { workspace.shown(File(it.path)) } to index.truncated
    }

    private companion object {
        const val MAX_ENTRIES = 500
    }
}

/** A file's text with numbered lines, a window of it at a time. */
internal class ReadFileTool(
    private val workspace: AssistantWorkspace,
    private val source: WorkspaceFileSource,
) : AssistantTool {
    override val name = "read_file"
    override val description = "Reads a text file. Lines are numbered; a long file is read " +
        "$MAX_LINES lines at a time from start_line."
    override val parameters = listOf(
        AssistantParameter("path", "The file"),
        AssistantParameter("start_line", "The first line to read, from 1", AssistantParameterType.Integer, required = false),
        AssistantParameter("line_count", "How many lines, at most $MAX_LINES", AssistantParameterType.Integer, required = false),
    )
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = "Read ${arguments.textOrNull("path") ?: "a file"}"

    override fun file(arguments: JSONObject): String? =
        arguments.textOrNull("path")?.let { path -> runCatching { workspace.relative(workspace.resolve(path)) }.getOrNull() }

    override suspend fun run(arguments: JSONObject): String {
        val path = arguments.text("path")
        val file = workspace.existingFile(path)
        val text = try {
            source.readDocument(file.path).text
        } catch (failure: IOException) {
            throw AssistantToolException("$path: ${failure.message ?: "unable to read it"}")
        }
        if ('\u0000' in text) throw AssistantToolException("$path is a binary file")
        val lines = text.lines()
        val start = arguments.intOr("start_line", 1).coerceIn(1, lines.size.coerceAtLeast(1))
        val count = arguments.intOr("line_count", MAX_LINES).coerceIn(1, MAX_LINES)
        val window = lines.drop(start - 1).take(count)
        val numbered = bounded(
            window.mapIndexed { index, line -> "${start + index}\t$line" }.joinToString("\n"),
            MAX_CHARACTERS,
        )
        val end = start + window.size - 1
        return if (start == 1 && end >= lines.size) numbered
        else "$numbered\n(lines $start–$end of ${lines.size}; read on with start_line)"
    }

    private companion object {
        const val MAX_LINES = 2_000
        const val MAX_CHARACTERS = 100_000
    }
}

/** Text across a folder's files — the workspace's by default — through ripgrep as Search uses it. */
internal class SearchTextTool(
    private val workspace: AssistantWorkspace,
    private val search: WorkspaceSearchEngine,
) : AssistantTool {
    override val name = "search_text"
    override val description = "Searches files for text (or a regular expression): the workspace's, or a folder's, " +
        "leaving out .git and what .gitignore ignores unless ignored. Answers path:line: text for each match."
    override val parameters = listOf(
        AssistantParameter("query", "The text to find"),
        AssistantParameter("path", "The folder to search; the workspace's top when absent", required = false),
        AssistantParameter("regex", "The query is a regular expression", AssistantParameterType.Boolean, required = false),
        AssistantParameter("match_case", "Upper and lower case differ", AssistantParameterType.Boolean, required = false),
        AssistantParameter("ignored", "Also search what .gitignore ignores", AssistantParameterType.Boolean, required = false),
    )
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String =
        "Search “${arguments.textOrNull("query").orEmpty()}”" + (arguments.textOrNull("path")?.let { " in $it" } ?: "")

    override suspend fun run(arguments: JSONObject): String {
        val query = arguments.text("query").ifEmpty { throw AssistantToolException("The query is empty") }
        val folder = workspace.existingFolder(arguments.textOrNull("path") ?: ".")
        (search.ensureAvailable() as? RipgrepAvailability.Failed)?.let { throw AssistantToolException(it.reason) }
        val outcome = search.search(
            folder.path, query, arguments.flag("match_case"), wholeWord = false, regex = arguments.flag("regex"), ignored = arguments.flag("ignored"),
        )
        outcome.warning?.let { if (outcome.results.isEmpty()) throw AssistantToolException(it) }
        val lines = outcome.results.flatMap { result ->
            val path = workspace.shown(File(result.path))
            result.matches.map { "$path:${it.lineNumber}: ${it.lineText.trim().take(MAX_LINE)}" }
        }
        if (lines.isEmpty()) return "No matches"
        val shown = lines.take(MAX_MATCHES).joinToString("\n")
        val more = lines.size - MAX_MATCHES
        return when {
            more > 0 -> "$shown\n($more more matches not shown; narrow the query)"
            outcome.truncated -> "$shown\n(the search stopped early; narrow the query)"
            else -> shown
        }
    }

    private companion object {
        const val MAX_MATCHES = 200
        const val MAX_LINE = 240
    }
}
