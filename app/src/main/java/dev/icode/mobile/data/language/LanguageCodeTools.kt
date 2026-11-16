package dev.icode.mobile.data.language

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.assistant.tools.intOr
import dev.icode.mobile.data.assistant.tools.text
import dev.icode.mobile.data.assistant.tools.textOrNull
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantToolName
import dev.icode.mobile.domain.DiagnosticRules
import dev.icode.mobile.domain.LanguagePlace
import dev.icode.mobile.domain.TextPosition
import java.io.File
import org.json.JSONObject

// The agent asks the language servers what the user would (docs/editor-and-language-services.md): problems, definitions,
// references, symbols, and the server's own rename and formatting, through the same owner the editor
// uses. Lines and columns are one-based here, as people and compilers count them.

internal fun languageCodeTools(services: LanguageServices, workspace: AssistantWorkspace): List<AssistantTool> = listOf(
    LanguageDiagnosticsTool(services, workspace),
    LanguagePlacesTool(services, workspace, references = false),
    LanguagePlacesTool(services, workspace, references = true),
    DocumentSymbolsTool(services, workspace),
    WorkspaceSymbolsTool(services, workspace),
    RenameSymbolTool(services, workspace),
    FormatFileTool(services, workspace),
)

/** A tool that asks about a file in the chat's workspace, which must be the one open in the editor. */
private abstract class LanguageFileTool(protected val services: LanguageServices, protected val workspace: AssistantWorkspace) : AssistantTool {
    override fun reach(arguments: JSONObject): List<File> = workspace.reach(arguments.textOrNull("path"))

    override fun file(arguments: JSONObject): String? = arguments.textOrNull("path")?.let { runCatching { workspace.relative(workspace.resolve(it)) }.getOrNull() }

    protected fun fileOf(arguments: JSONObject): File {
        if (services.workspace() != workspace.place) throw AssistantToolException("Language servers run for the workspace open in the editor: open ${workspace.name} there first")
        return workspace.existingFile(arguments.text("path"))
    }

    protected fun positionOf(arguments: JSONObject) = TextPosition(arguments.intOr("line", 1) - 1, arguments.intOr("column", 1) - 1)

    protected fun shown(places: List<LanguagePlace>): String = places.take(MAX_PLACES).joinToString("\n") { place ->
        "  ".repeat(place.depth) + "${place.file}:${DiagnosticRules.locationOf(place.range)}  ${place.preview}"
    } + if (places.size > MAX_PLACES) "\n(${places.size - MAX_PLACES} more)" else ""

    protected val position = listOf(
        AssistantParameter("path", "The file"),
        AssistantParameter("line", "The line, from 1", AssistantParameterType.Integer),
        AssistantParameter("column", "The column of the symbol, from 1", AssistantParameterType.Integer),
    )

    protected companion object {
        const val MAX_PLACES = 100
    }
}

private class LanguageDiagnosticsTool(services: LanguageServices, workspace: AssistantWorkspace) : LanguageFileTool(services, workspace) {
    override val name = AssistantToolName.LanguageDiagnostics.id
    override val description = "The language servers' problems (errors, warnings) in a file, as of its text now: run it after editing " +
        "to check the edit. It waits for the servers to report on the current text (starting one that serves the file if needed), " +
        "up to wait_seconds. Empty when no configured server serves the file: list_language_services shows them."
    override val parameters = listOf(
        AssistantParameter("path", "The file"),
        AssistantParameter("wait_seconds", "How long to wait for the servers' report, at most 60", AssistantParameterType.Integer, required = false),
    )
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = "Problems in ${arguments.textOrNull("path") ?: "a file"}"

    override suspend fun run(arguments: JSONObject): String {
        val file = fileOf(arguments)
        val wait = arguments.intOr("wait_seconds", DEFAULT_WAIT_SECONDS).coerceIn(1, MAX_WAIT_SECONDS) * 1000L
        val served = services.servingIds(file.path)
        if (served.isEmpty()) return "No configured language server serves ${workspace.shown(file)}."
        val problems = services.features.diagnose(file.path, wait)
        if (problems.isEmpty()) return "No problems reported in ${workspace.shown(file)}."
        return problems.joinToString("\n") { "${it.severity.name.lowercase()} ${it.location}: ${it.message} (${listOfNotNull(it.source, it.code).joinToString(" ")})" }
    }

    private companion object {
        const val DEFAULT_WAIT_SECONDS = 15
        const val MAX_WAIT_SECONDS = 60
    }
}

