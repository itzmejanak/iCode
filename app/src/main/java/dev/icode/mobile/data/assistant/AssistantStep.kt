package dev.icode.mobile.data.assistant

import dev.icode.mobile.domain.AssistantCall
import java.util.concurrent.atomic.AtomicReference

/**
 * The step a turn is on, for the tools that need it (docs/phone-agent-plan.md L2, L3): the calls of
 * the reply being run, and the picture the step leaves for the model, sent with its results.
 */
internal class AssistantStep(
    /** What a tool says it is doing now, for the turn's step: an MCP server's progress (docs/mcp-plan.md §6.5). */
    val progress: (String) -> Unit = {},
    /** The user takes a step themselves — a password, a payment — and the turn waits for Done (§5.5). Last: a trailing lambda is this. */
    val handOver: suspend (why: String) -> Unit = {},
) {
    @Volatile
    var calls: List<AssistantCall> = emptyList()
        internal set

    /** The call running now. */
    @Volatile
    var current: AssistantCall? = null
        internal set

    /** The model the turn asks sees pictures: a step may leave one. */
    @Volatile
    var seesPictures: Boolean = false
        internal set

    private val picture = AtomicReference<AssistantPicture?>()

    /** [call] is the last of this reply's calls to any of [tools]: the one after which the screen is read once. */
    fun lastOf(call: String, tools: Set<String>): Boolean = calls.lastOrNull { it.tool in tools }?.id == call

    /** [shown] goes to the model with this step's results, in place of any left before. */
    fun leave(shown: AssistantPicture) = picture.set(shown)

    /** The picture left, taken: a later step never sends it again. */
    fun take(): AssistantPicture? = picture.getAndSet(null)
}

/**
 * A tool that acts in a place outside the files (docs/phone-agent-plan.md L6, docs/mcp-plan.md §6.4) —
 * an app on the phone, an MCP server: the gate asks about the place, as about a folder outside.
 */
internal interface AssistantPlaceActor {
    /** The place [arguments] act in; null when it cannot be known yet, or the call needs no asking. Never throws. */
    fun place(arguments: org.json.JSONObject): dev.icode.mobile.domain.AssistantElsewhere?
}
