package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.model.CompleteElicitationNotification
import com.agentclientprotocol.model.CreateElicitationRequest
import com.agentclientprotocol.model.CreateElicitationResponse
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement

/** A chat of the assistant: its workspace (null for none) and iCode's ID for it. */
internal data class ChatKey(val workspace: String?, val session: String)

/**
 * What iCode answers for one session of an agent (docs/assistant-and-mcp.md): its updates, its approvals
 * and its questions, for the chat [chat] — which a workspace's rename moves. Updates come two ways, the
 * prompt's own while a turn runs and these between; both go to the [transcript] in hand, one at a time,
 * and only settings, commands, plans and usage count when none is.
 */
@OptIn(UnstableApi::class)
internal class AcpOperations(
    val entry: AgentEntry,
    @Volatile var chat: ChatKey,
    /** Where the chat works in the runtime. */
    val cwd: String,
    private val owner: Owner,
) : ClientSessionOperations {
    /** What answers for the session: its owner, who has the page and the user. */
    interface Owner {
        /** [call] asks with [options]: the ID of the one chosen, null when nobody answered. */
        suspend fun approve(operations: AcpOperations, call: AssistantCall, options: List<PermissionOption>): String?
        suspend fun ask(operations: AcpOperations, request: CreateElicitationRequest): CreateElicitationResponse

        /** An update with no turn and no reading back in hand: settings, commands, a plan, usage. */
        fun between(operations: AcpOperations, update: SessionUpdate)
    }

    private val lock = Any()
    private var transcript: AcpTranscript? = null
    @Volatile private var last = System.nanoTime()

    /** Updates go to [into] from now on; none when null. */
    fun attach(into: AcpTranscript?) = synchronized(lock) { transcript = into }

    fun deliver(update: SessionUpdate) {
        val between = synchronized(lock) {
            last = System.nanoTime()
            transcript?.update(update)
            transcript == null
        }
        if (between) owner.between(this, update)
    }

    /** [transcript] finished, under the same lock its updates take. */
    fun finish(transcript: AcpTranscript) = synchronized(lock) { transcript.finish() }

    /**
     * Until the updates the agent sent have all been read: the SDK hands a response over before the
     * updates sent ahead of it are, so a turn's end, a reading back and an approval wait for a quiet
     * moment first — at most [SETTLE_MAX_MILLIS].
     */
    suspend fun settle() {
        val began = System.nanoTime()
        while ((System.nanoTime() - last) / MILLION < QUIET_MILLIS && (System.nanoTime() - began) / MILLION < SETTLE_MAX_MILLIS) delay(STEP_MILLIS)
    }

    override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) = deliver(notification)

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse {
        settle()
        // The call as the chat has it: recorded now, if its own update has not come yet.
        val call = synchronized(lock) {
            transcript?.let { it.update(toolCall); it.recorded(toolCall.toolCallId.value) }
        } ?: AcpMapping.call(AcpToolCall(toolCall.toolCallId.value).apply { merge(toolCall.title, toolCall.kind, null, toolCall.content, toolCall.locations?.map { it.path }, toolCall.rawInput, null) }, cwd)
        val chosen = owner.approve(this, call, permissions)
        return RequestPermissionResponse(chosen?.let { RequestPermissionOutcome.Selected(PermissionOptionId(it)) } ?: RequestPermissionOutcome.Cancelled)
    }

    override suspend fun createElicitation(request: CreateElicitationRequest): CreateElicitationResponse {
        settle()
        return owner.ask(this, request)
    }

    override suspend fun completeElicitation(notification: CompleteElicitationNotification) = Unit

    private companion object {
        const val QUIET_MILLIS = 120L
        const val SETTLE_MAX_MILLIS = 1_500L
        const val STEP_MILLIS = 30L
        const val MILLION = 1_000_000L
    }
}

/** A chat read back from its agent (ACP `session/load`): its messages, as the chat keeps them. */
internal class AcpReplay : AcpTranscript.Sink {
    val messages = mutableListOf<AssistantMessage>()

    override fun replied(reply: AssistantMessage.Reply) {
        messages += reply
    }

    override fun extended(call: AssistantCall) {
        val at = messages.indexOfLast { it is AssistantMessage.Reply }
        if (at < 0) return replied(AssistantMessage.Reply("", listOf(call)))
        val reply = messages[at] as AssistantMessage.Reply
        messages[at] = reply.copy(calls = reply.calls + call)
    }

    override fun answered(result: AssistantResult) {
        val last = messages.lastOrNull()
        if (last is AssistantMessage.Results) messages[messages.lastIndex] = AssistantMessage.Results(last.results + result)
        else messages += AssistantMessage.Results(listOf(result))
    }

    override fun said(message: AssistantMessage.User) {
        messages += message
    }
}
