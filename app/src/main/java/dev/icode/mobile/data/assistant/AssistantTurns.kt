package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.runtime.GuestPaths
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantHistory
import dev.icode.mobile.domain.AssistantLive
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantPause
import dev.icode.mobile.domain.AssistantPermission
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSettings
import dev.icode.mobile.domain.AssistantTerminal
import dev.icode.mobile.domain.AssistantTurn
import dev.icode.mobile.domain.LineChanges
import dev.icode.mobile.domain.notices.AssistantNotices
import dev.icode.mobile.domain.notices.NoticePlace
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The assistant's work in each chat: a turn — a message answered, or a chat compacted — in its own
 * job, never behind the mutation lock, in as many chats at once as the user starts ([AssistantWork]);
 * and the questions each asks before a change or a command. What a turn does goes to its chat, and
 * onto the [page] when that chat is open.
 */
internal class AssistantTurns(
    private val page: AssistantPage,
    private val scope: CoroutineScope,
    private val engine: AssistantEngine,
    private val providers: AssistantProviderStore,
    /** The settings as saved, each provider's models as the engine knows them. */
    private val settings: () -> AssistantSettings,
    private val chats: AssistantChats,
    private val tools: AssistantTools,
    private val describe: (String) -> WorkspaceRoot,
    /** The runtime's paths: what the turn's workspace resolves the model's paths through. */
    private val paths: GuestPaths,
    private val attachments: AssistantAttachments,
    /** All Files Access, as Android answers it now: phone storage is read with it (docs/voice-and-phone-control.md). */
    private val storageAccess: () -> Boolean,
    /** What the turn asks the user; its answers come to it from the coordinator. */
    private val questions: AssistantQuestions,
) {
    /** The servers serve every chat: the last chat to end idles them. */
    private val working = AssistantWork(scope) { tools.done() }

    fun runs(workspace: String?, session: String): Boolean = working.runs(workspace to session)

    fun send(workspace: String?, session: String, input: String, attachments: List<String>, aloud: Boolean = false) =
        launch(workspace, session, aloud) { converse(input, attachments) }

    /** The last message, unanswered, sent again with its files as they were. */
    fun retry(workspace: String?, session: String) = launch(workspace, session) {
        val setup = setup()
        val message = chats.withdraw(workspace, session) ?: return@launch
        AssistantRules.attachmentProblem(message.attachments.map { it.kind }, setup.model)?.let {
            chats.append(workspace, session, message)
            throw AssistantTurnException(it)
        }
        talk(setup, message)
    }

    fun compact(workspace: String?, session: String) = launch(workspace, session) {
        compact(setup(), AssistantRules.forModel(chats.of(workspace, session).messages))
    }

    fun stop(workspace: String?, session: String) = working.stop(workspace to session)

    /** Stops every turn in [workspace], and waits until their chats are saved. */
    suspend fun stopIn(workspace: String) = working.stopIn(workspace)

    /** One turn in a chat at a time; another chat's never waits on it. */
    private fun launch(workspace: String?, session: String, aloud: Boolean = false, work: suspend Turn.() -> Unit) {
        val turn = Turn(workspace, session, aloud)
        if (!working.start(turn.chat) { turn.run(work) }) page.tell(AssistantNotices.busy(NoticePlace(AssistantPlace.name(workspace), workspace)))
    }

    /** What a turn works with, as set when it starts: a change made while it runs is for the next one. */
    private class Setup(val settings: AssistantSettings, val connection: AssistantConnection, val model: AssistantModel, val place: AssistantWorkspace, val choice: AssistantChoice)

    /** A turn in [session] of [workspace], and the events of its engine. */
    private inner class Turn(val workspace: String?, val session: String, val aloud: Boolean, val began: Long = System.currentTimeMillis()) : AssistantEvents {
        val chat = workspace to session

        /** The step its tools are on, the hand-over its phone tools wait on, and the screen they take. */
        val step = AssistantStep(
            clarify = { call, asked -> questions.clarify(workspace, session, call, asked) },
            handOver = { why -> questions.pause(workspace, session, AssistantPause(AssistantPause.Kind.HandOver, why), THINKING) },
            progress = { text -> step(text) },
            terminal = { callId, tab -> update { it.copy(terminal = AssistantTerminal(callId, tab)) } },
            takePhone = { if (working.takePhone(chat)) update { it.copy(onPhone = true) } },
        )

        /** The lines each change to a file added and removed, by call, until its result is recorded. */
        val counted = ConcurrentHashMap<String, LineChanges>()

        /** Who answers this turn: its replies are kept as theirs (docs/assistant-and-mcp.md). */ var chosen: AssistantChoice? = null
        /** This chat's mode now: the setting, raised by its own answers. */
        fun permission(): AssistantPermission = AssistantPermissions.effective(settings().permission, chats.allowedInWorkspace(workspace, session), workspace != null)

        suspend fun run(work: suspend Turn.() -> Unit) {
            var failure: String? = null
            var stopped = false
            try {
                val name = workspace?.let { runCatching { describe(it).name }.getOrNull() } ?: AssistantPlace.name(workspace)
                page.state {
                    it.copy(turns = it.turns + AssistantTurn(workspace, name, session, THINKING, aloud = aloud), failure = if (shows(it.opened, it.workspace, it.session)) null else it.failure)
                }
                work()
            } catch (cancelled: CancellationException) {
                stopped = true // By the user, with its workspace, or with the backend: what was said and done stays.
            } catch (refused: AssistantTurnException) {
                failure = refused.message
            } catch (unexpected: Exception) {
                // Its type only: the message of an unexpected failure may quote the chat.
                BackendLog.w(TAG, "A turn failed (${unexpected.javaClass.simpleName})")
                failure = "The assistant stopped: ${unexpected.message ?: unexpected.javaClass.simpleName}"
            } finally {
                withContext(NonCancellable) {
                    chats.close(workspace, session)
                    questions.clear(workspace, session)
                    page.listed(workspace)
                    page.refresh(workspace, session)
                    page.state { it.turnEnded(workspace, session).copy(failure = if (shows(it.opened, it.workspace, it.session)) failure else it.failure) }
                    page.ended(workspace, session, failure, stopped, began, shown = page.current.let { shows(it.opened, it.workspace, it.session) })
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
            chosen = setup.choice.also { chats.choose(workspace, session, it) }
            update { it.copy(by = setup.choice) }
            val before = chats.of(workspace, session)
            chats.append(workspace, session, message)
            page.listed(workspace)
            page.refresh(workspace, session)
            if ((AssistantRules.contextShare(before.contextTokens, setup.model.contextWindow) ?: 0f) >= AssistantRules.COMPACT_AT) {
                compact(setup, AssistantRules.forModel(before.messages))
            }
            val history = AssistantRules.forModel(chats.of(workspace, session).messages.dropLast(1))
            step.seesPictures = setup.model.images
            val offer = tools.of(setup.place, session, step, setup.settings.mode)
            val offered = offer.tools
            val request = AssistantTurnRequest(
                connection = setup.connection,
                model = setup.model,
                effort = AssistantRules.effortFor(setup.settings.effort, setup.model),
                instructions = AssistantInstructions.of(setup.place, setup.settings.mode, permission(), history.summary, offered.any { it.name.startsWith(PHONE_TOOLS) }, aloud) + offer.instructions,
                history = history.messages,
                input = message,
                tools = offered.map { tool ->
                    AssistantGate(tool, setup.place, chats, page, questions, workspace, session, ::permission, { step.current }) { call, changes ->
                        counted[call.id] = changes
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
                        // A retry after the model answered goes on from the chat: what it ran or asked is not done again.
                        if (attempt > 0 && AssistantRules.replied(chats.of(workspace, session).messages)) next = goOn(next)
                        engine.run(next, this)
                    }
                } catch (limit: AssistantStepLimitException) {
                    // The user says whether it goes on; it goes on from the chat as it is.
                    questions.pause(workspace, session, AssistantPause(AssistantPause.Kind.StepLimit, "It has taken $MAX_STEPS steps on this message."), THINKING)
                    next = goOn(next)
                }
            }
        }

        /** [request] going on from the chat as saved: its steps so far are the history, and nothing new is said. */
        private fun goOn(request: AssistantTurnRequest) = request.copy(history = AssistantRules.forModel(chats.of(workspace, session).messages).messages, input = null)

        private suspend fun runWithRetry(block: suspend (Int) -> Unit) = AssistantRetries.runWithRetry(step = ::step, block = block)

        /** [history] summarized by the model and marked in the chat. */
        suspend fun compact(setup: Setup, history: AssistantHistory) =
            AssistantRetries.retryable(COMPACTING, step = ::step) {
                val instructions = AssistantInstructions.of(setup.place, setup.settings.mode, permission(), history.summary)
                chats.compacted(workspace, session, engine.compact(setup.connection, setup.model, instructions, history.messages))
                page.refresh(workspace, session)
            }

        /** The chat's own model while its provider still offers it, else what a new chat starts with. */
        fun setup(): Setup {
            val settings = settings()
            val choice = (chats.of(workspace, session).answerer as? AssistantChoice)?.takeIf { settings.modelOf(it) != null } ?: settings.choice
            val model = settings.modelOf(choice)
            val connection = settings.providerOf(choice)?.let { providers.connection(it.id) }
            if (choice == null || model == null || connection == null) throw AssistantTurnException(NOT_SET_UP)
            val folder = workspace?.let { runCatching { File(describe(it).location) }.getOrElse { throw AssistantTurnException("This workspace no longer exists") } }
            return Setup(settings, connection, model, AssistantWorkspace(folder, paths, storageAccess), choice)
        }

        override fun writing(reasoning: String, text: String) {
            update { it.copy(live = AssistantLive(reasoning, text)) }
        }

        override fun replied(reply: AssistantMessage.Reply) {
            step.calls = reply.calls
            chats.append(workspace, session, reply.copy(by = chosen))
            page.refresh(workspace, session, recorded = true)
        }

        override fun calling(call: AssistantCall) {
            step.current = call
            step(call.summary)
        }

        override fun answered(result: AssistantResult) {
            // A change to a file says by how many lines, as its tool counted them while it ran.
            val changes = counted.remove(result.callId)
            chats.answer(workspace, session, result.copy(changes = changes, printed = step.takePrinted(result.callId)))
            page.refresh(workspace, session)
            val changed = changes != null && !result.failed
            update { it.copy(step = THINKING, terminal = null, changedFiles = it.changedFiles || changed) }
        }

        override fun used(tokens: Long) {
            chats.used(workspace, session, tokens)
            page.refresh(workspace, session)
        }

        /** Its model does not see after all: the rest of the turn sends no picture, and it is kept as reading none. */
        override fun picturesRefused() {
            step.seesPictures = false
            val settings = settings()
            val provider = settings.providerOf(chosen) ?: return
            val model = settings.modelOf(chosen) ?: return
            val models = provider.models.map { if (it.id == model.id) it.spec.copy(images = false) else it.spec }
            runCatching { providers.saveProvider(provider.id, provider.kind, provider.name, provider.baseUrl, key = null, models) }
                .onFailure { return }
            page.state { it.copy(settings = settings()) }
            page.tell(AssistantNotices.noPictures(model.id))
        }

        private fun shows(opened: Boolean, shownWorkspace: String?, shownSession: String?) = opened && shownWorkspace == workspace && shownSession == session

        private fun step(text: String) = update { it.copy(step = text) }

        /** Its own turn on the page changed: another chat's is never touched. */
        private fun update(change: (AssistantTurn) -> AssistantTurn) = page.state { it.changeTurn(workspace, session, change) }
    }

    private companion object {
        const val TAG = "Assistant"
        const val MAX_STEPS = 40
        const val THINKING = "Thinking…"
        const val COMPACTING = "Compacting the chat…"

        /** The phone tools' names start so: a turn offered one is told how to act on the phone. */
        const val PHONE_TOOLS = "phone_"
        const val NOT_SET_UP = "Set up the assistant first: add a provider and choose a model"
    }
}
