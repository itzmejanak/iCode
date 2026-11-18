package dev.icode.mobile.data.assistant

import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantAnswerer
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantLive
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantQuestion
import dev.icode.mobile.domain.AssistantReply
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantTerminal
import dev.icode.mobile.domain.AssistantTurn
import dev.icode.mobile.domain.LineChanges
import java.util.concurrent.ConcurrentHashMap

/**
 * What a turn's engine reports (docs/assistant-and-mcp.md), as [session]'s chat keeps it and the page
 * shows it — the reply being written, each reply, the call running and its result, the tokens a
 * request used — and the step its tools move through. Only its own turn on the page is changed.
 */
internal class AssistantTurnEvents(
    private val workspace: String?,
    private val session: String,
    private val page: AssistantPage,
    private val chats: AssistantChats,
    /** A model refused the pictures sent to it: it is kept as reading none. */
    private val seesNoPictures: (AssistantChoice) -> Unit,
    clarify: suspend (AssistantCall, List<AssistantQuestion>) -> AssistantReply,
    handOver: suspend (why: String) -> Unit,
    /** The turn takes the phone's screen: true when it has just taken it ([AssistantWork.takePhone]). */
    takePhone: () -> Boolean,
) : AssistantEvents {
    /** The step its tools are on, the hand-over its phone tools wait on, and the screen they take. */
    val step = AssistantStep(
        progress = ::show,
        terminal = { callId, tab -> update { it.copy(terminal = AssistantTerminal(callId, tab)) } },
        clarify = clarify,
        takePhone = { if (takePhone()) update { it.copy(onPhone = true) } },
        handOver = handOver,
    )

    /** The lines each change to a file added and removed, by call, until its result is recorded. */
    val counted = ConcurrentHashMap<String, LineChanges>()

    /** Who answers the turn: its replies are kept as theirs (docs/assistant-and-mcp.md). */
    @Volatile var chosen: AssistantAnswerer? = null

    /** What the turn does now, on its step's line. */
    fun show(text: String) = update { it.copy(step = text) }

    /** Its own turn on the page, changed: another chat's is never touched. */
    fun update(change: (AssistantTurn) -> AssistantTurn) = page.state { it.changeTurn(workspace, session, change) }

    override fun writing(reasoning: String, text: String) {
        update { it.copy(live = AssistantLive(reasoning, text)) }
    }

    override fun replied(reply: AssistantMessage.Reply) {
        step.calls = reply.calls
        chats.append(workspace, session, reply.copy(by = chosen))
        page.refresh(workspace, session, recorded = true)
    }

    override fun calling(call: AssistantCall) {
        step.current = call
        show(call.summary)
    }

    override fun answered(result: AssistantResult) {
        // A change to a file says by how many lines, as its tool counted them while it ran.
        val changes = counted.remove(result.callId)
        chats.answer(workspace, session, result.copy(changes = changes, printed = step.takePrinted(result.callId)))
        page.refresh(workspace, session)
        val changed = changes != null && !result.failed
        update { it.copy(step = THINKING, terminal = null, changedFiles = it.changedFiles || changed) }
    }

    override fun used(tokens: Long) {
        chats.used(workspace, session, tokens)
        page.refresh(workspace, session)
    }

    /** Its model does not see after all: the rest of the turn sends no picture. */
    override fun picturesRefused() {
        step.seesPictures = false
        (chosen as? AssistantChoice)?.let(seesNoPictures)
    }

    companion object {
        const val THINKING = "Thinking…"
    }
}
