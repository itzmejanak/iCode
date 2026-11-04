package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.runtime.GuestPaths
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantHistory
import dev.icode.mobile.domain.AssistantLive
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantPause
import dev.icode.mobile.domain.AssistantPermission
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSettings
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantTurn
import dev.icode.mobile.domain.LineChanges
import dev.icode.mobile.domain.NoticeTone
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The assistant's work (§4.4, §12 R1, R4, R6, R8): one turn at a time, its own job, never behind the
 * mutation lock — a message answered, or a chat compacted — and the questions it asks before a
 * change or a command. What it does goes to its chat, and onto the [page] when that chat is open.
 */
internal class AssistantTurns(
    private val page: AssistantPage,
    private val scope: CoroutineScope,
    private val engine: AssistantEngine,
    private val providers: AssistantProviderStore,
    /** The settings as saved, each provider's models as the engine knows them. */
    private val settings: () -> AssistantSettings,
    private val chats: AssistantChats,
    private val tools: (AssistantWorkspace, chat: String, step: AssistantStep) -> List<AssistantTool>,
    private val describe: (String) -> WorkspaceRoot,
    /** The runtime's paths: what the turn's workspace resolves the model's paths through. */
    private val paths: GuestPaths,
    private val attachments: AssistantAttachments,
    /** All Files Access, as Android answers it now: phone storage is read with it (docs/phone-agent-plan.md L8). */
    private val storageAccess: () -> Boolean,
) {
    private val lock = Any()
    private var job: Job? = null
    /** The workspace and chat [job] works in. */
    private var place: Pair<String, String>? = null
    /** The call running now: what an approval is about. */
    private var calling: AssistantCall? = null
    private val questions = AssistantQuestions(page)

    /** The lines each change to a file added and removed, by call, until its result is recorded. */
    private val counted = mutableMapOf<String, LineChanges>()

    /** The workspace and chat of the turn in hand; null when none runs. */
    val running: Pair<String, String>? get() = synchronized(lock) { place }

    fun send(workspace: String, session: String, input: String, attachments: List<String>, aloud: Boolean = false) =
        launch(workspace, session, aloud) { converse(input, attachments) }

    /** The last message, unanswered, sent again with its files as they were (§13 P9). */
    fun retry(workspace: String, session: String) = launch(workspace, session) {
        val setup = setup()
        val message = chats.withdraw(workspace, session) ?: return@launch
        AssistantRules.attachmentProblem(message.attachments.map { it.kind }, setup.model)?.let {
            chats.append(workspace, session, message)
            throw AssistantTurnException(it)
        }
        talk(setup, message)
    }

    fun compact(workspace: String, session: String) = launch(workspace, session) {
        compact(setup(), AssistantRules.forModel(chats.of(workspace, session).messages))
    }

    fun stop() {
        synchronized(lock) { job }?.cancel()
    }

    /** Stops the turn in [workspace], if one runs there, and waits until its chat is saved. */
    suspend fun stopIn(workspace: String) {
        synchronized(lock) { job.takeIf { place?.first == workspace } }?.cancelAndJoin()
    }

    fun answer(callId: String, answer: AssistantAnswer) = questions.answer(callId, answer)

    /** The turn's pause answered Continue (or Done): it goes on. */
    fun resume() = questions.resume()

    /** One turn at a time: the job, not the page, decides — a second Send in the same instant finds the first. */
    private fun launch(workspace: String, session: String, aloud: Boolean = false, work: suspend Turn.() -> Unit) = synchronized(lock) {
        if (job?.isActive == true) {
            val name = page.current.turn?.workspaceName ?: AssistantPlace.name(workspace)
            return@synchronized page.say(NoticeTone.HeadsUp, "The assistant is working in $name: stop it, or wait")
        }
        place = workspace to session
        job = scope.launch { Turn(workspace, session, aloud).run(work) }
    }

    /** What a turn works with, as set when it starts: a change made while it runs is for the next one. */
    private class Setup(
        val settings: AssistantSettings,
        val connection: AssistantConnection,
        val model: AssistantModel,
        val place: AssistantWorkspace,
    )

    /** A turn in [session] of [workspace], and the events of its engine. */
    private inner class Turn(val workspace: String, val session: String, val aloud: Boolean) : AssistantEvents {
        /** The step its tools are on (L2, L3), and the hand-over its phone tools wait on (§5.5). */
        val step = AssistantStep { why -> questions.pause(AssistantPause(AssistantPause.Kind.HandOver, why), THINKING) }

        /** This chat's mode now: the setting, raised by its own answers. */
        fun permission(): AssistantPermission = AssistantPermissions.effective(settings().permission, chats.allowedInWorkspace(workspace, session))

        suspend fun run(work: suspend Turn.() -> Unit) {
            var failure: String? = null
            try {
                val name = runCatching { describe(workspace).name }.getOrElse { AssistantPlace.name(workspace) }
                page.state {
                    // A new chat on the page is this one from now: it is listed with its first message.
                    val shown = if (it.workspace == workspace && it.session == null) session else it.session
                    it.copy(turn = AssistantTurn(workspace, name, session, THINKING, aloud = aloud), session = shown, failure = if (shows(it.workspace, shown)) null else it.failure)
                }
                work()
            } catch (stopped: CancellationException) {
                // Stopped by the user, with its workspace, or with the backend: what was said and done stays.
            } catch (refused: AssistantTurnException) {
                failure = refused.message
            } catch (unexpected: Exception) {
                // Its type only: the message of an unexpected failure may quote the chat.
                BackendLog.w(TAG, "A turn failed (${unexpected.javaClass.simpleName})")
                failure = "The assistant stopped: ${unexpected.message ?: unexpected.javaClass.simpleName}"
            } finally {
                withContext(NonCancellable) {
                    chats.close(workspace, session)
                    synchronized(lock) {
                        job = null
                        place = null
                        calling = null
                        counted.clear()
                    }
                    questions.clear()
                    page.listed(workspace)
                    page.refresh(workspace, session)
                    page.state { it.copy(turn = null, approval = null, pause = null, failure = if (shows(it.workspace, it.session)) failure else it.failure) }
                    if (failure != null && !page.current.let { shows(it.workspace, it.session) }) page.say(NoticeTone.Problem, "The assistant's turn failed", failure)
                }
            }
        }

        suspend fun converse(input: String, locations: List<String>) {
            val setup = setup()
            val files = attachments.read(setup.place, locations, setup.model) { bytes, extension -> chats.keep(workspace, session, bytes, extension) }
            talk(setup, AssistantMessage.User(input, files))
        }

        /** [message] added to the chat, and answered — retries transient rate-limit / connection with backoff, provider-agnostic. */
        suspend fun talk(setup: Setup, message: AssistantMessage.User) {
            val before = chats.of(workspace, session)
            chats.append(workspace, session, message)
            page.listed(workspace)
            page.refresh(workspace, session)
            if ((AssistantRules.contextShare(before.contextTokens, setup.model.contextWindow) ?: 0f) >= AssistantRules.COMPACT_AT) {
                compact(setup, AssistantRules.forModel(before.messages))
            }
            val history = AssistantRules.forModel(chats.of(workspace, session).messages.dropLast(1))
            step.seesPictures = setup.model.images
            val offered = tools(setup.place, session, step).filter { setup.settings.mode == AssistantMode.Code || setup.settings.mode == AssistantMode.Orchestrator || it.kind == AssistantToolKind.Read }
            val request = AssistantTurnRequest(
                connection = setup.connection,
                model = setup.model,
                effort = AssistantRules.effortFor(setup.settings.effort, setup.model),
                instructions = AssistantInstructions.of(setup.place, setup.settings.mode, permission(), history.summary, offered.any { it.name.startsWith(PHONE_TOOLS) }, aloud),
                history = history.messages,
                input = message,
                tools = offered.map { tool ->
                    AssistantGate(tool, setup.place, chats, page, questions, workspace, session, ::permission, { synchronized(lock) { calling } }) { call, changes ->
                        synchronized(lock) { counted[call.id] = changes }
                    }
                },
                maxSteps = MAX_STEPS,
                picture = step::take,
            )
            var next = request
            while (true) {
                try {
                    return runWithRetry { attempt ->
                        step(if (attempt == 0) THINKING else "Preparing to retry…")
                        engine.run(next, this)
                    }
                } catch (limit: AssistantStepLimitException) {
                    // The user says whether it goes on; it goes on from the chat as it is (L5).
                    questions.pause(AssistantPause(AssistantPause.Kind.StepLimit, "It has taken $MAX_STEPS steps on this message."), THINKING)
                    next = next.copy(history = AssistantRules.forModel(chats.of(workspace, session).messages).messages, input = null)
                }
            }
        }

        private suspend fun runWithRetry(block: suspend (Int) -> Unit) =
            AssistantRetries.runWithRetry(step = ::step, block = block)

        /** [history] summarized by the model and marked in the chat. */
        suspend fun compact(setup: Setup, history: AssistantHistory) =
            AssistantRetries.retryable(COMPACTING, step = ::step) {
                val instructions = AssistantInstructions.of(setup.place, setup.settings.mode, permission(), history.summary)
                chats.compacted(workspace, session, engine.compact(setup.connection, setup.model, instructions, history.messages))
                page.refresh(workspace, session)
            }

        fun setup(): Setup {
            val settings = settings()
            val provider = settings.provider
            val model = settings.model
            val connection = provider?.let { providers.connection(it.id) }
            if (model == null || connection == null) throw AssistantTurnException(NOT_SET_UP)
            val root = runCatching { describe(workspace) }.getOrElse { throw AssistantTurnException("This workspace no longer exists") }
            return Setup(settings, connection, model, AssistantWorkspace(File(root.location), paths, storageAccess))
        }

        override fun writing(reasoning: String, text: String) {
            page.state { it.copy(turn = it.turn?.copy(live = AssistantLive(reasoning, text))) }
        }

        override fun replied(reply: AssistantMessage.Reply) {
            step.calls = reply.calls
            chats.append(workspace, session, reply)
            page.refresh(workspace, session, recorded = true)
        }

        override fun calling(call: AssistantCall) {
            synchronized(lock) { calling = call }
            step.current = call
            val acting = call.tool.startsWith(PHONE_TOOLS)
            page.state { it.copy(turn = it.turn?.copy(step = call.summary, onPhone = it.turn.onPhone || acting)) }
        }

        override fun answered(result: AssistantResult) {
            // A change to a file says by how many lines, as its tool counted them while it ran.
            val changes = synchronized(lock) {
                calling = null
                counted.remove(result.callId)
            }
            chats.answer(workspace, session, result.copy(changes = changes))
            page.refresh(workspace, session)
            step(THINKING)
        }

        override fun used(tokens: Long) {
            chats.used(workspace, session, tokens)
            page.refresh(workspace, session)
        }

        private fun shows(shownWorkspace: String?, shownSession: String?) = shownWorkspace == workspace && shownSession == session

        private fun step(text: String) = page.state { it.copy(turn = it.turn?.copy(step = text)) }
    }

    private companion object {
        const val TAG = "Assistant"
        const val MAX_STEPS = 40
        const val THINKING = "Thinking…"
        const val COMPACTING = "Compacting the chat…"

        /** The phone tools' names start so: a turn that calls one acts on the phone (§5.6). */
        const val PHONE_TOOLS = "phone_"
        const val NOT_SET_UP = "Set up the assistant first: add a provider and choose a model"
    }
}
