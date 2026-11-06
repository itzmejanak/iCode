package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.bounded
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSession
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.LineChanges
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/** One chat as kept: what was said, and the tokens its last request used (the context meter). */
internal data class AssistantChat(val messages: List<AssistantMessage> = emptyList(), val contextTokens: Long? = null)

/**
 * Each workspace's chats, in iCode's storage (§4.6, §12 R5): a folder per workspace named by the
 * sha256 of its location — a name never has to be made safe for a file — holding one file per chat
 * and `index.json`, the list its history shows. Each chat file also names its own title and time, so
 * an index that cannot be read is made again from them. Every file is written whole and renamed into
 * place: a killed process leaves the last state saved, never half of one.
 */
internal class AssistantChatStore(private val folder: File) {
    /** [workspace]'s chats, newest first. */
    fun sessions(workspace: String): List<AssistantSession> = listed(workspace)

    /** [session] of [workspace], every call answered; empty when it is not there or cannot be read. */
    fun load(workspace: String, session: String): AssistantChat {
        val file = chatFile(workspace, session).takeIf { it.isFile } ?: return AssistantChat()
        return runCatching {
            val json = JSONObject(file.readText())
            AssistantChat(
                messages = AssistantRules.closed(json.getJSONArray("messages").objects().map { decode(workspace, it) }),
                contextTokens = json.optLong("contextTokens", -1).takeIf { it >= 0 },
            )
        }.onFailure { warn("read a chat", it) }.getOrDefault(AssistantChat())
    }

    /**
     * Saves [chat] as [session] of [workspace], updated at [now]: its newest [MAX_MESSAGES] from
     * something the user said, each output bounded. Its title, once given, stays.
     */
    fun save(workspace: String, session: String, chat: AssistantChat, now: Long) {
        val kept = chat.messages.takeLast(MAX_MESSAGES).dropWhile { it !is AssistantMessage.User }
        val others = listed(workspace)
        val title = others.firstOrNull { it.id == session }?.title
            ?: AssistantRules.sessionTitle(chat.messages.firstNotNullOfOrNull { it as? AssistantMessage.User }?.let(::titled).orEmpty())
        val entry = AssistantSession(session, title, now)
        val json = JSONObject()
            .put("id", entry.id)
            .put("title", entry.title)
            .put("updatedAt", entry.updatedAt)
            .put("workspace", workspace)
            .put("messages", JSONArray().apply { kept.forEach { put(encode(workspace, it)) } })
        chat.contextTokens?.let { json.put("contextTokens", it) }
        write(chatFile(workspace, session), json.toString())
        writeIndex(workspace, listOf(entry) + others.filter { it.id != session })
    }

    /**
     * Keeps [bytes] — an image or a PDF sent with a message — in [session]'s folder, named by its
     * content, and returns where. They go with the chat: deleted with it, moved with its workspace.
     */
    fun keep(workspace: String, session: String, bytes: ByteArray, extension: String): String {
        val folder = filesOf(workspace, session).apply { mkdirs() }
        val file = File(folder, Sha256.hex(bytes).take(NAME_LENGTH) + "." + extension)
        if (!file.isFile) write(file, bytes)
        return file.path
    }

    fun delete(workspace: String, session: String) {
        chatFile(workspace, session).delete()
        filesOf(workspace, session).deleteRecursively()
        writeIndex(workspace, listed(workspace).filter { it.id != session })
    }

