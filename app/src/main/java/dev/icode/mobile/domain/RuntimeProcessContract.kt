package dev.icode.mobile.domain

// Managed processes (docs/runtime-and-terminal.md): what runs in the Linux runtime beyond
// the command that started it — a development server, a watcher, a worker — until it is stopped.

/** A long-running process of a workspace's, as the page and the assistant see it. */
data class ManagedProcess(
    /** iCode's own, in this process's lifetime: `p1`, `p2`… */
    val id: String,
    /** The workspace it runs in, by its location; null for one a chat in no workspace started (docs/design-and-navigation.md). */
    val workspace: String?,
    /** What it is called: given when started, or its command's first word. */
    val name: String,
    val command: String,
    /** Where it started, in the runtime: `/workspace/app/backend`. */
    val directory: String,
    val startedAt: Long,
    val state: ManagedProcessState,
    /** Ports on this phone its output names a server on (`http://127.0.0.1:8000`), at most a few, in order. */
    val ports: List<Int> = emptyList(),
) {
    val running: Boolean get() = state == ManagedProcessState.Running
}

sealed interface ManagedProcessState {
    data object Running : ManagedProcessState

    /** It ended: [code] as the shell reports it (128 + n for signal n); [stopped] when iCode was asked to stop it. */
    data class Ended(val code: Int, val at: Long, val stopped: Boolean) : ManagedProcessState
}

object ProcessRules {
    /** Ports a server on a phone can listen on: Android lets no app below 1024. */
    val LISTENABLE_PORTS = 1024..65535

    /**
     * An exit status in words: 0 succeeded, 126 found but not executable, 127 not found, 128 + n
     * ended by signal n; anything else failed.
     */
    fun exitMeaning(code: Int): String = when {
        code == 0 -> "succeeded"
        code == 126 -> "found but not executable"
        code == 127 -> "command not found"
        code in SIGNALLED -> "ended by ${signalName(code - SIGNAL_BASE)}"
        else -> "failed"
    }

    /** "SIGTERM" for 15; "signal 31" for one without a common name. */
    fun signalName(signal: Int): String = SIGNAL_NAMES[signal] ?: "signal $signal"

    /** "Running · 4m", "Exited 0", "Stopped", "Ended by SIGKILL": what the page says of [state]. */
    fun stateLabel(state: ManagedProcessState, now: Long, startedAt: Long): String = when (state) {
        ManagedProcessState.Running -> "Running · ${RelativeTime.span(now - startedAt)}"
        is ManagedProcessState.Ended -> when {
            state.stopped -> "Stopped"
            state.code in SIGNALLED -> "Ended by ${signalName(state.code - SIGNAL_BASE)}"
            else -> "Exited ${state.code}"
        }
    }

    /** What the Assistant page's row says of [processes]: "2 running", "1 running · 1 ended", "1 ended". */
    fun summary(processes: List<ManagedProcess>): String {
        val running = processes.count { it.running }
        val ended = processes.size - running
        return listOfNotNull("$running running".takeIf { running > 0 }, "$ended ended".takeIf { ended > 0 }).joinToString(" · ")
    }

    /** Where a port opens in the phone's browser: apps share the phone's loopback. */
    fun address(port: Int): String = "http://127.0.0.1:$port"

    /**
     * The ports on this phone [text] names a server on, in order, each once: an address as a URL
     * (`http://127.0.0.1:8000`, `ws://localhost:5173`), or bare (`0.0.0.0:8000`, `[::1]:3000`) on a
     * line that says it listens. An address elsewhere is not one: a request log's is its client's
     * (`INFO: 127.0.0.1:50744 - "GET /"`).
     */
    fun ports(text: String): List<Int> = text.lineSequence()
        .flatMap { line ->
            val urls = URL.findAll(line).map { it.groupValues[1] }
            if (LISTENS.containsMatchIn(line)) urls + ADDRESS.findAll(line).map { it.groupValues[1] } else urls
        }
        .mapNotNull { it.toIntOrNull() }
        .filter { it in LISTENABLE_PORTS }
        .distinct()
        .toList()

    private const val SIGNAL_BASE = 128
    private val SIGNALLED = 129..192
    private val SIGNAL_NAMES = mapOf(
        1 to "SIGHUP", 2 to "SIGINT", 3 to "SIGQUIT", 4 to "SIGILL", 6 to "SIGABRT", 7 to "SIGBUS",
        8 to "SIGFPE", 9 to "SIGKILL", 11 to "SIGSEGV", 13 to "SIGPIPE", 14 to "SIGALRM", 15 to "SIGTERM",
    )
    private const val HOST = """(?:localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1?\])"""
    private val ADDRESS = Regex("""(?<![\w.])$HOST:(\d{4,5})(?!\d)""")
    private val URL = Regex("""\b(?:https?|wss?)://$HOST:(\d{4,5})(?!\d)""", RegexOption.IGNORE_CASE)

    /** What a server says as it starts to listen: "Listening on", "running on", "serving at", "available on". */
    private val LISTENS = Regex("""\b(?:listen(?:s|ing)?|serving|running on|started (?:server )?on|bound to|available (?:at|on))\b""", RegexOption.IGNORE_CASE)
}
