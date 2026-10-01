package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.domain.AssistantToolKind
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

// What runs in the Linux runtime for the assistant (§4.3): Git's read-only views without asking,
// and any command — asked about first — in the workspace, as the terminal would run it.

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

/** A shell command in the workspace, as the terminal runs one: asked about first, stopped with the turn. */
internal class RunCommandTool(
    private val workspace: AssistantWorkspace,
    private val guest: GuestCommand,
    private val runtimeInstalled: () -> Boolean,
    /** The command may have changed files: the workspace refreshes as after a terminal command. */
    private val changed: () -> Unit,
) : AssistantTool {
    override val name = "run_command"
    override val description = "Runs a shell command (/bin/sh) in the workspace's top folder, in the Ubuntu runtime " +
        "(ARM64, no root, no systemd; apt via sudo). Nothing is typed into it, and it is stopped after " +
        "timeout_seconds. Answers its exit code and output."
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
            val result = withTimeoutOrNull(seconds * 1_000L) {
                // Nothing is typed into it: a command that reads its input ends instead of waiting.
                guest.stream(workspace.root.path, listOf("/bin/sh", "-c", "{ $command\n} </dev/null"), "assistant", ENVIRONMENT) { line ->
                    output.add(line)
                    true
                }
            }
            val stderr = result?.stderr?.trim().orEmpty()
            return buildString {
                append(if (result == null) "Stopped after $seconds s" else "Exit code ${result.exitCode}")
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

        /** No pager, prompt or colour codes: the output is read, not watched. */
        val ENVIRONMENT = mapOf(
            "PAGER" to "cat",
            "GIT_PAGER" to "cat",
            "GIT_TERMINAL_PROMPT" to "0",
            "DEBIAN_FRONTEND" to "noninteractive",
            "NO_COLOR" to "1",
            "TERM" to "dumb",
        )
    }
}
