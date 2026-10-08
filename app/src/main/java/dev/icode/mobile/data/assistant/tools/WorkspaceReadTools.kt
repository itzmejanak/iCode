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

// What the assistant reads (§4.3): never asked about, never written.

/** A folder's entries, or with `recursive` every file under it as Git sees them (ignored ones left out). */
internal class ListFilesTool(
    private val workspace: AssistantWorkspace,
    private val search: WorkspaceSearchEngine,
) : AssistantTool {
    override val name = "list_files"
    override val description = "Lists a folder of the workspace: its folders (ending in /) and files. " +
        "With recursive, every file under it instead, leaving out what .gitignore ignores (such as node_modules)."
    override val parameters = listOf(
        AssistantParameter("path", "The folder, relative to the workspace's top; \".\" is the top", required = false),
        AssistantParameter("recursive", "List every file under the folder", AssistantParameterType.Boolean, required = false),
    )
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String =
        "List ${arguments.textOrNull("path") ?: "."}" + if (arguments.flag("recursive")) ", all files" else ""

    override suspend fun run(arguments: JSONObject): String {
        val folder = workspace.resolve(arguments.textOrNull("path") ?: ".")
        if (!folder.isDirectory) throw AssistantToolException("${workspace.relative(folder)} is not a folder")
        val names = if (arguments.flag("recursive")) everything(folder) else entries(folder)
        if (names.isEmpty()) return "(empty)"
        val shown = names.take(MAX_ENTRIES).joinToString("\n")
        return if (names.size > MAX_ENTRIES) "$shown\n(${names.size - MAX_ENTRIES} more not shown)" else shown
    }

    private fun entries(folder: File): List<String> = folder.listFiles().orEmpty()
        .filter { it.name != ".git" }
        .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        .map { if (it.isDirectory) "${it.name}/" else it.name }

    private suspend fun everything(folder: File): List<String> {
        (search.ensureAvailable() as? RipgrepAvailability.Failed)?.let { throw AssistantToolException(it.reason) }
        val index = search.index(workspace.root.path)
        val under = if (folder == workspace.root) "" else workspace.relative(folder) + "/"
        return index.files.map { workspace.relative(File(it.path)) }.filter { it.startsWith(under) }
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
    override val description = "Reads a text file of the workspace. Lines are numbered; a long file is read " +
        "$MAX_LINES lines at a time from start_line."
    override val parameters = listOf(
        AssistantParameter("path", "The file, relative to the workspace's top"),
        AssistantParameter("start_line", "The first line to read, from 1", AssistantParameterType.Integer, required = false),
        AssistantParameter("line_count", "How many lines, at most $MAX_LINES", AssistantParameterType.Integer, required = false),
    )
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = "Read ${arguments.textOrNull("path") ?: "a file"}"

    override fun file(arguments: JSONObject): String? =
        arguments.textOrNull("path")?.let { path -> runCatching { workspace.relative(workspace.resolve(path)) }.getOrNull() }

    override suspend fun run(arguments: JSONObject): String {
        val file = workspace.resolve(arguments.text("path"))
        if (!file.isFile) throw AssistantToolException("${workspace.relative(file)} is not a file")
        val text = try {
            source.readDocument(file.path).text
        } catch (failure: IOException) {
            throw AssistantToolException(failure.message ?: "Unable to read ${workspace.relative(file)}")
        }
        if ('\u0000' in text) throw AssistantToolException("${workspace.relative(file)} is a binary file")
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

/** Text across the workspace, through ripgrep as Search uses it. */
internal class SearchTextTool(
    private val workspace: AssistantWorkspace,
    private val search: WorkspaceSearchEngine,
) : AssistantTool {
    override val name = "search_text"
    override val description = "Searches the workspace's files for text (or a regular expression), " +
        "leaving out what .gitignore ignores. Answers path:line: text for each match."
    override val parameters = listOf(
        AssistantParameter("query", "The text to find"),
        AssistantParameter("regex", "The query is a regular expression", AssistantParameterType.Boolean, required = false),
        AssistantParameter("match_case", "Upper and lower case differ", AssistantParameterType.Boolean, required = false),
    )
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = "Search “${arguments.textOrNull("query").orEmpty()}”"

    override suspend fun run(arguments: JSONObject): String {
        val query = arguments.text("query").ifEmpty { throw AssistantToolException("The query is empty") }
        (search.ensureAvailable() as? RipgrepAvailability.Failed)?.let { throw AssistantToolException(it.reason) }
        val outcome = search.search(workspace.root.path, query, arguments.flag("match_case"), wholeWord = false, regex = arguments.flag("regex"))
        outcome.warning?.let { if (outcome.results.isEmpty()) throw AssistantToolException(it) }
        val lines = outcome.results.flatMap { result ->
            val path = workspace.relative(File(result.path))
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
