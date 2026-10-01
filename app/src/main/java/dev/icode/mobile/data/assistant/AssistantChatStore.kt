package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.bounded
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantToolKind
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/**
 * One chat per workspace, in iCode's storage (§4.6): `<sha256 of its location>.json`, so a name never
 * has to be made safe for a file. It is written whole and renamed into place, so a killed process
 * leaves the last chat saved, never half of one.
 */
internal class AssistantChatStore(private val folder: File) {
    /** [workspace]'s chat, every call answered; empty when it has none or it cannot be read. */
    fun load(workspace: String): List<AssistantMessage> {
        val file = fileOf(workspace).takeIf { it.isFile } ?: return emptyList()
        return runCatching {
            val array = JSONObject(file.readText()).getJSONArray("messages")
            AssistantRules.closed((0 until array.length()).map { decode(array.getJSONObject(it)) })
        }.onFailure {
            // Its type only: a parser's message can quote the chat, and a chat never reaches the log.
            BackendLog.w(TAG, "Unable to read a chat (${it.javaClass.simpleName}); it starts again")
        }.getOrDefault(emptyList())
    }

    /** Saves [messages] as [workspace]'s chat: the newest [MAX_MESSAGES], each output bounded. */
    fun save(workspace: String, messages: List<AssistantMessage>) {
        val kept = messages.takeLast(MAX_MESSAGES).dropWhile { it !is AssistantMessage.User }
        val json = JSONObject()
            .put("workspace", workspace)
            .put("messages", JSONArray().apply { kept.forEach { put(encode(it)) } })
        folder.mkdirs()
        val target = fileOf(workspace)
        val temporary = File.createTempFile(".chat-", ".tmp", folder)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(json.toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    /** The workspace was renamed or moved: its chat goes with it. */
    fun move(from: String, to: String) {
        val chat = load(from)
        if (chat.isEmpty()) return
        save(to, chat)
        forget(from)
    }

    fun forget(workspace: String) {
        fileOf(workspace).delete()
    }

    private fun fileOf(workspace: String) = File(folder, Sha256.hex(workspace) + ".json")

    private fun encode(message: AssistantMessage): JSONObject = when (message) {
        is AssistantMessage.User -> JSONObject().put("role", "user").put("text", message.text)
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
    }

    private fun decode(json: JSONObject): AssistantMessage = when (val role = json.getString("role")) {
        "user" -> AssistantMessage.User(json.getString("text"))
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
        else -> throw IllegalArgumentException("Unknown role $role")
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private companion object {
        const val TAG = "AssistantChatStore"
        const val MAX_MESSAGES = 400
        const val MAX_OUTPUT = 16_000
    }
}
