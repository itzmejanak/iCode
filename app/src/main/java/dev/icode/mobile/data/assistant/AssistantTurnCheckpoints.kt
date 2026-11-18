package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.domain.AssistantMessage

/**
 * One turn's checkpoints for Undo (docs/assistant-and-mcp.md): its place's files before each message
 * it answers, and the last of them kept again as the turn leaves the files. [checkpoints] is null
 * where none are kept; [show] names the step while one is taken.
 */
internal class AssistantTurnCheckpoints(private val checkpoints: AssistantCheckpoints?, private val show: (String) -> Unit) {
    /** The last checkpoint taken, and where: kept again as the turn ends. */
    private var kept: Pair<String, AssistantWorkspace>? = null

    /** [place]'s files now: the checkpoint's ID, or null when none is kept. */
    fun take(place: AssistantWorkspace): String? {
        val owner = checkpoints ?: return null
        show(KEEPING)
        return owner.take(place)?.also { kept = it to place }
    }

    /** [message] with the checkpoint Undo goes back to: one sent again keeps its own. */
    fun before(message: AssistantMessage.User, place: AssistantWorkspace): AssistantMessage.User =
        message.copy(checkpoint = message.checkpoint?.also { kept = it to place } ?: take(place))

    /** A message not shown goes on with [history]'s last: the work is that message's, under its checkpoint. */
    fun goingOn(history: List<AssistantMessage>, place: AssistantWorkspace): AssistantMessage.User? =
        (history.lastOrNull { it is AssistantMessage.User } as? AssistantMessage.User)?.also { last -> last.checkpoint?.let { kept = it to place } }

    /** The turn ended: the files as it left them are kept with its last checkpoint. */
    fun ended() = kept?.let { (id, place) -> checkpoints?.ended(place, id) }

    private companion object {
        const val KEEPING = "Keeping a checkpoint…"
    }
}
