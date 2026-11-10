package dev.icode.mobile.data.runtime

import kotlinx.coroutines.CompletableDeferred

/**
 * One terminal's commands, as its shell's marks bound them (docs/agent-control-plan.md §4.1): where a
 * prompt waits, empty, for a command; each command's output from its C to its D, as a reader wants it
 * ([ProcessOutput]) and as it was printed (the replay's, bounded at its start and its end); its exit
 * code; the directory the shell is in. Fed on the session's main thread by its output tap; read from
 * anywhere. A shell that prints no marks leaves [integrated] false, and nothing here is known of it.
 */
internal class TerminalCommands(
    /** A command ended: once, with its record. */
    private val onFinished: (Command) -> Unit = {},
    /** A command started running, or ended: on the session's main thread, as its output is read. */
    private val onRunning: (Boolean) -> Unit = {},
) : TerminalMarks.Listener {
    /** One command, from its output's start. Its [ended] completes with its exit code. */
    class Command internal constructor(val number: Int) {
        private val output = ProcessOutput(TEXT_LIMIT)
        private val raw = HeadAndTail(RAW_LIMIT)
        internal val ended = CompletableDeferred<Int?>()

        /** Where the shell was when the command ended; null until then, or when it says none. */
        @Volatile var directory: String? = null
            internal set

        internal fun add(data: ByteArray, from: Int, count: Int) = synchronized(this) {
            output.append(String(data, from, count, Charsets.UTF_8))
            raw.add(data, from, count)
        }

        /** What it printed, its escapes removed and its carriage returns applied: what a model reads. */
        fun text(): String = synchronized(this) { output.since(0).first }

        /** What it printed, as printed: its start and its end where it was longer than the replay keeps. */
        fun printed(): String = synchronized(this) { raw.text() }

        val exitCode: Int? get() = if (ended.isCompleted) ended.getCompleted() else null
        val running: Boolean get() = !ended.isCompleted

        /** The exit code once it ends, null when the shell gave none. */
        suspend fun await(): Int? = ended.await()
    }

    private val lock = Any()
    private val marks = TerminalMarks(this)
    private var phase = Phase.Unknown
    private var typed = false
    private var count = 0
    private var current: Command? = null
    private var next = CompletableDeferred<Command>()

    /** The shell prints marks: it can be told a command and be waited on. */
    @Volatile var integrated = false
        private set

    /** Where the shell is, as it last said (OSC 7). */
    @Volatile var directory: String? = null
        private set

    /** The session's output, from its tap. */
    fun feed(data: ByteArray, count: Int) = synchronized(lock) { marks.feed(data, count) }

    /** A prompt waits with nothing typed at it: a command written now is the next one run. */
    val ready: Boolean get() = synchronized(lock) { phase == Phase.Input && !typed }

    /** A command runs. */
    val running: Boolean get() = synchronized(lock) { phase == Phase.Running }

    /** The command the next C opens: what a command written now becomes. */
    fun nextCommand(): CompletableDeferred<Command> = synchronized(lock) { next }

    override fun output(data: ByteArray, from: Int, count: Int) {
        when (phase) {
            Phase.Running -> current?.add(data, from, count)
            // The line being typed, echoed: a command written now would be added to it.
            Phase.Input -> typed = true
            Phase.Unknown, Phase.Prompt -> Unit
        }
    }

    override fun mark(mark: TerminalMarks.Mark) {
        integrated = true
        when (mark) {
            TerminalMarks.Mark.PromptStart -> phase = Phase.Prompt
            TerminalMarks.Mark.InputStart -> {
                phase = Phase.Input
                typed = false
            }
            TerminalMarks.Mark.OutputStart -> {
                // Each line of a multi-line command prints its C; the command is one until its D.
                if (current != null) return
                phase = Phase.Running
                onRunning(true)
                val started = Command(++count).also { current = it }
                next.complete(started)
                next = CompletableDeferred()
            }
            is TerminalMarks.Mark.CommandEnd -> {
                // A prompt after an empty line, or the first one, ends no command.
                val ended = current ?: return
                current = null
                phase = Phase.Prompt
                onRunning(false)
                ended.directory = directory
                ended.ended.complete(mark.exitCode)
                onFinished(ended)
            }
            is TerminalMarks.Mark.Directory -> directory = mark.path
        }
    }

    /** The session ended: what still runs ends with it, its code unknown. */
    fun close() = synchronized(lock) {
        current?.ended?.complete(null)
        current = null
        next.cancel()
    }

    private enum class Phase { Unknown, Prompt, Input, Running }

    /** The first and the last of what came, [limit] bytes in all, and how much between them is gone. */
    private class HeadAndTail(limit: Int) {
        private val head = ByteArray(limit / 2)
        private var headSize = 0
        private val ring = ByteArray(limit / 2)
        private var ringStart = 0
        private var ringSize = 0
        private var dropped = 0L

        fun add(data: ByteArray, from: Int, count: Int) {
            val toHead = minOf(head.size - headSize, count)
            System.arraycopy(data, from, head, headSize, toHead)
            headSize += toHead
            for (index in from + toHead until from + count) {
                if (ringSize < ring.size) {
                    ring[(ringStart + ringSize++) % ring.size] = data[index]
                } else {
                    ring[ringStart] = data[index]
                    ringStart = (ringStart + 1) % ring.size
                    dropped++
                }
            }
        }

        fun text(): String {
            val first = String(head, 0, headSize, Charsets.UTF_8)
            val last = String(ByteArray(ringSize) { ring[(ringStart + it) % ring.size] }, Charsets.UTF_8)
            // Reset, so what the cut left of a colour or a cursor move never colours what follows.
            return if (dropped == 0L) first + last else "$first\u001b[0m\r\n… ($dropped bytes not kept) …\r\n$last"
        }
    }

    companion object {
        /** What a reader is given at most: a model reads less (32 000 characters). */
        const val TEXT_LIMIT = 256 * 1024

        /** What the replay keeps of a command, as printed (§3 A10). */
        const val RAW_LIMIT = 64 * 1024
    }
}
