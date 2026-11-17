package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.AvailableCommand
import com.agentclientprotocol.model.AvailableCommandInput
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallLocation
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.model.ToolKind
import dev.icode.mobile.domain.AgentCommand
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantToolName
import dev.icode.mobile.domain.LineChanges
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an agent's updates make of its chat (docs/assistant-and-mcp.md): one result right after each reply. */
@OptIn(UnstableApi::class)
class AcpTranscriptTest {
    private val replay = AcpReplay()
    private val commands = mutableListOf<AgentCommand>()
    private val transcript = AcpTranscript("/workspace/app", object : AcpTranscript.Sink by replay {
        override fun commands(commands: List<AgentCommand>) {
            this@AcpTranscriptTest.commands += commands
        }
    })

    private fun said(text: String) = transcript.update(SessionUpdate.AgentMessageChunk(ContentBlock.Text(text)))
    private fun call(id: String, kind: ToolKind, title: String = id, path: String? = null) =
        transcript.update(SessionUpdate.ToolCall(ToolCallId(id), title, kind, ToolCallStatus.PENDING, locations = listOfNotNull(path?.let(::ToolCallLocation))))
    private fun done(id: String, vararg content: ToolCallContent) =
        transcript.update(SessionUpdate.ToolCallUpdate(ToolCallId(id), status = ToolCallStatus.COMPLETED, content = content.toList()))

    @Test fun wordsThenCallsThenWordsAreRepliesWithTheirResults() {
        transcript.update(SessionUpdate.UserMessageChunk(ContentBlock.Text("Fix it")))
        said("Let me ")
        said("look.")
        call("a", ToolKind.READ, "Read app.kt")
        done("a", ToolCallContent.Content(ContentBlock.Text("fun a() = 1")))
        said("Found it.")
        call("b", ToolKind.EDIT, "Edit app.kt", "/workspace/app/app.kt")
        done("b", ToolCallContent.Diff("/workspace/app/app.kt", "fun a() = 2\n", "fun a() = 1\n"))
        said("Fixed.")
        transcript.finish()

        val messages = replay.messages
        assertEquals(AssistantMessage.User("Fix it"), messages[0])
        assertEquals("Let me look.", (messages[1] as AssistantMessage.Reply).text)
        assertEquals("fun a() = 1", (messages[2] as AssistantMessage.Results).results.single().output)
        val edit = (messages[3] as AssistantMessage.Reply).calls.single()
        assertEquals("Found it.", (messages[3] as AssistantMessage.Reply).text)
        assertEquals(AssistantToolName.EditFile.id, edit.tool)
        assertEquals(AssistantToolKind.Change, edit.kind)
        assertEquals("app.kt", edit.file)
        assertEquals(LineChanges(1, 1), (messages[4] as AssistantMessage.Results).results.single().changes)
        assertEquals(AssistantMessage.Reply("Fixed.", emptyList(), ""), messages[5])
        assertEquals(messages, AssistantRules.closed(messages))
    }

    @Test fun aCallStartedBeforeTheLastOnesResultJoinsItsReply() {
        said("Two at once.")
        call("a", ToolKind.SEARCH)
        call("b", ToolKind.EXECUTE)
        said("While they run.") // Waits for both: a reply now would come between the calls and their results.
        done("b")
        done("a")
        transcript.finish()

        val reply = replay.messages[0] as AssistantMessage.Reply
        assertEquals(listOf("a", "b"), reply.calls.map { it.id })
        assertEquals(listOf("b", "a"), (replay.messages[1] as AssistantMessage.Results).results.map { it.callId })
        assertEquals("While they run.", (replay.messages[2] as AssistantMessage.Reply).text)
        // Both results are right after their reply: kept as the chat keeps them, in the calls' order.
        assertEquals(listOf("a", "b"), (AssistantRules.closed(replay.messages)[1] as AssistantMessage.Results).results.map { it.callId })
    }

    @Test fun aCommandShowsItsCommandAndAFailedCallFails() {
        transcript.update(
            SessionUpdate.ToolCall(
                ToolCallId("c"), "Run tests", ToolKind.EXECUTE, ToolCallStatus.IN_PROGRESS,
                rawInput = JsonObject(mapOf("command" to JsonArray(listOf(JsonPrimitive("npm"), JsonPrimitive("test"))))),
            ),
        )
        transcript.update(SessionUpdate.ToolCallUpdate(ToolCallId("c"), status = ToolCallStatus.FAILED, rawOutput = JsonPrimitive("1 failing")))
        transcript.finish()
        val call = (replay.messages[0] as AssistantMessage.Reply).calls.single()
        assertEquals("npm test", call.command)
        val result = (replay.messages[1] as AssistantMessage.Results).results.single()
        assertTrue(result.failed)
        assertEquals("1 failing", result.output)
    }

    @Test fun commandsGoToThePageNotTheChat() {
        transcript.update(SessionUpdate.AvailableCommandsUpdate(listOf(AvailableCommand("review", "Review the changes", AvailableCommandInput.Unstructured("what to review")))))
        transcript.finish()
        assertEquals(listOf(AgentCommand("review", "Review the changes", "what to review")), commands)
        assertTrue(replay.messages.isEmpty())
    }
}
