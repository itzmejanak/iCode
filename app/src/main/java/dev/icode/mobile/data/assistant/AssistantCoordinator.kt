package dev.icode.mobile.data.assistant

import android.content.Context
import dev.icode.mobile.data.assistant.koog.KoogAssistantEngine
import dev.icode.mobile.data.assistant.tools.AssistantToolbox
import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.data.workspace.WorkspaceFileSource
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.WorkbenchIntent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * The assistant (docs/assistant-koog-plan.md): its intents, its settings, and its workspaces'
 * lifecycle. The page shows ([AssistantPage]), the turns work ([AssistantTurns]); settings and
 * [AssistantChats] are its stores, and [AssistantEngine] answers.
 */
internal class AssistantCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val scope: CoroutineScope,
    private val engine: AssistantEngine,
    private val providers: AssistantProviderStore,
    private val chats: AssistantChats,
    /** The tools a turn may use, made for the workspace it works in ([AssistantToolbox.forWorkspace]). */
    tools: (AssistantWorkspace) -> List<AssistantTool>,
    /** The open workspace's location; null on the home screen. */
    private val activeRoot: () -> String?,
    /** A workspace by its location: its name and runtime path. Throws when it is gone. */
    describe: (String) -> WorkspaceRoot,
    /** A file's text as saved. */
    readText: (String) -> String,
) {
    private val page = AssistantPage(workbench, scope, chats)
    private val turns = AssistantTurns(
        page, scope, engine, providers, chats, tools, describe, readText,
        openText = { location -> workbench.current.openDocuments.firstOrNull { it.location == location }?.text },
    )

    /**
     * The intents in the order they were made: "New chat" then Send is never sent in the chat before.
     * Nothing here waits on a turn — a turn is its own job — so Stop and an answer are never held up.
     */
    private val intents = Channel<WorkbenchIntent.AssistantIntent>(Channel.UNLIMITED)

    /** The settings and the engine's catalogue, read once the backend starts; then the intents, in order. */
    fun start() {
        scope.launch {
            val catalogue = engine.catalogue
            settingsChanged()
            page.state { it.copy(catalogue = catalogue) }
            for (intent in intents) apply(intent)
        }
    }

    fun handle(intent: WorkbenchIntent.AssistantIntent) {
        intents.trySend(intent)
    }

    private suspend fun apply(intent: WorkbenchIntent.AssistantIntent) {
        when (intent) {
            WorkbenchIntent.OpenAssistant -> page.show(activeRoot(), turns.running)
            is WorkbenchIntent.OpenAssistantSession -> page.current.workspace?.let { page.display(it, intent.id) }
            is WorkbenchIntent.DeleteAssistantSession -> delete(intent.id)
            WorkbenchIntent.NewAssistantChat -> (page.current.workspace ?: activeRoot())?.let { page.display(it, null) }
            is WorkbenchIntent.SendAssistantMessage -> send(intent.text, intent.attachments)
            WorkbenchIntent.StopAssistant -> turns.stop()
            is WorkbenchIntent.AnswerAssistant -> turns.answer(intent.callId, intent.answer)
            WorkbenchIntent.CompactAssistantChat -> compact()
            is WorkbenchIntent.ChooseAssistantModel -> remember { providers.chooseModel(intent.model) }
            is WorkbenchIntent.ChooseAssistantEffort -> remember { providers.chooseEffort(intent.effort) }
            is WorkbenchIntent.ChooseAssistantMode -> remember { providers.chooseMode(intent.mode) }
            is WorkbenchIntent.SetAssistantAskFirst -> remember { providers.setAskFirst(intent.ask) }
            is WorkbenchIntent.SaveAssistantSettings -> save(intent)
            WorkbenchIntent.RemoveAssistantSettings -> remember { providers.clear() }
        }
    }

    /**
     * A workspace was renamed or moved: its chats follow it (the lifecycle's hook). The lifecycle
     * refuses while the assistant works there ([dev.icode.mobile.data.workspace.WorkspaceUsage]); a
     * turn started since that check is stopped, and its chat saved, before the chats move.
     */
    suspend fun workspaceMoved(from: String, to: String) {
        turns.stopIn(from)
        chats.moved(from, to)
        if (page.current.workspace == from) page.display(to, page.current.session)
    }

    /** A workspace was deleted: its chats go with it, after any turn started there since the lifecycle's check. */
    suspend fun workspaceForgotten(location: String) {
        turns.stopIn(location)
        chats.forgotten(location)
        page.forgotten(location)
    }

    private fun send(text: String, attachments: List<String>) {
        val input = text.trim().ifEmpty { return }
        val shown = page.current
        val workspace = shown.workspace ?: activeRoot() ?: return page.say("Open a workspace to ask the assistant")
        val session = shown.session?.takeIf { shown.workspace == workspace } ?: chats.newSession()
        turns.send(workspace, session, input, attachments)
    }

    private fun compact() {
        val workspace = page.current.workspace ?: return
        val session = page.current.session ?: return
        if (AssistantRules.forModel(chats.of(workspace, session).messages).messages.isEmpty()) return page.say("Nothing to compact yet")
        turns.compact(workspace, session)
    }

    private fun delete(session: String) {
        val workspace = page.current.workspace ?: return
        if (turns.running == (workspace to session)) return page.say("Stop the assistant before deleting this chat")
        chats.delete(workspace, session)
        if (page.current.session == session) page.display(workspace, null) else page.listed(workspace)
    }

    private suspend fun save(intent: WorkbenchIntent.SaveAssistantSettings) {
        val saved = providers.settings()
        val keySaved = saved?.hasKey == true && saved.kind == intent.kind
        AssistantRules.settingsProblem(
            intent.kind, intent.baseUrl, intent.model, !intent.key.isNullOrBlank(), keySaved, intent.contextWindow, intent.maxOutput,
        )?.let { return workbench.message(it) }
        val stored = runCatching {
            providers.save(
                intent.kind, AssistantRules.storedBaseUrl(intent.kind, intent.baseUrl), intent.model, intent.key,
                AssistantRules.tokens(intent.contextWindow), AssistantRules.tokens(intent.maxOutput),
            )
        }
        stored.exceptionOrNull()?.let { return workbench.message(it.message ?: "Unable to save the assistant's settings") }
        settingsChanged()
        workbench.message("Assistant settings saved")
    }

    /** A choice made on the chat page: kept, then shown. */
    private suspend fun remember(change: () -> Unit) {
        runCatching(change).exceptionOrNull()?.let { return workbench.message(it.message ?: "Unable to save the assistant's settings") }
        settingsChanged()
    }

    /** The settings as stored, and the chosen model as the engine knows it, with the user's limits. */
    private fun settingsChanged() {
        val settings = providers.settings()
        val model = settings?.let { engine.model(it.kind, it.model, it.contextWindow, it.maxOutput) }
        val recent = providers.recentModels()
        page.state { it.copy(settings = settings, model = model, recentModels = recent) }
    }

    companion object {
        /** The assistant on Koog, its settings in preferences, its chats in iCode's storage, its files [source]'s. */
        fun create(
            context: Context,
            workbench: WorkbenchStateHolder,
            scope: CoroutineScope,
            toolbox: AssistantToolbox,
            activeRoot: () -> String?,
            source: WorkspaceFileSource,
        ) = AssistantCoordinator(
            workbench, scope, KoogAssistantEngine.create(), SharedPreferencesAssistantProviderStore(context),
            AssistantChats(AssistantChatStore(File(context.applicationContext.filesDir, "assistant/chats"))),
            toolbox::forWorkspace, activeRoot, source::describeRoot, readText = { location -> source.readDocument(location).text },
        )
    }
}
