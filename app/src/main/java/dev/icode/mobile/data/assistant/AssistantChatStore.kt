package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.bounded
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSession
import dev.icode.mobile.domain.AssistantToolKind
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
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
    fun sessions(workspace: String): List<AssistantSession> {
        migrate(workspace)
        return listed(workspace)
    }

    /** [session] of [workspace], every call answered; empty when it is not there or cannot be read. */
    fun load(workspace: String, session: String): AssistantChat {
        migrate(workspace)
        val file = chatFile(workspace, session).takeIf { it.isFile } ?: return AssistantChat()
        return runCatching {
            val json = JSONObject(file.readText())
            AssistantChat(
                messages = AssistantRules.closed(json.getJSONArray("messages").objects().map(::decode)),
                contextTokens = json.optLong("contextTokens", -1).takeIf { it >= 0 },
            )
        }.onFailure { warn("read a chat", it) }.getOrDefault(AssistantChat())
    }

    /**
     * Saves [chat] as [session] of [workspace], updated at [now]: its newest [MAX_MESSAGES] from
     * something the user said, each output bounded. Its title, once given, stays.
     */
    fun save(workspace: String, session: String, chat: AssistantChat, now: Long) {
        migrate(workspace)
        keep(workspace, session, chat, now)
    }

    fun delete(workspace: String, session: String) {
        migrate(workspace)
        chatFile(workspace, session).delete()
        writeIndex(workspace, listed(workspace).filter { it.id != session })
    }

    /** The workspace was renamed or moved: its chats go with it. */
    fun move(from: String, to: String) {
        migrate(from)
        val source = folderOf(from).takeIf { it.isDirectory } ?: return
        val target = folderOf(to)
        // A folder already there belongs to no workspace: one at that location could not be moved onto.
        target.deleteRecursively()
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    /** The workspace was deleted: its chats go with it. */
    fun forget(workspace: String) {
        legacyFile(workspace).delete()
        folderOf(workspace).deleteRecursively()
    }

    /** The one chat per workspace of before (`<sha>.json`) becomes its first session. */
    private fun migrate(workspace: String) {
        val legacy = legacyFile(workspace).takeIf { it.isFile } ?: return
        runCatching {
            val messages = JSONObject(legacy.readText()).getJSONArray("messages").objects().map(::decode)
            if (messages.isNotEmpty()) keep(workspace, UUID.randomUUID().toString(), AssistantChat(messages), legacy.lastModified())
        }.onFailure { warn("read a chat", it) }
        legacy.delete()
    }

    private fun keep(workspace: String, session: String, chat: AssistantChat, now: Long) {
        val kept = chat.messages.takeLast(MAX_MESSAGES).dropWhile { it !is AssistantMessage.User }
        val others = listed(workspace)
        val title = others.firstOrNull { it.id == session }?.title
            ?: AssistantRules.sessionTitle(chat.messages.firstNotNullOfOrNull { it as? AssistantMessage.User }?.text.orEmpty())
        val entry = AssistantSession(session, title, now)
        val json = JSONObject()
            .put("id", entry.id)
            .put("title", entry.title)
            .put("updatedAt", entry.updatedAt)
            .put("workspace", workspace)
            .put("messages", JSONArray().apply { kept.forEach { put(encode(it)) } })
        chat.contextTokens?.let { json.put("contextTokens", it) }
        write(chatFile(workspace, session), json.toString())
        writeIndex(workspace, listOf(entry) + others.filter { it.id != session })
    }

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

    private fun write(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temporary = File.createTempFile(".chat-", ".tmp", target.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    private fun folderOf(workspace: String) = File(folder, Sha256.hex(workspace))
    private fun legacyFile(workspace: String) = File(folder, Sha256.hex(workspace) + ".json")

    /** A session's file; its ID is iCode's own (a UUID), never a path. */
    private fun chatFile(workspace: String, session: String): File {
        require(SESSION_ID.matches(session)) { "Not a chat ID" }
        return File(folderOf(workspace), "$session.json")
    }

    private fun session(json: JSONObject) = AssistantSession(json.getString("id"), json.getString("title"), json.getLong("updatedAt"))

    private fun encode(message: AssistantMessage): JSONObject = when (message) {
        is AssistantMessage.User -> JSONObject().put("role", "user").put("text", message.text).put(
            "attachments",
            JSONArray().apply { message.attachments.forEach { put(JSONObject().put("path", it.path).put("content", it.content)) } },
        )
        is AssistantMessage.Reply -> JSONObject().put("role", "reply").put("text", message.text).put(
            "calls",
            JSONArray().apply {
                message.calls.forEach { call ->
                    put(
                        JSONObject().put("id", call.id).put("tool", call.tool).put("arguments", call.arguments)
                            .put("summary", call.summary).put("kind", call.kind.name),
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
                            .put("output", bounded(result.output, MAX_OUTPUT)).put("failed", result.failed),
                    )
                }
            },
        )
        is AssistantMessage.Compacted -> JSONObject().put("role", "compacted").put("summary", message.summary)
    }

    private fun decode(json: JSONObject): AssistantMessage = when (val role = json.getString("role")) {
        "user" -> AssistantMessage.User(
            json.getString("text"),
            json.optJSONArray("attachments")?.objects().orEmpty().map { AssistantAttachment(it.getString("path"), it.getString("content")) },
        )
        "reply" -> AssistantMessage.Reply(
            json.getString("text"),
            json.getJSONArray("calls").objects().map { call ->
                AssistantCall(
                    id = call.getString("id"),
                    tool = call.getString("tool"),
                    arguments = call.getString("arguments"),
                    summary = call.getString("summary"),
                    kind = AssistantToolKind.valueOf(call.getString("kind")),
                )
            },
        )
        "results" -> AssistantMessage.Results(
            json.getJSONArray("results").objects().map { result ->
                AssistantResult(result.getString("callId"), result.getString("tool"), result.getString("output"), result.getBoolean("failed"))
            },
        )
        "compacted" -> AssistantMessage.Compacted(json.getString("summary"))
        else -> throw IllegalArgumentException("Unknown role $role")
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    /** Its type only: a parser's message can quote the chat, and a chat never reaches the log. */
    private fun warn(what: String, failure: Throwable) = BackendLog.w(TAG, "Unable to $what (${failure.javaClass.simpleName})")

    private companion object {
        const val TAG = "AssistantChatStore"
        const val INDEX = "index.json"
        const val MAX_MESSAGES = 400
        const val MAX_OUTPUT = 16_000
        val SESSION_ID = Regex("^[A-Za-z0-9-]{1,64}$")
    }
}
