package dev.icode.mobile.data.assistant

import android.content.Context
import dev.icode.mobile.data.assistant.koog.KoogAssistantEngine
import dev.icode.mobile.data.assistant.tools.AssistantToolbox
import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantApproval
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantTurn
import dev.icode.mobile.domain.WorkbenchIntent
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The assistant (docs/assistant-koog-plan.md): the chat shown, the turn in hand — one at a time, its
 * own job, never behind the mutation lock — and the questions it asks before a change or a command.
 * Settings and the turn's [AssistantChats] are its stores; [AssistantEngine] answers.
 */
internal class AssistantCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val scope: CoroutineScope,
    private val engine: AssistantEngine,
    private val providers: AssistantProviderStore,
    private val chats: AssistantChats,
    /** The tools a turn may use, made for the workspace it works in ([AssistantToolbox.forWorkspace]). */
    private val tools: (AssistantWorkspace) -> List<AssistantTool>,
    /** The open workspace's location; null on the home screen. */
    private val activeRoot: () -> String?,
    /** A workspace by its location: its name and runtime path. Throws when it is gone. */
    private val describe: (String) -> WorkspaceRoot,
) {
    private val lock = Any()
    private var turn: Job? = null
    /** The call running now: what an approval is about. */
    private var calling: AssistantCall? = null
    private var pending: CompletableDeferred<AssistantAnswer>? = null

    /** The settings and the engine's suggestions, read once the backend starts. */
    fun start() {
        scope.launch { state { it.copy(settings = providers.settings(), suggestions = engine.suggestions) } }
    }

    fun handle(intent: WorkbenchIntent.AssistantIntent) {
        when (intent) {
            WorkbenchIntent.OpenAssistant -> scope.launch { show(activeRoot()) }
            is WorkbenchIntent.SendAssistantMessage -> send(intent.text)
            WorkbenchIntent.StopAssistant -> synchronized(lock) { turn }?.cancel()
            is WorkbenchIntent.AnswerAssistant -> answer(intent.callId, intent.answer)
            WorkbenchIntent.NewAssistantChat -> scope.launch { newChat() }
            is WorkbenchIntent.SaveAssistantSettings -> scope.launch { save(intent) }
            WorkbenchIntent.RemoveAssistantSettings -> scope.launch {
                providers.clear()
                state { it.copy(settings = null) }
            }
        }
    }

    /** A workspace was renamed or moved: its chat follows it (the lifecycle's hook). */
    fun workspaceMoved(from: String, to: String) {
        chats.moved(from, to)
        state { if (it.workspace == from) it.copy(workspace = to) else it }
    }

    /** A workspace was deleted: its chat goes with it. */
    fun workspaceForgotten(location: String) {
        chats.forgotten(location)
        state { if (it.workspace == location) it.copy(workspace = null, messages = emptyList(), allowAll = false) else it }
    }

    private fun show(workspace: String?) {
        val messages = workspace?.let(chats::of).orEmpty()
        state { it.copy(workspace = workspace, messages = messages, allowAll = workspace != null && chats.allowsAll(workspace)) }
    }

    private fun send(text: String) {
        val input = text.trim().ifEmpty { return }
        val workspace = workbench.current.assistant.workspace ?: activeRoot()
            ?: return say("Open a workspace to ask the assistant")
        synchronized(lock) {
            // The job, not the page: a second Send in the same instant finds the first one's turn.
            if (turn?.isActive == true) {
                val running = workbench.current.assistant.turn?.workspaceName ?: AssistantRules.workspaceName(workspace)
                return say("The assistant is working in $running: stop it, or wait")
            }
            turn = scope.launch { converse(workspace, input) }
        }
    }

    private suspend fun converse(workspace: String, input: String) {
        var failure: String? = null
        try {
            val connection = providers.connection() ?: throw AssistantTurnException("Set up the assistant first: its settings name a provider and a model")
            val root = runCatching { describe(workspace) }.getOrElse { throw AssistantTurnException("This workspace no longer exists") }
            val place = AssistantWorkspace(File(root.location), root.displayPath)
            val history = AssistantRules.closed(chats.of(workspace))
            chats.append(workspace, AssistantMessage.User(input))
            state { it.withChat(workspace).copy(turn = AssistantTurn(workspace, root.name, THINKING), streaming = "", failure = null) }
            val asking = tools(place).map { Asking(it, workspace) }
            engine.run(AssistantTurnRequest(connection, AssistantInstructions.of(place), history, input, asking, MAX_STEPS), Events(workspace))
        } catch (stopped: CancellationException) {
            // Stopped by the user or with the backend: what was said and done stays.
        } catch (refused: AssistantTurnException) {
            failure = refused.message
        } catch (unexpected: Exception) {
            // Its type only: the message of an unexpected failure may quote the chat.
            BackendLog.w(TAG, "A turn failed (${unexpected.javaClass.simpleName})")
            failure = "The assistant stopped: ${unexpected.message ?: unexpected.javaClass.simpleName}"
        } finally {
            withContext(NonCancellable) {
                chats.close(workspace)
                synchronized(lock) {
                    turn = null
                    calling = null
                    pending = null
                }
                state { shown ->
                    shown.withChat(workspace).copy(turn = null, approval = null, streaming = "", failure = if (shown.workspace == workspace) failure else null)
                }
                if (failure != null && workbench.current.assistant.workspace != workspace) say("Assistant: $failure")
            }
        }
    }

    private fun answer(callId: String, answer: AssistantAnswer) {
        if (workbench.current.assistant.approval?.call?.id != callId) return
        synchronized(lock) { pending }?.complete(answer)
    }

    private fun newChat() {
        val workspace = workbench.current.assistant.workspace ?: return
        if (workbench.current.assistant.turn?.workspace == workspace) return say("Stop the assistant before starting a new chat")
        chats.clear(workspace)
        state { it.withChat(workspace).copy(failure = null) }
    }

    private suspend fun save(intent: WorkbenchIntent.SaveAssistantSettings) {
        val saved = providers.settings()
        val keySaved = saved?.hasKey == true && saved.kind == intent.kind
        AssistantRules.settingsProblem(intent.kind, intent.baseUrl, intent.model, !intent.key.isNullOrBlank(), keySaved)?.let {
            return workbench.message(it)
        }
        val stored = runCatching {
            providers.save(intent.kind, AssistantRules.storedBaseUrl(intent.kind, intent.baseUrl), intent.model, intent.key, intent.askBeforeChanges)
        }
        stored.exceptionOrNull()?.let { return workbench.message(it.message ?: "Unable to save the assistant's settings") }
        state { it.copy(settings = providers.settings()) }
        workbench.message("Assistant settings saved")
    }

    /** The shown chat follows [workspace]'s, when it is the one shown. */
    private fun AssistantState.withChat(workspace: String): AssistantState =
        if (this.workspace != workspace) this else copy(messages = chats.of(workspace), allowAll = chats.allowsAll(workspace))

    private fun state(change: (AssistantState) -> AssistantState) = workbench.update { it.copy(assistant = change(it.assistant)) }

    private fun step(text: String) = state { it.copy(turn = it.turn?.copy(step = text)) }

    private fun say(text: String) {
        scope.launch { workbench.message(text) }
    }

    /** What a turn reports, onto the page; the text a few times a second rather than at every word. */
    private inner class Events(private val workspace: String) : AssistantEvents {
        private val written = StringBuilder()
        private var shownAt = 0L

        override fun text(delta: String) {
            written.append(delta)
            val now = System.nanoTime()
            if (now - shownAt < STREAM_INTERVAL_NANOS) return
            shownAt = now
            state { it.copy(streaming = written.toString()) }
        }

        override fun replied(reply: AssistantMessage.Reply) {
            written.clear()
            chats.append(workspace, reply)
            // One update: the reply never shows twice, as streamed and as said.
            state { it.withChat(workspace).copy(streaming = "") }
        }

        override fun calling(call: AssistantCall) {
            synchronized(lock) { calling = call }
            step(call.summary)
        }

        override fun answered(results: AssistantMessage.Results) {
            synchronized(lock) { calling = null }
            chats.append(workspace, results)
            state { it.withChat(workspace).copy(turn = it.turn?.copy(step = THINKING)) }
        }
    }

    /** A tool that asks first when it changes something or runs a command (§4.4). */
    private inner class Asking(private val tool: AssistantTool, private val workspace: String) : AssistantTool by tool {
        override suspend fun run(arguments: JSONObject): String {
            val asks = tool.kind != AssistantToolKind.Read &&
                workbench.current.assistant.settings?.askBeforeChanges != false && !chats.allowsAll(workspace)
            if (asks) {
                val call = synchronized(lock) { calling } ?: throw AssistantToolException("Nothing to ask about")
                when (ask(call, tool.detail(arguments))) {
                    AssistantAnswer.Allow -> Unit
                    AssistantAnswer.AllowAll -> {
                        chats.allowAll(workspace)
                        state { it.withChat(workspace) }
                    }
                    AssistantAnswer.Deny -> throw AssistantToolException(
                        if (tool.kind == AssistantToolKind.Command) "The user did not allow this command" else "The user did not allow this change",
                    )
                }
            }
            return tool.run(arguments)
        }

        private suspend fun ask(call: AssistantCall, detail: String): AssistantAnswer {
            val answer = CompletableDeferred<AssistantAnswer>()
            synchronized(lock) { pending = answer }
            state { it.copy(approval = AssistantApproval(call, detail), turn = it.turn?.copy(step = WAITING)) }
            try {
                return answer.await()
            } finally {
                synchronized(lock) { pending = null }
                state { it.copy(approval = null, turn = it.turn?.copy(step = call.summary)) }
            }
        }
    }

    companion object {
        /** The assistant on Koog, its settings in preferences and its chats in iCode's storage. */
        fun create(
            context: Context,
            workbench: WorkbenchStateHolder,
            scope: CoroutineScope,
            toolbox: AssistantToolbox,
            activeRoot: () -> String?,
            describe: (String) -> WorkspaceRoot,
        ) = AssistantCoordinator(
            workbench, scope, KoogAssistantEngine.create(), SharedPreferencesAssistantProviderStore(context),
            AssistantChats(AssistantChatStore(File(context.applicationContext.filesDir, "assistant/chats"))),
            toolbox::forWorkspace, activeRoot, describe,
        )

        private const val TAG = "Assistant"
        private const val MAX_STEPS = 40
        private const val THINKING = "Thinking…"
        private const val WAITING = "Waiting for your answer"
        private const val STREAM_INTERVAL_NANOS = 80_000_000L
    }
}