    /** The workspace was renamed or moved: its chats go with it. */
    fun move(from: String, to: String) {
        val source = folderOf(from).takeIf { it.isDirectory } ?: return
        val target = folderOf(to)
        // A folder already there belongs to no workspace: one at that location could not be moved onto.
        target.deleteRecursively()
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    /** The workspace was deleted: its chats go with it. */
    fun forget(workspace: String) {
        folderOf(workspace).deleteRecursively()
    }

    /** What a chat is named by: its first message's text, or the files it sent alone. */
    private fun titled(first: AssistantMessage.User): String = first.text.ifBlank { first.attachments.joinToString(", ") { it.name } }

    /** The list as saved, newest first; made again from the chats' files when it cannot be read. */
    private fun listed(workspace: String): List<AssistantSession> {
        val index = File(folderOf(workspace), INDEX).takeIf { it.isFile } ?: return rebuilt(workspace)
        return runCatching { JSONArray(index.readText()).objects().map(::session) }
            .onFailure { warn("read a chat list", it) }
            .getOrElse { rebuilt(workspace) }
            .sortedByDescending { it.updatedAt }
    }

    /** The list made again from the chats' own files, and saved. */
    private fun rebuilt(workspace: String): List<AssistantSession> {
        val chats = folderOf(workspace).listFiles { file -> file.name.endsWith(".json") && file.name != INDEX }.orEmpty()
        val sessions = chats.mapNotNull { file ->
            runCatching { session(JSONObject(file.readText())) }.onFailure { warn("read a chat", it) }.getOrNull()
        }.sortedByDescending { it.updatedAt }
        if (sessions.isNotEmpty()) runCatching { writeIndex(workspace, sessions) }.onFailure { warn("save a chat list", it) }
        return sessions
    }

    private fun writeIndex(workspace: String, sessions: List<AssistantSession>) {
        val json = JSONArray().apply {
            sessions.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("updatedAt", it.updatedAt)) }
        }
        write(File(folderOf(workspace), INDEX), json.toString())
    }

    private fun write(target: File, text: String) = write(target, text.toByteArray(Charsets.UTF_8))

    private fun write(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temporary = File.createTempFile(".chat-", ".tmp", target.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    private fun folderOf(workspace: String) = File(folder, Sha256.hex(workspace))

    /** Where [session]'s images and PDFs are kept. */
    private fun filesOf(workspace: String, session: String): File {
        require(SESSION_ID.matches(session)) { "Not a chat ID" }
        return File(folderOf(workspace), "$session.files")
    }

    /** A session's file; its ID is iCode's own (a UUID), never a path. */
    private fun chatFile(workspace: String, session: String): File {
        require(SESSION_ID.matches(session)) { "Not a chat ID" }
        return File(folderOf(workspace), "$session.json")
    }

    private fun session(json: JSONObject) = AssistantSession(json.getString("id"), json.getString("title"), json.getLong("updatedAt"))

    private fun encode(workspace: String, message: AssistantMessage): JSONObject = when (message) {
        is AssistantMessage.User -> JSONObject().put("role", "user").put("text", message.text).put(
            "attachments",
            JSONArray().apply {
                message.attachments.forEach { attachment ->
                    put(
                        JSONObject().put("name", attachment.name).put("kind", attachment.kind.name).put("mimeType", attachment.mimeType).apply {
                            if (attachment.inWorkspace) put("inWorkspace", true)
                            if (attachment.kind == AssistantAttachmentKind.Text) put("text", attachment.text)
                            // Kept by its place in the workspace's folder, which a rename moves whole.
                            attachment.file?.let { put("file", File(it).relativeTo(folderOf(workspace)).path) }
                        },
                    )
                }
            },
        )
        is AssistantMessage.Reply -> JSONObject().put("role", "reply").put("text", message.text).apply {
            if (message.reasoning.isNotBlank()) put("reasoning", message.reasoning)
        }.put(
            "calls",
            JSONArray().apply {
                message.calls.forEach { call ->
                    put(
                        JSONObject().put("id", call.id).put("tool", call.tool).put("arguments", call.arguments)
                            .put("summary", call.summary).put("kind", call.kind.name)
                            .apply { call.file?.let { put("file", it) } }
                            .apply { call.command?.let { put("command", it) } },
                    )
                }
            },
        )
        is AssistantMessage.Results -> JSONObject().put("role", "results").put(
            "results",
            JSONArray().apply {
                message.results.forEach { result ->
                    put(
                        JSONObject().put("callId", result.callId).put("tool", result.tool)
                            .put("output", bounded(result.output, MAX_OUTPUT)).put("failed", result.failed).put("printed", result.printed)
                            .apply { result.changes?.let { put("added", it.added).put("removed", it.removed) } },
                    )
                }
            },
        )
        is AssistantMessage.Compacted -> JSONObject().put("role", "compacted").put("summary", message.summary)
    }

    private fun decode(workspace: String, json: JSONObject): AssistantMessage = when (val role = json.getString("role")) {
        "user" -> AssistantMessage.User(json.getString("text"), json.optJSONArray("attachments")?.objects().orEmpty().map { attachment(workspace, it) })
        "reply" -> AssistantMessage.Reply(
            json.getString("text"),
            json.getJSONArray("calls").objects().map { call ->
                AssistantCall(
                    id = call.getString("id"),
                    tool = call.getString("tool"),
                    arguments = call.getString("arguments"),
                    summary = call.getString("summary"),
                    kind = AssistantToolKind.valueOf(call.getString("kind")),
                    file = call.optString("file").ifEmpty { null },
                    command = call.optString("command").ifEmpty { null },
                )
            },
            json.optString("reasoning"),
        )
        "results" -> AssistantMessage.Results(
            json.getJSONArray("results").objects().map { result ->
                AssistantResult(
                    result.getString("callId"), result.getString("tool"), result.getString("output"), result.getBoolean("failed"),
                    changes = if (result.has("added")) LineChanges(result.getInt("added"), result.getInt("removed")) else null,
                    printed = result.optString("printed").takeIf { result.has("printed") && !result.isNull("printed") },
                )
            },
        )
        "compacted" -> AssistantMessage.Compacted(json.getString("summary"))
        else -> throw IllegalArgumentException("Unknown role $role")
    }

    /** An attachment as kept: its file, where it has one, relative to the workspace's folder. */
    private fun attachment(workspace: String, json: JSONObject): AssistantAttachment =
        AssistantAttachment(
            name = json.getString("name"),
            kind = AssistantAttachmentKind.valueOf(json.getString("kind")),
            text = json.optString("text"),
            file = json.optString("file").ifEmpty { null }?.let { File(folderOf(workspace), it).path },
            mimeType = json.optString("mimeType", "text/plain"),
            inWorkspace = json.optBoolean("inWorkspace"),
        )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    /** Its type only: a parser's message can quote the chat, and a chat never reaches the log. */
    private fun warn(what: String, failure: Throwable) = BackendLog.w(TAG, "Unable to $what (${failure.javaClass.simpleName})")

    private companion object {
        const val TAG = "AssistantChatStore"
        const val INDEX = "index.json"
        const val MAX_MESSAGES = 400
        /** Above every command's output as its tool bounds it: the page shows what the model read. */
        const val MAX_OUTPUT = 48_000
        const val NAME_LENGTH = 24
        val SESSION_ID = Regex("^[A-Za-z0-9-]{1,64}$")
    }
}
