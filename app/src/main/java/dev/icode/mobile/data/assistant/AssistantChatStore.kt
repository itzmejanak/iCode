package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantAnswerer
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantQueued
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSession
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/** One chat as kept: what was said, the tokens its last request used (the context meter), and who answers it now. */
internal data class AssistantChat(
    val messages: List<AssistantMessage> = emptyList(),
    val contextTokens: Long? = null,
    val answerer: AssistantAnswerer? = null,
    /** The messages waiting while it works, kept in its entry in `index.json`. */
    val queued: List<AssistantQueued> = emptyList(),
)

/**
 * Each workspace's chats, in iCode's storage: a folder per workspace named by the SHA-256 of its
 * location — a name never has to be made safe for a file — and one for the chats in no workspace
 * (docs/design-and-navigation.md), [NO_WORKSPACE]. Each chat is a file of lines: its ID and title,
 * then every message, whole, one a line — kept in full, however long (docs/assistant-and-mcp.md). A
 * change appends what is new, cutting back only what changed at the end, so a long chat is never
 * written again; a line a killed process left half written is dropped when the chat is read.
 * `index.json`, the list its history shows, is written whole and renamed into place, and is made
 * again from the chats' files when it cannot be read.
 */
internal class AssistantChatStore(private val folder: File) {
    /** A chat's file as this process last read or wrote it: its messages, and where each line ends ([0]: the first, its title's). */
    private class Written(val messages: List<AssistantMessage>, val ends: List<Long>)

    private val lock = Any()
    private val written = HashMap<File, Written>()

    /** [workspace]'s chats, newest first. */
    fun sessions(workspace: String?): List<AssistantSession> = synchronized(lock) { listed(workspace).map { it.session } }

    /** [session] of [workspace], every call answered; empty when it is not there or cannot be read. */
    fun load(workspace: String?, session: String): AssistantChat = synchronized(lock) {
        val listed = listed(workspace).firstOrNull { it.session.id == session }
        val file = chatFile(workspace, session)
        val messages = when {
            file.isFile -> runCatching { read(file, codec(workspace)) }.onFailure { warn("read a chat", it) }.getOrDefault(emptyList())
            else -> legacy(workspace, session)
        }
        AssistantChat(AssistantRules.closed(messages), listed?.tokens, listed?.by, listed?.queued.orEmpty())
    }

    /** Saves [chat] as [session] of [workspace], updated at [now]: every message, in full. Its title, once given, stays. */
    fun save(workspace: String?, session: String, chat: AssistantChat, now: Long) = synchronized(lock) {
        val others = listed(workspace)
        val title = others.firstOrNull { it.session.id == session }?.session?.title
            ?: AssistantRules.sessionTitle(chat.messages.firstNotNullOfOrNull { it as? AssistantMessage.User }?.let(::titled).orEmpty())
        val file = chatFile(workspace, session)
        val codec = codec(workspace)
        val before = written[file]?.takeIf { file.isFile && file.length() == it.ends.last() }
        written[file] = if (before == null) whole(file, workspace, session, title, chat.messages, codec) else tail(file, before, chat.messages, codec)
        legacyFile(workspace, session).delete()
        writeIndex(workspace, listOf(Listed(AssistantSession(session, title, now), chat.contextTokens, chat.answerer, chat.queued)) + others.filter { it.session.id != session })
    }

    /**
     * Keeps [bytes] — an image or a PDF sent with a message — in [session]'s folder, named by its
     * content, and returns where. They go with the chat: deleted with it, moved with its workspace.
     */
    fun keep(workspace: String?, session: String, bytes: ByteArray, extension: String): String {
        val folder = filesOf(workspace, session).apply { mkdirs() }
        val file = File(folder, Sha256.hex(bytes).take(NAME_LENGTH) + "." + extension)
        if (!file.isFile) replace(file, bytes)
        return file.path
    }

    fun delete(workspace: String?, session: String) = synchronized(lock) {
        val file = chatFile(workspace, session)
        written -= file
        file.delete()
        legacyFile(workspace, session).delete()
        filesOf(workspace, session).deleteRecursively()
        writeIndex(workspace, listed(workspace).filter { it.session.id != session })
    }

