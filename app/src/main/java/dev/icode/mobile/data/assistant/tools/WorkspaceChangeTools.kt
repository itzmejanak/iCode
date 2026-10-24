package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantFileChanger
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.workspace.WorkspaceDocument
import dev.icode.mobile.data.workspace.WorkspaceFileSource
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.LineChanges
import dev.icode.mobile.domain.countOf
import java.io.File
import java.io.IOException
import org.json.JSONObject

// What the assistant changes (§4.3, §4.5): any file in the runtime (docs/runtime-contract-plan.md §4.3),
// through the file service the editor saves with — atomic, fingerprint-checked, the file's encoding
// kept — and never under the user's unsaved edits.

/** Writes go through here, for both tools: the checks, the write, the refresh. */
internal class WorkspaceWriter(
    private val workspace: AssistantWorkspace,
    private val source: WorkspaceFileSource,
    /** An open tab holds unsaved edits of this location. */
    private val unsavedEdits: (String) -> Boolean,
    /** Files may have changed: the workspace's tree, Git and index refresh as after a terminal's edit. */
    private val changed: () -> Unit,
) {
    /** [path] as a file the assistant may write: not a folder, not under the user's edits. */
    fun target(path: String): File {
        val file = workspace.resolve(path)
        if (file.isDirectory) throw AssistantToolException("${path.trim()} is a folder")
        if (unsavedEdits(file.path)) {
            throw AssistantToolException("The user has unsaved changes in ${path.trim()}: ask them to save or discard first")
        }
        return file
    }

    /** [path] as a file of the workspace, relative to its top; null for one outside it. */
    fun relative(path: String): String? = runCatching { workspace.relative(workspace.resolve(path)) }.getOrNull()

    /** What writing [path] reaches ([AssistantWorkspace.reach]). */
    fun reach(path: String?): List<File> = path?.let(workspace::reach).orEmpty()

    /** The file as it is now; null when there is none. */
    fun read(file: File): WorkspaceDocument? = if (!file.exists()) null else io(file) { source.readDocument(file.path) }

    /** Writes [text] over [current] (read just before), or into a new file; the workspace refreshes. */
    fun write(file: File, current: WorkspaceDocument?, text: String) {
        val document = current ?: io(file) {
            val parent = file.parentFile ?: throw IOException("No folder for ${file.name}")
            if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Unable to make ${workspace.shown(parent)}")
            source.readDocument(source.createFile(parent.path, file.name))
        }
        io(file) { source.writeDocument(document, text) }
        changed()
    }

    private fun <T> io(file: File, block: () -> T): T = try {
        block()
    } catch (failure: IOException) {
        throw AssistantToolException("${workspace.shown(file)}: ${failure.message ?: "unable to write"}")
    }
}

internal class WriteFileTool(private val writer: WorkspaceWriter) : AssistantTool, AssistantFileChanger {
    override val name = "write_file"
    override val description = "Writes a whole file: creates it (and its folders), or " +
        "replaces what it holds. For a small change to an existing file, edit_file is better."
    override val parameters = listOf(
        AssistantParameter("path", "The file"),
        AssistantParameter("content", "Everything the file will hold"),
    )
    override val kind = AssistantToolKind.Change

    override var lastChanges: LineChanges? = null
        private set

    override fun summary(arguments: JSONObject): String = "Write ${arguments.textOrNull("path") ?: "a file"}"

    override fun file(arguments: JSONObject): String? = arguments.textOrNull("path")?.let(writer::relative)

    override fun reach(arguments: JSONObject): List<File> = writer.reach(arguments.textOrNull("path"))

    override fun detail(arguments: JSONObject): String {
        val content = arguments.textOrNull("content").orEmpty()
        return "${summary(arguments)} (${countOf(content.lines().size, "line")})\n\n${bounded(content, DETAIL_CHARACTERS)}"
    }

    override suspend fun run(arguments: JSONObject): String {
        lastChanges = null
        val file = writer.target(arguments.text("path"))
        val content = arguments.text("content")
        val current = writer.read(file)
        writer.write(file, current, content)
        val changes = LineChanges.between(current?.text, content).also { lastChanges = it }
        return "${if (current == null) "Created" else "Replaced"} ${arguments.text("path")}: ${said(changes)}"
    }
}

internal class EditFileTool(private val writer: WorkspaceWriter) : AssistantTool, AssistantFileChanger {
    override val name = "edit_file"
    override val description = "Replaces exact text in a file. old_text must match the file " +
        "exactly, whitespace included, and only once unless replace_all; read the file first."
    override val parameters = listOf(
        AssistantParameter("path", "The file"),
        AssistantParameter("old_text", "The text to replace, exactly as it is in the file"),
        AssistantParameter("new_text", "What replaces it"),
        AssistantParameter("replace_all", "Replace every match, not just one", AssistantParameterType.Boolean, required = false),
    )
    override val kind = AssistantToolKind.Change

    override var lastChanges: LineChanges? = null
        private set

    override fun summary(arguments: JSONObject): String = "Edit ${arguments.textOrNull("path") ?: "a file"}"

    override fun file(arguments: JSONObject): String? = arguments.textOrNull("path")?.let(writer::relative)

    override fun reach(arguments: JSONObject): List<File> = writer.reach(arguments.textOrNull("path"))

    override fun detail(arguments: JSONObject): String = summary(arguments) +
        "\n\n− ${bounded(arguments.optString("old_text"), DETAIL_CHARACTERS / 2)}" +
        "\n\n+ ${bounded(arguments.optString("new_text"), DETAIL_CHARACTERS / 2)}"

    override suspend fun run(arguments: JSONObject): String {
        lastChanges = null
        val path = arguments.text("path")
        val file = writer.target(path)
        val document = writer.read(file) ?: throw AssistantToolException("$path does not exist; write_file creates a file")
        val (oldText, newText) = matching(document.text, arguments.text("old_text"), arguments.text("new_text"))
            ?: throw AssistantToolException("old_text is not in $path; read the file and copy the text exactly")
        val count = occurrences(document.text, oldText)
        val all = arguments.flag("replace_all")
        if (count > 1 && !all) {
            throw AssistantToolException("old_text is in $path $count times; include more lines around it, or set replace_all")
        }
        val edited = if (all) document.text.replace(oldText, newText) else document.text.replaceFirst(oldText, newText)
        writer.write(file, document, edited)
        val changes = LineChanges.between(document.text, edited).also { lastChanges = it }
        return "Edited $path${if (all && count > 1) " in $count places" else ""}: ${said(changes)}"
    }

    /** The texts as the file writes its line endings: a model writes `\n` into a `\r\n` file. */
    private fun matching(text: String, oldText: String, newText: String): Pair<String, String>? {
        if (oldText.isEmpty()) throw AssistantToolException("old_text is empty; write_file writes a whole file")
        if (oldText in text) return oldText to newText
        val crlf = oldText.replace("\r\n", "\n").replace("\n", "\r\n")
        return if ("\r\n" in text && crlf in text) crlf to newText.replace("\r\n", "\n").replace("\n", "\r\n") else null
    }

    private fun occurrences(text: String, part: String): Int {
        var count = 0
        var from = text.indexOf(part)
        while (from >= 0) {
            count++
            from = text.indexOf(part, from + part.length)
        }
        return count
    }
}

/** [changes] as the model reads them: "+12 −3 lines". */
private fun said(changes: LineChanges): String =
    if (changes.added == 0 && changes.removed == 0) "no lines changed" else "${changes.label} lines"

/** How much of a change an approval shows: enough to judge it on a phone. */
private const val DETAIL_CHARACTERS = 4_000
