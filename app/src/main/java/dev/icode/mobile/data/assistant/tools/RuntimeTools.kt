package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantToolName
import java.io.File
import org.json.JSONObject

// Git's read-only views in the Linux runtime for the assistant (§4.3), without asking. Its commands
// run in the terminal (TerminalTools, docs/agent-control-plan.md §4.3).

/** `git status` or `git diff`, in the repository that holds the path: read-only, so never asked about. */
internal class GitReadTool(
    private val workspace: AssistantWorkspace,
    private val git: CliGitService,
    private val diff: Boolean,
) : AssistantTool {
    override val name = (if (diff) AssistantToolName.GitDiff else AssistantToolName.GitStatus).id
    override val description = if (diff) {
        "Shows Git's diff of uncommitted changes, of one file or folder or of the whole repository; staged shows what is staged instead."
    } else {
        "Shows Git's status of the repository holding the path: the branch, and the files changed, staged or untracked."
    }
    override val parameters = listOfNotNull(
        AssistantParameter("path", "A file or folder; the workspace's top when absent", required = false),
        AssistantParameter("staged", "Show what is staged", AssistantParameterType.Boolean, required = false).takeIf { diff },
    )
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String =
        (if (diff) "Git diff" else "Git status") + (arguments.textOrNull("path")?.let { " $it" } ?: "")

    override fun reach(arguments: JSONObject): List<File> = workspace.reach(arguments.textOrNull("path"))

    override suspend fun run(arguments: JSONObject): String {
        if (!git.isAvailable()) throw AssistantToolException("Git is not installed in the runtime; it installs from Source Control")
        val target = workspace.resolve(arguments.textOrNull("path") ?: ".")
        val guestTarget = workspace.guestOf(target)
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