private class LanguagePlacesTool(services: LanguageServices, workspace: AssistantWorkspace, private val references: Boolean) : LanguageFileTool(services, workspace) {
    override val name = (if (references) AssistantToolName.FindReferences else AssistantToolName.FindDefinition).id
    override val description = if (references) "Every use of the symbol at a place in a file, as its language server finds them across the workspace."
    else "Where the symbol at a place in a file is defined, as its language server finds it."
    override val parameters = position
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = (if (references) "References" else "Definition") + " in ${arguments.textOrNull("path") ?: "a file"}"

    override suspend fun run(arguments: JSONObject): String {
        val file = fileOf(arguments)
        val places = runCatching {
            if (references) services.features.references(file.path, positionOf(arguments)) else services.features.definition(file.path, positionOf(arguments))
        }.getOrElse { throw AssistantToolException(LanguageFailure.wordsOf(it)) }
        return if (places.isEmpty()) "Nothing found there." else shown(places)
    }
}

private class DocumentSymbolsTool(services: LanguageServices, workspace: AssistantWorkspace) : LanguageFileTool(services, workspace) {
    override val name = AssistantToolName.DocumentSymbols.id
    override val description = "A file's outline from its language server: its classes, functions and other symbols, nested, with where each is."
    override val parameters = listOf(AssistantParameter("path", "The file"))
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = "Symbols in ${arguments.textOrNull("path") ?: "a file"}"

    override suspend fun run(arguments: JSONObject): String {
        val places = runCatching { services.features.documentSymbols(fileOf(arguments).path) }.getOrElse { throw AssistantToolException(LanguageFailure.wordsOf(it)) }
        return if (places.isEmpty()) "No symbols reported." else shown(places)
    }
}

private class WorkspaceSymbolsTool(services: LanguageServices, workspace: AssistantWorkspace) : LanguageFileTool(services, workspace) {
    override val name = AssistantToolName.WorkspaceSymbols.id
    override val description = "Symbols across the workspace whose names match a query, from the running language servers that offer it."
    override val parameters = listOf(AssistantParameter("query", "Part of the name"))
    override val kind = AssistantToolKind.Read

    override fun reach(arguments: JSONObject): List<File> = workspace.reach(".")

    override fun summary(arguments: JSONObject): String = "Symbols named ${arguments.textOrNull("query").orEmpty()}"

    override suspend fun run(arguments: JSONObject): String {
        if (services.workspace() != workspace.place) throw AssistantToolException("Language servers run for the workspace open in the editor: open ${workspace.name} there first")
        val places = runCatching { services.features.workspaceSymbols(arguments.text("query")) }.getOrElse { throw AssistantToolException(LanguageFailure.wordsOf(it)) }
        return if (places.isEmpty()) "No symbols found (only running servers that offer workspace symbols are asked)." else shown(places)
    }
}

private class RenameSymbolTool(services: LanguageServices, workspace: AssistantWorkspace) : LanguageFileTool(services, workspace) {
    override val name = AssistantToolName.RenameSymbol.id
    override val description = "Renames the symbol at a place, and every use the language server finds, in every file it changes. " +
        "Open files change in the editor, unsaved; closed ones on disk. Refused whole if a file changed meanwhile."
    override val parameters = position + AssistantParameter("new_name", "The new name")
    override val kind = AssistantToolKind.Change

    override fun summary(arguments: JSONObject): String = "Rename to ${arguments.textOrNull("new_name").orEmpty()} in ${arguments.textOrNull("path") ?: "a file"}"

    override suspend fun run(arguments: JSONObject): String {
        val files = services.features.rename(fileOf(arguments).path, positionOf(arguments), arguments.text("new_name")).getOrElse { throw AssistantToolException(LanguageFailure.wordsOf(it)) }
        return "Renamed in $files file${if (files == 1) "" else "s"}."
    }
}

private class FormatFileTool(services: LanguageServices, workspace: AssistantWorkspace) : LanguageFileTool(services, workspace) {
    override val name = AssistantToolName.FormatFile.id
    override val description = "Formats a file with its language server, at the workspace's indentation. An open file changes in the editor, unsaved."
    override val parameters = listOf(AssistantParameter("path", "The file"))
    override val kind = AssistantToolKind.Change

    override fun summary(arguments: JSONObject): String = "Format ${arguments.textOrNull("path") ?: "a file"}"

    override suspend fun run(arguments: JSONObject): String {
        val changed = services.features.format(fileOf(arguments).path).getOrElse { throw AssistantToolException(LanguageFailure.wordsOf(it)) }
        return if (changed == 0) "Already formatted." else "Formatted."
    }
}