    /** The workspace was renamed or moved: its chats go with it. */
    fun move(from: String, to: String) = synchronized(lock) {
        val source = folderOf(from).takeIf { it.isDirectory } ?: return@synchronized
        val target = folderOf(to)
        written.clear()
        // A folder already there belongs to no workspace: one at that location could not be moved onto.
        target.deleteRecursively()
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    /** The workspace was deleted: its chats go with it. */
    fun forget(workspace: String) = synchronized(lock) {
        written.clear()
        folderOf(workspace).deleteRecursively()
    }

    /** The whole file written again, through a temporary file renamed into place: a new chat, or one this process has not written. */
    private fun whole(file: File, workspace: String?, session: String, title: String, messages: List<AssistantMessage>, codec: AssistantChatCodec): Written {
        val lines = listOf(JSONObject().put("id", session).put("title", title).put("workspace", workspace)) + messages.map(codec::encode)
        val bytes = lines.map { (it.toString() + "\n").toByteArray(Charsets.UTF_8) }
        val all = ByteArrayOutputStream(bytes.sumOf { it.size }).apply { bytes.forEach(::write) }
        replace(file, all.toByteArray())
        return Written(messages, bytes.runningFold(0L) { end, line -> end + line.size }.drop(1))
    }

    /** What changed at the end: the lines after the messages [before] shares with [messages] cut, and [messages]' own appended. */
    private fun tail(file: File, before: Written, messages: List<AssistantMessage>, codec: AssistantChatCodec): Written {
        val kept = before.messages.zip(messages).takeWhile { (old, new) -> old == new }.size
        val ends = before.ends.take(kept + 1).toMutableList()
        RandomAccessFile(file, "rw").use { output ->
            output.setLength(ends.last())
            output.seek(ends.last())
            messages.drop(kept).forEach { message ->
                output.write((codec.encode(message).toString() + "\n").toByteArray(Charsets.UTF_8))
                ends += output.filePointer
            }
            output.fd.sync()
        }
        return Written(messages, ends)
    }

    /** A chat file's messages; its last line, half written by a killed process, dropped. Remembers what it read. */
    private fun read(file: File, codec: AssistantChatCodec): List<AssistantMessage> {
        val bytes = file.readBytes()
        val messages = mutableListOf<AssistantMessage>()
        val ends = mutableListOf<Long>()
        var start = 0
        var whole = true
        while (start < bytes.size) {
            var newline = start
            while (newline < bytes.size && bytes[newline] != NEWLINE) newline++
            if (newline == bytes.size) break
            val line = String(bytes, start, newline - start, Charsets.UTF_8)
            if (ends.isNotEmpty()) {
                val message = runCatching { codec.decode(JSONObject(line)) }.onFailure { warn("read a chat", it) }.getOrNull()
                if (message == null) {
                    whole = false
                    break
                }
                messages += message
            }
            start = newline + 1
            ends += start.toLong()
        }
        // A line cut short, or one that cannot be read, is written over by the next save: the file is written again whole.
        if (whole && start.toLong() == bytes.size.toLong() && ends.isNotEmpty()) written[file] = Written(messages, ends) else written -= file
        return messages
    }

    /** A chat as an earlier build kept it — one JSON object, written whole — read until it is next saved in lines. */
    private fun legacy(workspace: String?, session: String): List<AssistantMessage> {
        val file = legacyFile(workspace, session).takeIf { it.isFile } ?: return emptyList()
        val codec = codec(workspace)
        return runCatching { JSONObject(file.readText()).getJSONArray("messages").objects().map(codec::decode) }
            .onFailure { warn("read a chat", it) }.getOrDefault(emptyList())
    }

    /** What a chat is named by: its first message's text, or the files it sent alone. */
    private fun titled(first: AssistantMessage.User): String = first.text.ifBlank { first.attachments.joinToString(", ") { it.name } }

    /** A chat on the list, with the tokens of its last request and who answers it now. */
    private class Listed(val session: AssistantSession, val tokens: Long?, val by: AssistantAnswerer? = null, val queued: List<AssistantQueued> = emptyList())

    /** The list as saved, newest first; made again from the chats' files when it cannot be read. */
    private fun listed(workspace: String?): List<Listed> {
        val index = File(folderOf(workspace), INDEX).takeIf { it.isFile } ?: return rebuilt(workspace)
        return runCatching {
            JSONArray(index.readText()).objects().map {
                Listed(
                    session(it), it.optLong("contextTokens", -1).takeIf { tokens -> tokens >= 0 }, AssistantChatCodec.answerer(it.optJSONObject("by")),
                    it.optJSONArray("queued")?.objects().orEmpty().map(AssistantChatCodec::queued),
                )
            }
        }.onFailure { warn("read a chat list", it) }.getOrElse { rebuilt(workspace) }.sortedByDescending { it.session.updatedAt }
    }

    /** The list made again from the chats' own files — their first lines, or an earlier build's whole files — and saved. */
    private fun rebuilt(workspace: String?): List<Listed> {
        val files = folderOf(workspace).listFiles { file -> file.isFile && file.name != INDEX }.orEmpty()
        val sessions = files.filter { it.name.endsWith(LINES) }.mapNotNull { file ->
            runCatching { file.useLines { it.first() } }.mapCatching { JSONObject(it) }
                .map { Listed(AssistantSession(it.getString("id"), it.getString("title"), file.lastModified()), null) }
                .onFailure { warn("read a chat", it) }.getOrNull()
        }
        val earlier = files.filter { it.name.endsWith(LEGACY) && files.none { lines -> lines.name == it.name.removeSuffix(LEGACY) + LINES } }.mapNotNull { file ->
            runCatching { Listed(session(JSONObject(file.readText())), null) }.onFailure { warn("read a chat", it) }.getOrNull()
        }
        val all = (sessions + earlier).sortedByDescending { it.session.updatedAt }
        if (all.isNotEmpty()) runCatching { writeIndex(workspace, all) }.onFailure { warn("save a chat list", it) }
        return all
    }

    private fun writeIndex(workspace: String?, sessions: List<Listed>) {
        val json = JSONArray().apply {
            sessions.forEach { listed ->
                put(
                    JSONObject().put("id", listed.session.id).put("title", listed.session.title).put("updatedAt", listed.session.updatedAt)
                        .apply { listed.tokens?.let { put("contextTokens", it) } }
                        .apply { listed.by?.let { put("by", AssistantChatCodec.answerer(it)) } }
                        .apply { if (listed.queued.isNotEmpty()) put("queued", JSONArray(listed.queued.map(AssistantChatCodec::queued))) },
                )
            }
        }
        replace(File(folderOf(workspace), INDEX), json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun replace(target: File, bytes: ByteArray) {
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

    private fun codec(workspace: String?) = AssistantChatCodec(folderOf(workspace))

    /** [workspace]'s chats' folder: named by its location's SHA-256, or [NO_WORKSPACE] for the chats in none. */
    private fun folderOf(workspace: String?) = File(folder, workspace?.let(Sha256::hex) ?: NO_WORKSPACE)

    /** Where [session]'s images and PDFs are kept. */
    private fun filesOf(workspace: String?, session: String): File = File(folderOf(workspace), checked(session) + ".files")

    /** A session's file; its ID is iCode's own (a UUID), never a path. */
    private fun chatFile(workspace: String?, session: String): File = File(folderOf(workspace), checked(session) + LINES)

    /** The file an earlier build kept [session] in, whole. */
    private fun legacyFile(workspace: String?, session: String): File = File(folderOf(workspace), checked(session) + LEGACY)

    private fun checked(session: String): String = session.also { require(SESSION_ID.matches(it)) { "Not a chat ID" } }

    private fun session(json: JSONObject) = AssistantSession(json.getString("id"), json.getString("title"), json.getLong("updatedAt"))

    /** Its type only: a parser's message can quote the chat, and a chat never reaches the log. */
    private fun warn(what: String, failure: Throwable) = BackendLog.w(TAG, "Unable to $what (${failure.javaClass.simpleName})")

    private companion object {
        const val TAG = "AssistantChatStore"
        const val INDEX = "index.json"
        const val LINES = ".jsonl"
        const val LEGACY = ".json"
        const val NAME_LENGTH = 24
        const val NEWLINE = '\n'.code.toByte()
        val SESSION_ID = Regex("^[A-Za-z0-9-]{1,64}$")
        /** The chats in no workspace's folder: no SHA-256's hex is ever this. */
        const val NO_WORKSPACE = "no-workspace"
    }
}
