package dev.icode.mobile.data.assistant

import android.content.Context
import android.os.Environment
import dev.icode.mobile.data.agents.CodingAgentsCoordinator
import dev.icode.mobile.data.assistant.acp.AgentChats
import dev.icode.mobile.data.assistant.koog.KoogAssistantEngine
import dev.icode.mobile.data.assistant.mcp.McpServers
import dev.icode.mobile.data.assistant.tools.AssistantToolbox
import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.runtime.GuestPaths
import dev.icode.mobile.data.runtime.RuntimePackageInstaller
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceFileSource
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AgentChatIntent
import dev.icode.mobile.domain.AgentIntent
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantDraftFile
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.McpIntent
import dev.icode.mobile.domain.ResumeAssistantChat
import dev.icode.mobile.domain.UndoIntent
import dev.icode.mobile.domain.notices.AssistantNotices
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * The assistant (docs/assistant-and-mcp.md): its intents, its settings, and its workspaces'
 * lifecycle. The page shows ([AssistantPage]), the turns work ([AssistantTurns]); settings and
 * [AssistantChats] are its stores, and [AssistantEngine] answers.
 */
internal class AssistantCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val engine: AssistantEngine,
    private val providers: AssistantProviderStore,
    private val chats: AssistantChats,
    /** The tools a turn may use, made for the place it works in and the step it is on ([AssistantToolbox.forWorkspace]). */
    tools: (AssistantWorkspace, chat: String, step: AssistantStep) -> List<AssistantTool>,
    /** The open workspace's location; null on the home screen. */
    private val activeRoot: () -> String?,
    /** A workspace by its location: its name and runtime path. Throws when it is gone. */
    describe: (String) -> WorkspaceRoot,
    /** The runtime's paths, as the tools follow them. */
    paths: GuestPaths,
    /** The files of a message: the workspace's and the phone's ([AssistantAttachments]). */
    attachments: (openText: (String) -> String?) -> AssistantAttachments,
    /** Git, for a workspace, in the repository it works in: what a commit message is written from. */
    git: suspend (workspace: String, arguments: List<String>) -> CliGitService.CommandResult,
    /** All Files Access, as Android answers it now: phone storage is read with it (docs/voice-and-phone-control.md). */
    storageAccess: () -> Boolean,
    /** The MCP servers, whose tools join a turn's after iCode's own (docs/assistant-and-mcp.md); none in tests that need none. */
    private val mcp: McpServers? = null,
    /** The coding agents' installs (docs/assistant-and-mcp.md); none in tests that need none. */
    private val agents: CodingAgentsCoordinator? = null,
    /** A chat deleted: what it left in the runtime goes, its terminal tabs ([AssistantToolbox.forget]). */
    private val forget: suspend (workspace: String?, chat: String) -> Unit = { _, _ -> },
    /** A chat's own terminal tab, opened if it has none ([AssistantToolbox.openTab]); throws with why it cannot be. */
    private val openTab: suspend (workspace: String?, chat: String) -> String = { _, _ -> error("No terminal") },
    /** Where a shared chat's transcript is written for the share sheet: in the cache, only the latest kept. */
    shares: File = File(System.getProperty("java.io.tmpdir"), "assistant-share"),
    /** Where the places' checkpoints are kept for Undo ([AssistantCheckpoints]); none in tests that need none. */
    checkpoints: File? = null,
    /** The coding agents in the chats, made over the page and the chats (docs/assistant-and-mcp.md); none in tests that need none. */
    agentChatsOf: ((AssistantPage, AssistantChats, (String?) -> AssistantWorkspace) -> AgentChats)? = null,
) {
    private val checkpointing = checkpoints?.let(::AssistantCheckpoints)
    private val page = AssistantPage(stateHolder, scope, chats)
    private val placeOf = { workspace: String? -> AssistantWorkspace(workspace?.let(::File), paths) }
    private val agentChats = agentChatsOf?.invoke(page, chats, placeOf)
    private val settings = AssistantProviders(stateHolder, scope, engine, providers, page)
    private val commitMessages = AssistantCommitMessages(scope, engine, providers, settings::settings, page, git)
    private val questions = AssistantQuestions(page)
    private val queue: AssistantQueue = AssistantQueue(chats, page) { turns }
    private val turns: AssistantTurns = AssistantTurns(
        page, scope, engine, providers, settings::settings, chats, AssistantTools.of(tools, mcp), describe, paths,
        attachments { location -> stateHolder.current.openDocuments.firstOrNull { it.location == location }?.text },
        storageAccess, questions, queue, settings::seesNoPictures, checkpointing, agentChats,
    )
    private val actions = AssistantChatActions(
        stateHolder, scope, page, chats, turns, AssistantShares(shares), checkpointing, placeOf, forget,
    )

    /**
     * The intents in the order they were made: "New chat" then Send is never sent in the chat before.
     * Nothing here waits on a turn — a turn is its own job — so Stop and an answer are never held up.
     */
    private val intents = Channel<ICodeIntent.AssistantIntent>(Channel.UNLIMITED)

    /** The settings and the engine's catalogue, read once the backend starts; then the intents, in order. */
    fun start() {
        scope.launch {
            val catalogue = engine.catalogue
            settings.shown()
            page.state { it.copy(catalogue = catalogue) }
            mcp?.start()
            agents?.start()
            agentChats?.start()
            for (intent in intents) apply(intent)
        }
    }

    fun handle(intent: ICodeIntent.AssistantIntent) { intents.trySend(intent) }

    private suspend fun apply(intent: ICodeIntent.AssistantIntent) {
        when (intent) {
            is ICodeIntent.OpenAssistant -> {
                // Inside the workspace its chats — with none open, those shown, so the page and the bubble stay
                // in the chat the user was in; outside it, the chats in no workspace (docs/design-and-navigation.md).
                page.show(if (intent.inWorkspace) activeRoot() ?: page.current.workspace else null)
                mcp?.handle(McpIntent.Connect)
            }
            is ICodeIntent.OpenAssistantSession -> page.display(intent.workspace, intent.id)
            is ICodeIntent.DeleteAssistantSession -> actions.delete(intent.id)
            is ICodeIntent.ExportAssistantSession -> actions.export(intent.id)
            ICodeIntent.NewAssistantChat -> page.display(here(), null)
            is ICodeIntent.SendAssistantMessage -> send(intent.text, intent.attachments, intent.aloud)
            is ICodeIntent.SendQueuedAssistantMessage -> queue.sendNow(intent.id)
            is ICodeIntent.RemoveQueuedAssistantMessage -> queue.remove(intent.id)
            is ICodeIntent.StopAssistant -> turns.stop(intent.workspace, intent.session)
            is ICodeIntent.AnswerAssistant -> questions.answer(intent.workspace, intent.session, intent.callId, intent.answer, intent.option)
            is ICodeIntent.ReplyAssistant -> questions.reply(intent.workspace, intent.session, intent.callId, intent.reply)
            is ICodeIntent.ContinueAssistant -> questions.resume(intent.workspace, intent.session)
            ResumeAssistantChat -> actions.resume()
            ICodeIntent.CompactAssistantChat -> actions.compact()
            ICodeIntent.OpenAssistantTerminal -> openTerminal()
            ICodeIntent.RetryAssistantMessage -> actions.retry()
            ICodeIntent.TakeBackAssistantMessage -> actions.takeBack()
            is UndoIntent.Message -> actions.undo(intent.index, intent.files, intent.chat, intent.confirmed)
            UndoIntent.Dismiss -> actions.dismissUndo()
            UndoIntent.Taken -> page.state { it.copy(takenBack = null) }
            is ICodeIntent.ChooseAssistantModel -> choose(AssistantChoice(intent.provider, intent.model))
            is ICodeIntent.ChooseAssistantEffort -> settings.remember { providers.chooseEffort(intent.effort) }
            is ICodeIntent.ChooseAssistantMode -> settings.remember { providers.chooseMode(intent.mode) }
            is ICodeIntent.SetAssistantPermission -> setPermission(intent)
            is ICodeIntent.EditAssistantProvider -> settings.edit(intent.id, intent.kind)
            is ICodeIntent.SaveAssistantProvider -> settings.save(intent)
            is ICodeIntent.RemoveAssistantProvider -> settings.remove(intent.id)
            is ICodeIntent.ListAssistantModels -> settings.list(intent)
            ICodeIntent.WriteCommitMessage -> activeRoot()?.let(commitMessages::write) ?: page.tell(AssistantNotices.openWorkspaceFirst())
            ICodeIntent.CommitMessageTaken -> commitMessages.taken()
            is ICodeIntent.ChooseCommitModel -> settings.remember { providers.chooseCommitModel(intent.choice) }
            is McpIntent -> mcp?.handle(intent)
            is AgentIntent -> agents?.handle(intent)
            is AgentChatIntent -> agentChats?.handle(intent)
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
        checkpointing?.moved(from, to)
        if (page.current.workspace == from) page.display(to, page.current.session)
    }

    /** A workspace was deleted: its chats go with it, after any turn started there since the lifecycle's check. */
    suspend fun workspaceForgotten(location: String) {
        turns.stopIn(location)
        chats.forgotten(location)
        checkpointing?.forgotten(location)
        page.forgotten(location)
    }

    private fun send(text: String, files: List<AssistantDraftFile>, aloud: Boolean) {
        // A photo alone is a message, as it is in any chat; nothing at all is not.
        val input = text.trim()
        if (input.isEmpty() && files.isEmpty()) return
        val shown = page.current
        val workspace = here()
        val onPage = shown.opened && shown.workspace == workspace
        val session = shown.session?.takeIf { onPage } ?: chats.newSession().also { created ->
            // A new chat on the page is this one from now, so a second Send finds it at work; it is listed with its first message.
            if (onPage) page.state { it.copy(session = created) }
        }
        // Sent while the turn there asks its questions: the reply, not a message waiting for the work to end.
        if (files.isEmpty() && questions.replyWith(workspace, session, input)) return
        // Read aloud as Spoken replies says: decided once, so the turn's instructions and its voice agree.
        queue.send(workspace, session, input, files, stateHolder.current.voice.spoken.speaks(aloud))
    }

    /**
     * The workspace whose chats a message goes to, null for the chats in none: the page's, so the
     * bubble and "Open in iCode" stay in the chat the user was in; before the page has opened, the open
     * workspace's, else the chats in no workspace (docs/design-and-navigation.md).
     */
    private fun here(): String? = if (page.current.opened) page.current.workspace else activeRoot()

    /** The shown chat's own tab — a new chat given its ID, as sending would — for the menu's Terminal sheet. */
    private fun openTerminal() {
        val workspace = here()
        val session = page.current.session?.takeIf { page.current.opened && page.current.workspace == workspace } ?: chats.newSession().also { page.display(workspace, it) }
        scope.launch {
            runCatching { openTab(workspace, session) }
                .onSuccess { id -> page.state { if (it.opened && it.workspace == workspace && it.session == session) it.copy(tab = id) else it } }
                .onFailure { page.tell(AssistantNotices.terminalNotOpened(it.message ?: it.javaClass.simpleName)) }
        }
    }

    /**
     * [choice] answers the chat shown from its next message on — the conversation so far goes with it
     * (docs/assistant-and-mcp.md) — and is what a new chat starts with.
     */
    private fun choose(choice: AssistantChoice) {
        settings.choose(choice)
        agentChats?.modelChosen()
        val shown = page.current
        // Only a model its provider offers: one it does not list is not chosen, for the chat or for new ones.
        if (shown.opened && shown.session != null && shown.settings.modelOf(choice) != null) {
            chats.choose(shown.workspace, shown.session, choice)
            page.refresh(shown.workspace, shown.session)
        }
    }

    /** The mode chosen in the chip: the setting, and the chat shown's, whose own answers it replaces. */
    private fun setPermission(intent: ICodeIntent.SetAssistantPermission) {
        settings.remember { providers.setPermission(intent.permission) }
        val shown = page.current
        if (shown.opened && shown.session != null) {
            chats.forgetAnswers(shown.workspace, shown.session)
            page.refresh(shown.workspace, shown.session)
        }
    }

    companion object {
        /**
         * The assistant on Koog, its settings in preferences, its chats in iCode's storage, its files
         * [source]'s and the phone's, its commit messages written from what [git] reads.
         */
        fun create(
            context: Context,
            icode: ICodeStateHolder,
            scope: CoroutineScope,
            toolbox: AssistantToolbox,
            activeRoot: () -> String?,
            source: WorkspaceFileSource,
            paths: GuestPaths,
            git: CliGitService,
            packages: RuntimePackageInstaller,
            /** An open file saved, as Save does: before a message goes to an agent. */
            save: suspend (String) -> Boolean,
        ) = AssistantCoordinator(
            icode, scope, KoogAssistantEngine.create(), SharedPreferencesAssistantProviderStore(context),
            AssistantChats(AssistantChatStore(File(context.applicationContext.filesDir, "assistant/chats"))),
            toolbox::forWorkspace, activeRoot, source::describeRoot, paths,
            attachments = { openText ->
                AssistantAttachments(
                    ContentResolverPhoneFiles(context.applicationContext.contentResolver),
                    AssistantImages::prepare,
                    openText,
                    readText = { location -> source.readDocument(location).text },
                )
            },
            git = { workspace, arguments -> git.run(workspace, arguments) },
            storageAccess = Environment::isExternalStorageManager,
            mcp = McpServers.create(context, icode, scope, toolbox.execution, toolbox.runtimeInstalled, activeRoot),
            agents = CodingAgentsCoordinator.create(context, icode, scope, packages),
            forget = toolbox::forget,
            openTab = toolbox::openTab,
            shares = File(context.applicationContext.cacheDir, "assistant-share"),
            checkpoints = File(context.applicationContext.filesDir, "assistant/checkpoints"),
            agentChatsOf = AgentChats.maker(context, icode, scope, toolbox, save),
        )
    }
}
