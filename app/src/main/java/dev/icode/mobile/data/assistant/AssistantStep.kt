package dev.icode.mobile.data.assistant

import dev.icode.mobile.domain.AssistantCall
import java.util.concurrent.atomic.AtomicReference

/**
 * The step a turn is on, for the tools that need it (docs/phone-agent-plan.md L2, L3): the calls of
 * the reply being run, and the picture the step leaves for the model, sent with its results.
 */
internal class AssistantStep(
    /** The user takes a step themselves — a password, a payment — and the turn waits for Done (§5.5). */
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

/** A tool that acts in an app on the phone (docs/phone-agent-plan.md L6): the gate asks about the app. */
internal interface AssistantAppActor {
    /** The app [arguments] act in, its name and its package; null when it cannot be known yet. Never throws. */
    fun app(arguments: org.json.JSONObject): Pair<String, String>?
}
