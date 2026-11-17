package dev.icode.mobile.data.assistant

import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.LineChanges
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * A chat's messages as JSON, one object each — the one reader and writer of the chat files'
 * messages ([AssistantChatStore]). An attachment's file is kept by its place in [folder], the chat's
 * workspace's folder, which a rename moves whole.
 */
internal class AssistantChatCodec(private val folder: File) {
    fun encode(message: AssistantMessage): JSONObject = when (message) {
        is AssistantMessage.User -> JSONObject().put("role", "user").put("text", message.text).put(
            "attachments",
            JSONArray().apply { message.attachments.forEach { put(attachment(it)) } },
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
                            .put("output", result.output).put("failed", result.failed).put("printed", result.printed)
                            .apply { result.changes?.let { put("added", it.added).put("removed", it.removed) } },
                    )
                }
            },
        )
        is AssistantMessage.Compacted -> JSONObject().put("role", "compacted").put("summary", message.summary)
    }

    fun decode(json: JSONObject): AssistantMessage = when (val role = json.getString("role")) {
        "user" -> AssistantMessage.User(json.getString("text"), json.optJSONArray("attachments")?.objects().orEmpty().map(::attachment))
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

    private fun attachment(attachment: AssistantAttachment): JSONObject =
        JSONObject().put("name", attachment.name).put("kind", attachment.kind.name).put("mimeType", attachment.mimeType).apply {
            if (attachment.inWorkspace) put("inWorkspace", true)
            if (attachment.kind == AssistantAttachmentKind.Text) put("text", attachment.text)
            attachment.file?.let { put("file", File(it).relativeTo(folder).path) }
        }

    private fun attachment(json: JSONObject): AssistantAttachment =
        AssistantAttachment(
            name = json.getString("name"),
            kind = AssistantAttachmentKind.valueOf(json.getString("kind")),
            text = json.optString("text"),
            file = json.optString("file").ifEmpty { null }?.let { File(folder, it).path },
            mimeType = json.optString("mimeType", "text/plain"),
            inWorkspace = json.optBoolean("inWorkspace"),
        )
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
