package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.runtime.GuestPaths
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AssistantAgent
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantHistory
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantPause
import dev.icode.mobile.domain.AssistantPermission
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSettings
import dev.icode.mobile.domain.AssistantTurn
import dev.icode.mobile.domain.notices.AssistantNotices
import dev.icode.mobile.domain.notices.NoticePlace
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
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
    /** Each chat's waiting messages: the next when a chat's work ends, and the one Send now steers it with. */
    private val queue: AssistantQueue,
    /** A model refused the pictures sent to it: it is kept as reading none. */
    private val seesNoPictures: (AssistantChoice) -> Unit,
    /** The places' files before each message and as each turn leaves them, for Undo; none in tests that need none. */
    private val checkpoints: AssistantCheckpoints?,
    /** The coding agents, which answer the chats set to one (docs/assistant-and-mcp.md); none in tests that need none. */
    private val agents: AgentAnswers? = null,
) {
    /** The servers serve every chat: the last to end idles them. */
    private val working = AssistantWork(scope) { chat, last, goesOn ->
        if (last) tools.done()
        scope.launch { queue.ended(chat, goesOn) }
    }

    fun runs(workspace: String?, session: String): Boolean = working.runs(workspace to session)

    fun send(workspace: String?, session: String, input: String, attachments: List<String>, aloud: Boolean = false) =
        launch(workspace, session, aloud) { converse(input, attachments) }

    /** The last message, unanswered, sent again with its files as they were. */
    fun retry(workspace: String?, session: String) = launch(workspace, session) {
        answeringAgent()?.let { agent ->
            val message = chats.withdraw(workspace, session) ?: return@launch
            // Sent again, it starts the agent afresh, from the chat as it stands.
            agents?.forget(chat)
            return@launch agentTalk(agent, message)
        }
        val setup = setup()
        val message = chats.withdraw(workspace, session) ?: return@launch
        AssistantRules.attachmentProblem(message.attachments.map { it.kind }, setup.model)?.let {
            chats.append(workspace, session, message)
            throw AssistantTurnException(it)
        }
        talk(setup, message)
    }

    /** A chat iCode was closed while it worked, taken up again: a message asks to go on, shown nowhere (owner's choice, 2026-11-18). */
    fun resume(workspace: String?, session: String) = launch(workspace, session) {
        val asked = AssistantMessage.User(AssistantRules.GO_ON)
        answeringAgent()?.let { return@launch agentTalk(it, asked, shown = false) }
        talk(setup(), asked, shown = false)
    }

    fun compact(workspace: String?, session: String) = launch(workspace, session) {
        compact(setup(), AssistantRules.forModel(chats.of(workspace, session).messages))
    }

    fun stop(workspace: String?, session: String) = working.stop(workspace to session)

    /** [session]'s agents start afresh from the chat as it stands: its words changed under them. */
    fun afresh(workspace: String?, session: String) = agents?.forget(workspace to session)

    /** Stops every turn in [workspace], and waits until their chats are saved; their waiting messages wait. */
    suspend fun stopIn(workspace: String) = working.stopIn(workspace)

    /** One turn in a chat at a time; another chat's never waits on it. */
    private fun launch(workspace: String?, session: String, aloud: Boolean = false, work: suspend Turn.() -> Unit) {
        val turn = Turn(workspace, session, aloud)
        if (!working.start(turn.chat) { turn.run(work) }) page.tell(AssistantNotices.busy(NoticePlace(AssistantPlace.name(workspace), workspace)))
    }

    /** A turn in [session] of [workspace]. */
    private inner class Turn(val workspace: String?, val session: String, val aloud: Boolean, val began: Long = System.currentTimeMillis()) {
        val chat = workspace to session

        /** What its engine reports, and the step its tools are on. */
        val events = AssistantTurnEvents(
            workspace, session, page, chats, seesNoPictures,
            clarify = { call, asked -> questions.clarify(workspace, session, call, asked) },
            handOver = { why -> questions.pause(workspace, session, AssistantPause(why), THINKING) },
            takePhone = { working.takePhone(chat) },
        )

        /** Its checkpoints for Undo: before each message, and as it leaves the files. */
        val marks = AssistantTurnCheckpoints(checkpoints, events::show)
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
                chats.working(workspace, session)
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
                    marks.ended()
                    questions.clear(workspace, session)
                    page.listed(workspace)
                    page.refresh(workspace, session)
                    page.state { it.turnEnded(workspace, session).copy(failure = if (shows(it.opened, it.workspace, it.session)) failure else it.failure) }
                    page.ended(workspace, session, failure, stopped, began, shown = page.current.let { shows(it.opened, it.workspace, it.session) })
                }
            }
        }

        suspend fun converse(input: String, locations: List<String>) {
            answeringAgent()?.let { agent ->
                val name = agents?.name(agent.id) ?: agent.id
                return agentTalk(agent, AssistantMessage.User(input, attachments.read(place(), locations, AssistantModel(name, 0), ::keep)))
            }
            val setup = setup()
            talk(setup, AssistantMessage.User(input, files(setup, locations)))
        }

        /** The files at [locations], read for [setup]'s model: kept in the chat's folder where they are pictures or PDFs. */
        private suspend fun files(setup: AssistantSetup, locations: List<String>) = attachments.read(setup.place, locations, setup.model, ::keep)

        private fun keep(bytes: ByteArray, extension: String) = chats.keep(workspace, session, bytes, extension)

        /** The agent this chat is answered by: its own, or a new chat's, the one chosen last; null for a model. */
        fun answeringAgent(): AssistantAgent? =
            (chats.of(workspace, session).answerer ?: page.current.agent?.let(::AssistantAgent)) as? AssistantAgent

        /** [asked] added to the chat — unless not [shown] — and answered by [agent], in the chat's place. */
        suspend fun agentTalk(agent: AssistantAgent, asked: AssistantMessage.User, shown: Boolean = true) {
            val answering = agents ?: throw AssistantTurnException("Coding agents do not answer here")
            val place = place()
            events.chosen = agent.also { chats.choose(workspace, session, it) }
            events.update { it.copy(by = agent) }
            val history = chats.of(workspace, session).messages
            val message = if (shown) said(asked, place) else asked.also { marks.goingOn(history, place) }
            page.listed(workspace)
            page.refresh(workspace, session)
            events.show(THINKING)
            answering.answer(agent.id, chat, place, history, message, events, aloud) { call, options -> questions.choose(workspace, session, call, options) }
        }

        /** [asked] added to the chat, with the checkpoint Undo goes back to. */
        private fun said(asked: AssistantMessage.User, place: AssistantWorkspace) = marks.before(asked, place).also { chats.append(workspace, session, it) }

        /** [asked] added to the chat — unless not [shown] — and answered; retries transient rate-limit / connection with backoff, provider-agnostic. */
        suspend fun talk(setup: AssistantSetup, asked: AssistantMessage.User, shown: Boolean = true) {
            events.chosen = setup.choice.also { chats.choose(workspace, session, it) }
            events.update { it.copy(by = setup.choice) }
            val before = chats.of(workspace, session)
            // The message the turn works on: the one said, or for one not shown, the chat's last.
            val message = if (shown) said(asked, setup.place) else marks.goingOn(before.messages, setup.place) ?: asked
            page.listed(workspace)
            page.refresh(workspace, session)
            if (AssistantRules.nearFull(before.contextTokens, setup.model.contextWindow)) compact(setup, AssistantRules.forModel(before.messages))
            events.step.seesPictures = setup.model.images
            val offer = tools.of(setup.place, session, events.step, setup.settings.mode)
            val phone = offer.tools.any { it.name.startsWith(PHONE_TOOLS) }
            val gated = offer.tools.map { tool ->
                AssistantGate(tool, setup.place, chats, page, questions, workspace, session, ::permission, { events.step.current }) { call, changes ->
                    events.counted[call.id] = changes
                }
            }
            // The first request after a compaction made while it works says whether it helped: one still near full is not made again.
            var compacted = false
            var compacts = true
            var compacting = false
            fun request(history: AssistantHistory, input: AssistantMessage.User?) = AssistantTurnRequest(
                connection = setup.connection,
                model = setup.model,
                effort = AssistantRules.effortFor(setup.settings.effort, setup.model),
                instructions = AssistantInstructions.of(setup.place, setup.settings.mode, permission(), history.summary, phone, aloud) + offer.instructions,
                history = history.messages,
                input = input,
                tools = gated,
                picture = events.step::take,
                handBack = {
                    val full = AssistantRules.nearFull(chats.of(workspace, session).contextTokens, setup.model.contextWindow)
                    if (compacted) {
                        compacts = !full
                        compacted = false
                    }
                    compacting = full && compacts
                    queue.steering(chat) || compacting
                },
            )

            /** On from the chat as saved: its steps so far, after [message], and nothing new said. */
            fun goOn() = request(AssistantRules.forModel(chats.of(workspace, session).messages, message), input = null)
            var next = if (shown) request(AssistantRules.forModel(chats.of(workspace, session).messages.dropLast(1)), message)
            else request(AssistantRules.forModel(chats.of(workspace, session).messages, message), asked)
            // As many steps as it takes: no limit, and no asking whether to go on.
            while (true) {
                val end = runWithRetry { attempt ->
                    events.show(if (attempt == 0) THINKING else "Preparing to retry…")
                    // A retry after the model answered goes on from the chat: what it ran or asked is not done again.
                    if (attempt > 0 && AssistantRules.replied(chats.of(workspace, session).messages)) next = goOn()
                    engine.run(next, events)
                }
                if (end == AssistantRunEnd.Answered) return
                // Given back: near full, compacted; for Send now, the first waiting message joins the chat. It goes on by itself.
                // The screen the step left is not sent a step later.
                events.step.take()
                if (compacting) {
                    compact(setup, AssistantRules.forModel(chats.of(workspace, session).messages, message))
                    compacted = true
                }
                val steered = queue.steered(chat)
                next = if (steered == null) goOn() else {
                    val said = AssistantMessage.User(steered.text, files(setup, steered.files.map { it.location }), marks.take(setup.place))
                    chats.append(workspace, session, said)
                    page.refresh(workspace, session)
                    request(AssistantRules.forModel(chats.of(workspace, session).messages.dropLast(1), message), said)
                }
            }
        }

        private suspend fun <T> runWithRetry(block: suspend (Int) -> T): T = AssistantRetries.runWithRetry(step = events::show, block = block)

        /** [history] summarized by the model and marked in the chat. */
        suspend fun compact(setup: AssistantSetup, history: AssistantHistory) =
            AssistantRetries.retryable(COMPACTING, step = events::show) {
                val instructions = AssistantInstructions.of(setup.place, setup.settings.mode, permission(), history.summary)
                chats.compacted(workspace, session, engine.compact(setup.connection, setup.model, instructions, history.messages))
                page.refresh(workspace, session)
            }

        fun setup() = AssistantSetup.of(settings(), chats.of(workspace, session).answerer, providers, ::place)

        /** The chat's place: its workspace's folder, or the runtime's home for none. */
        private fun place(): AssistantWorkspace {
            val folder = workspace?.let { runCatching { File(describe(it).location) }.getOrElse { throw AssistantTurnException("This workspace no longer exists") } }
            return AssistantWorkspace(folder, paths, storageAccess)
        }

        private fun shows(opened: Boolean, shownWorkspace: String?, shownSession: String?) = opened && shownWorkspace == workspace && shownSession == session
    }

    private companion object {
        const val TAG = "Assistant"
        const val THINKING = AssistantTurnEvents.THINKING
        const val COMPACTING = "Compacting the chat…"

        /** The phone tools' names start so: a turn offered one is told how to act on the phone. */
        const val PHONE_TOOLS = "phone_"
    }
}
