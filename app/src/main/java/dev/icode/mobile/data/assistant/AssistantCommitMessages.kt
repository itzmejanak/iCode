package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.bounded
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantCommitMessage
import dev.icode.mobile.domain.AssistantSettings
import dev.icode.mobile.domain.CommitMessageRules
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Commit messages written by the model from the staged changes (docs/assistant-and-mcp.md):
 * read from the repository Git works in for the workspace, as Commit reads it, and written by the
 * model Settings › Source Control names, else the chat's. Each is its own job; nothing of a diff or
 * a message reaches the log.
 */
internal class AssistantCommitMessages(
    private val scope: CoroutineScope,
    private val engine: AssistantEngine,
    private val providers: AssistantProviderStore,
    private val settings: () -> AssistantSettings,
    private val page: AssistantPage,
    /** Git, for a workspace, in the repository it works in. */
    private val git: suspend (workspace: String, arguments: List<String>) -> CliGitService.CommandResult,
) {
    private var writing: Job? = null

    /** Which writing is the latest: an earlier one, stopped or not, never shows its result. */
    private val latest = AtomicLong()

    /** [workspace]'s staged changes described; the one being written before is stopped. */
    fun write(workspace: String) {
        writing?.cancel()
        val id = latest.incrementAndGet()
        page.state { it.copy(commitMessage = AssistantCommitMessage(workspace)) }
        writing = scope.launch {
            val written = try {
                AssistantCommitMessage(workspace, message = written(workspace))
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (refused: AssistantTurnException) {
                AssistantCommitMessage(workspace, failure = refused.message)
            } catch (unexpected: Exception) {
                // Its type only: a message can quote the diff.
                BackendLog.w(TAG, "Unable to write a commit message (${unexpected.javaClass.simpleName})")
                AssistantCommitMessage(workspace, failure = "The commit message could not be written — try again")
            }
            page.state { if (latest.get() == id) it.copy(commitMessage = written) else it }
        }
    }

    /** The message is in the commit field, or its failure was read: it is not offered again. */
    fun taken() = page.state { it.copy(commitMessage = it.commitMessage?.takeIf { message -> message.writing }) }

    private suspend fun written(workspace: String): String {
        val (provider, model) = settings().commitWriter ?: throw AssistantTurnException(NOT_SET_UP)
        val connection = providers.connection(provider.id) ?: throw AssistantTurnException(NOT_SET_UP)
        val files = read(workspace, listOf("diff", "--cached", "--numstat", "--find-renames"))
        if (files.isBlank()) throw AssistantTurnException("Nothing is staged: stage the changes to describe first")
        val diff = read(workspace, listOf("diff", "--cached", "--find-renames", "--no-color", "--no-ext-diff"))
        // A repository without commits has no log; its first commit follows no style.
        val log = git(workspace, listOf("log", "-n", SUBJECTS.toString(), "--no-merges", "--format=%s"))
        val subjects = if (log.succeeded) log.stdout.lines().filter { it.isNotBlank() } else emptyList()
        val request = CommitMessageRules.request(subjects, bounded(files, MAX_FILES), bounded(diff, MAX_DIFF))
        return CommitMessageRules.cleaned(engine.answer(connection, model, CommitMessageRules.INSTRUCTIONS, request))
            .ifEmpty { throw AssistantTurnException("The model sent an empty answer — try again") }
    }

    private suspend fun read(workspace: String, arguments: List<String>): String {
        val result = git(workspace, arguments)
        if (!result.succeeded) throw AssistantTurnException(result.errorMessage)
        return result.stdout
    }

    private companion object {
        const val TAG = "AssistantCommitMessages"
        const val NOT_SET_UP = "Set up the assistant first: add a provider and a model in Settings › Assistant"
        const val SUBJECTS = 10
        /** Lines added and removed per file: whole for all but the largest change. */
        const val MAX_FILES = 8_000
        /** The diff's head and tail: what the model needs to say what a change does. */
        const val MAX_DIFF = 24_000
    }
}
