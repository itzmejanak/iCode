package dev.icode.mobile.data.runtime

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A program in the Linux runtime that speaks a protocol over its stdin and stdout — an MCP server
 * (docs/mcp-plan.md §7), a language server (docs/lsp-plan.md §5.2): a PRoot of its own that ends
 * with iCode, exactly the [variables] given, its command and arguments as argv — never read again by
 * a shell — and stderr in a log of its own under [logs], so nothing it prints lands among the
 * protocol's bytes (the native bridge joins stderr to stdout, icode_spawn.c).
 */
internal class GuestStdio(
    private val layout: RuntimeLayout,
    context: GuestContext,
    /** The guest folder its logs go in. */
    private val logs: String,
    /** What its own checks are named by, in the runtime's temporary files. */
    private val tag: String,
) {
    private val guest = GuestCommand(layout, context)

    /** A started program: its streams, and the process to stop or watch end. */
    class Started internal constructor(private val process: RuntimeProcess, val ended: CompletableDeferred<Int>) {
        /** What the program reads. */
        val input: OutputStream get() = process.input

        /** What the program writes on stdout: the protocol alone. */
        val output: InputStream get() = process.output

        val pid: Int get() = process.pid

        /**
         * As MCP and LSP stop one: [closeInput] (its stdin, or the protocol's own close), then
         * SIGTERM, then SIGKILL, [GRACE_MILLIS] apart.
         */
        suspend fun stop(closeInput: suspend () -> Unit = { process.input.close() }) {
            runCatching { closeInput() }
            if (withTimeoutOrNull(GRACE_MILLIS) { ended.await() } != null) return
            process.sendSignal(PosixSignal.TERM)
            if (withTimeoutOrNull(GRACE_MILLIS) { ended.await() } != null) return
            process.destroy()
        }

        /** At once, for a program that must not outlive what started it. */
        fun kill() = process.destroy()
    }

    /** [command] when the runtime has no such program for [variables]' PATH; null when it has. */
    suspend fun missing(command: String, variables: Map<String, String>): String? {
        val found = guest.capture(null, listOf("/bin/bash", "-c", "command -v \"\$0\"", command), tag, variables)
        return if (found.exitCode == 0) null else command
    }

    /** Starts [argv] for [id] in [folder] (a guest path); its log starts empty. */
    fun start(id: String, argv: List<String>, variables: Map<String, String>, folder: String, scope: CoroutineScope): Started {
        require(argv.isNotEmpty()) { "A guest program needs a command" }
        log(id).apply { parentFile?.mkdirs(); writeText("") }
        val process = RuntimeProcess.start(
            argv = layout.prootCommand(
                // $0 is the log; "$@" the command and its arguments, exactly as given.
                guestCommand = listOf("/bin/bash", "-c", "exec 2>>\"\$0\"; exec \"\$@\"", "$logs/$id.log") + argv,
                variables = variables,
                guestWorkingDirectory = folder,
                killOnExit = true,
            ),
            environment = layout.hostEnvironment(),
            workingDirectory = layout.runtimeRoot.absolutePath,
        )
        val ended = CompletableDeferred<Int>()
        scope.launch(Dispatchers.IO) { ended.complete(process.waitFor()) }
        return Started(process, ended)
    }

    /** The last [lines] [id]'s stderr wrote, since it last started. */
    fun tail(id: String, lines: Int = LOG_LINES): List<String> =
        runCatching { log(id).readLines().takeLast(lines) }.getOrDefault(emptyList())

    private fun log(id: String) = File(layout.rootfs, "${logs.removePrefix("/")}/$id.log")

    companion object {
        const val LOG_LINES = 200
        const val GRACE_MILLIS = 3_000L
    }
}
