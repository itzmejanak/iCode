package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantToolException
import java.io.File
import org.json.JSONObject

/**
 * The workspace a turn works in, as its tools see it: every path the model writes is resolved here,
 * and none may leave it — not by `..`, an absolute path or a link (§4.3).
 */
internal class AssistantWorkspace(
    /** Its location in iCode's storage. */
    root: File,
    /** Its path in the runtime, `/workspace/<name>`: the path the model is told. */
    val guestPath: String,
) {
    val root: File = root.canonicalFile
    val name: String get() = root.name

    /** [path] as the model wrote it — relative, or under [guestPath] — as a file in this workspace. */
    fun resolve(path: String): File {
        val written = path.trim().ifEmpty { "." }
        val relative = when {
            written == guestPath -> "."
            written.startsWith("$guestPath/") -> written.removePrefix("$guestPath/")
            written.startsWith("/") || written.startsWith("~") ->
                throw AssistantToolException("$written is outside this workspace: paths are relative to $guestPath")
            else -> written
        }
        // Canonical: `..` and links are followed before the check, so neither leads out.
        val file = File(root, relative).canonicalFile
        if (file != root && !file.path.startsWith(root.path + File.separator)) {
            throw AssistantToolException("$written is outside this workspace")
        }
        return file
    }

    /** [file]'s path from the workspace's top, as the model writes it: `src/App.kt`, or `.`. */
    fun relative(file: File): String = file.relativeTo(root).path.ifEmpty { "." }

    /** [file] is inside a `.git` folder: Git's own, never written by the assistant. */
    fun isGitInternal(file: File): Boolean = relative(file).split(File.separatorChar).contains(".git")
}

/** A required text argument; the model is told which one it left out. */
internal fun JSONObject.text(name: String): String {
    if (!has(name) || isNull(name)) throw AssistantToolException("Missing the “$name” argument")
    return optString(name)
}

/** An optional text argument; null when absent or blank. */
internal fun JSONObject.textOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

internal fun JSONObject.intOr(name: String, default: Int): Int =
    if (!has(name) || isNull(name)) default else optInt(name, default)

internal fun JSONObject.flag(name: String): Boolean = has(name) && !isNull(name) && optBoolean(name, false)

/**
 * [text] kept within [limit] characters for the model: its start and its end, which is where a
 * build's or a test run's verdict is, with what was left out said between them.
 */
internal fun bounded(text: String, limit: Int): String {
    if (text.length <= limit) return text
    val head = text.take(limit / 2)
    val tail = text.takeLast(limit / 2)
    return "$head\n… (${text.length - head.length - tail.length} characters not shown) …\n$tail"
}
