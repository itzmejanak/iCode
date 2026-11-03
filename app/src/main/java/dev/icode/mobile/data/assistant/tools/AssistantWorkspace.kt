package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.runtime.GuestPaths
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.domain.AssistantPermissions
import java.io.File
import java.io.IOException
import org.json.JSONObject

/**
 * The place a turn works in, and the runtime its tools reach (docs/runtime-contract-plan.md §4.3): a
 * workspace. A path the
 * model writes is a path in the runtime — absolute as written, `~` as root's home, relative from this
 * place's top — and names the file the runtime would, its links followed as PRoot follows them
 * ([GuestPaths]). Phone storage is in the runtime too (L8), read with All Files Access.
 */
internal class AssistantWorkspace(
    /** Its location in iCode's storage. */
    root: File,
    private val paths: GuestPaths,
    /** All Files Access, as Android answers it now: phone storage is read with it. */
    private val storageAccess: () -> Boolean = { true },
) {
    val root: File = root.canonicalFile

    /** Its path in the runtime — `/workspace/<name>`: the path the model is told. */
    val guestPath: String = requireNotNull(paths.guestOf(this.root.path)) { "${this.root} is not in the runtime" }

    /** "app". */
    val name: String get() = root.name

    /** What the processes it starts belong to: the workspace's location. */
    val place: String get() = root.path

    /** [path] as the model wrote it, as the file on the phone it names; it need not exist. */
    fun resolve(path: String): File {
        val written = path.trim().ifEmpty { "." }
        val guest = when {
            written.startsWith('/') -> written
            written == "~" || written.startsWith("~/") -> RuntimeLayout.GUEST_HOME + written.removePrefix("~")
            else -> "$guestPath/$written"
        }
        val file = try {
            paths.hostOf(guest)
        } catch (failure: IOException) {
            throw AssistantToolException(failure.message ?: "$written cannot be followed")
        }
        if (paths.inShared(file) && !storageAccess()) throw AssistantToolException(NO_STORAGE_ACCESS)
        return file
    }

    /** [file]'s path from the workspace's top, `src/App.kt` or `.`; null when it is not in this workspace. */
    fun relative(file: File): String? = WorkspacePaths.relative(file.path, root.path)?.ifEmpty { "." }

    /** [file]'s path in the runtime. */
    fun guestOf(file: File): String = paths.guestOf(file.path) ?: file.path

    /** [file] as the model is told it, and can write it back: from the workspace's top inside it, its runtime path elsewhere. */
    fun shown(file: File): String = relative(file) ?: guestOf(file)

    /** What [path], as the model wrote it, reaches ([AssistantTool.reach]): the workspace's top when absent. */
    fun reach(path: String?): List<File> = listOfNotNull(runCatching { resolve(path ?: ".") }.getOrNull())

    /** The runtime paths of [files] outside this workspace (docs/assistant-permissions-plan.md §4.2). */
    fun outside(files: List<File>): List<String> = files.filter { relative(it) == null }.map(::guestOf).distinct()

    /** The folder "Allow in this chat" opens for [path], a runtime path outside the workspace (§4.4). */
    fun rootOf(path: String): String? = AssistantPermissions.rootOf(
        path,
        isFolder = { runCatching { paths.hostOf(it).isDirectory }.getOrDefault(false) },
        isRepository = { runCatching { File(paths.hostOf(it), ".git").exists() }.getOrDefault(false) },
    )

    /** [path], resolved, as a file that exists and is not a folder; said as the system says it when it is not. */
    fun existingFile(path: String): File = resolve(path).also { file ->
        when {
            !file.exists() -> throw AssistantToolException("${path.trim()} does not exist")
            file.isDirectory -> throw AssistantToolException("${path.trim()} is a folder")
            !file.canRead() -> throw AssistantToolException("${path.trim()}: Permission denied")
        }
    }

    /** [path], resolved, as a folder that exists; said as the system says it when it is not. */
    fun existingFolder(path: String): File = resolve(path).also { folder ->
        when {
            !folder.exists() -> throw AssistantToolException("${path.trim()} does not exist")
            !folder.isDirectory -> throw AssistantToolException("${path.trim()} is not a folder")
            !folder.canRead() -> throw AssistantToolException("${path.trim()}: Permission denied")
        }
    }

    companion object {
        /** What the model is told of phone storage without All Files Access: what the user can do. */
        const val NO_STORAGE_ACCESS =
            "Phone storage needs All Files Access, which iCode does not have. Ask the user to turn it on in " +
                "iCode's Settings › Storage › All Files Access, then try again."
    }
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
