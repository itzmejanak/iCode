package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.ProcessRules
import org.json.JSONObject

// What runs in the Linux runtime for the assistant (§4.3): Git's read-only views without asking,
// and any command — asked about first — in its workspace (docs/runtime-foundation-plan.md §3.3).

/** `git status` or `git diff`, in the repository that holds the path: read-only, so never asked about. */
internal class GitReadTool(
    private val workspace: AssistantWorkspace,
    private val git: CliGitService,
    private val diff: Boolean,
) : AssistantTool {
    override val name = if (diff) "git_diff" else "git_status"
    override val description = if (diff) {
        "Shows Git's diff of uncommitted changes, of one file or folder or of the whole repository; staged shows what is staged instead."
    } else {
        "Shows Git's status of the repository holding the path: the branch, and the files changed, staged or untracked."
    }
    override val parameters = listOfNotNull(
        AssistantParameter("path", "A file or folder, relative to the workspace's top; the top when absent", required = false),
        AssistantParameter("staged", "Show what is staged", AssistantParameterType.Boolean, required = false).takeIf { diff },
    )
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String =
        (if (diff) "Git diff" else "Git status") + (arguments.textOrNull("path")?.let { " $it" } ?: "")

    override suspend fun run(arguments: JSONObject): String {
        if (!git.isAvailable()) throw AssistantToolException("Git is not installed in the runtime; it installs from Source Control")
        val target = workspace.resolve(arguments.textOrNull("path") ?: ".")
        val guestTarget = if (target == workspace.root) workspace.guestPath else "${workspace.guestPath}/${workspace.relative(target)}"
        val folder = if (target.isDirectory) target else target.parentFile ?: workspace.root
        val arguments = if (diff) {
            listOfNotNull("diff", "--no-color", "--no-ext-diff", "--staged".takeIf { arguments.flag("staged") }, "--", guestTarget)
        } else {
            listOf("status", "--short", "--branch", "--", guestTarget)
        }
        val result = git.run(folder.path, arguments)
        if (!result.succeeded) throw AssistantToolException(result.errorMessage)
        return bounded(result.stdout, MAX_CHARACTERS).ifBlank { if (diff) "No changes" else "Nothing to commit" }
    }

    private companion object {
        const val MAX_CHARACTERS = 64_000
    }
}

/**
 * A command in bash in the workspace's runtime (docs/runtime-foundation-plan.md §3.3): asked about
 * first, stopped with the turn or at its time limit, in the directory its chat's last command
 * ended in, answered with its exit status in words.
 */
internal class RunCommandTool(
    private val workspace: AssistantWorkspace,
    private val chat: String,
    private val shell: AssistantShell,
    private val runtimeInstalled: () -> Boolean,
    /** The command may have changed files: the workspace refreshes as after a terminal command. */
    private val changed: () -> Unit,
) : AssistantTool {
    override val name = "run_command"
    override val description = "Runs a command in bash in the Ubuntu runtime (ARM64, under PRoot: uid 0 without root's powers, " +
        "no systemd) with the user's login environment, and answers its exit code, the directory it ended in and its output. " +
        "It starts where the last command ended — the workspace's top at first — so `cd` carries over; `export` and `source` " +
        "do not, so set what a command needs in that command. Its input is closed. It is stopped after timeout_seconds, with " +
        "everything it started: run a server, a watcher or anything that must keep running with start_process. It reaches " +
        "the whole runtime, but only this workspace under /workspace; the file tools stay inside the workspace."
    override val parameters = listOf(
        AssistantParameter("command", "The command line, as typed in a terminal"),
        AssistantParameter(
            "timeout_seconds", "Stop it after this many seconds: default $DEFAULT_TIMEOUT, at most $MAX_TIMEOUT",
            AssistantParameterType.Integer, required = false,
        ),
    )
    override val kind = AssistantToolKind.Command

    override fun summary(arguments: JSONObject): String = "Run `${arguments.textOrNull("command").orEmpty().lineSequence().first()}`"

    override fun detail(arguments: JSONObject): String = arguments.textOrNull("command").orEmpty()

    override suspend fun run(arguments: JSONObject): String {
        val command = arguments.text("command").ifBlank { throw AssistantToolException("The command is empty") }
        if (!runtimeInstalled()) throw AssistantToolException("The Linux runtime is not installed")
        val seconds = arguments.intOr("timeout_seconds", DEFAULT_TIMEOUT).coerceIn(1, MAX_TIMEOUT)
        val output = Output(MAX_CHARACTERS)
        try {
            val ran = shell.run(workspace, chat, command, seconds, output::add)
            val stderr = ran.stderr.trim()
            return buildString {
                val code = ran.exitCode
                if (code == null) {
                    append("Stopped after $seconds s, still running: it ended with everything it started. ")
                    append("Run a server, a watcher or anything that must keep running with start_process.")
                } else {
                    append("Exit code $code (${ProcessRules.exitMeaning(code)})")
                }
                append("\nin ").append(ran.directory)
                ran.notes.forEach { append("\n").append(it) }
                if (code == 127) append("\nPATH: ").append(ran.path)
                output.text().takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
                if (stderr.isNotEmpty()) append("\n\nstderr:\n").append(bounded(stderr, MAX_CHARACTERS / 4))
            }
        } finally {
            changed()
        }
    }

    /** What a command printed, kept to its start and its end within [limit]. */
    private class Output(private val limit: Int) {
        private val head = StringBuilder()
        private val tail = ArrayDeque<String>()
        private var tailLength = 0
        private var dropped = 0

        fun add(line: String) {
            if (head.length < limit / 2) {
                head.append(line).append('\n')
                return
            }
            tail.addLast(line)
            tailLength += line.length + 1
            while (tailLength > limit / 2 && tail.isNotEmpty()) {
                tailLength -= tail.removeFirst().length + 1
                dropped++
            }
        }

        fun text(): String = buildString {
            append(head)
            if (dropped > 0) append("… ($dropped lines not shown) …\n")
            tail.forEach { append(it).append('\n') }
        }.trimEnd()
    }

    private companion object {
        const val DEFAULT_TIMEOUT = 120
        const val MAX_TIMEOUT = 600
        const val MAX_CHARACTERS = 32_000
    }
}
