package dev.icode.mobile.data.assistant

import android.content.Context
import android.os.Environment
import dev.icode.mobile.data.assistant.koog.KoogAssistantEngine
import dev.icode.mobile.data.assistant.mcp.McpServers
import dev.icode.mobile.data.assistant.tools.AssistantToolbox
import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.runtime.GuestPaths
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceFileSource
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantExports
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSession
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.McpIntent
import dev.icode.mobile.domain.notices.AssistantNotices
import dev.icode.mobile.domain.notices.NoticePlace
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
    /** A chat deleted: what it left in the runtime goes, its terminal tabs ([AssistantToolbox.forget]). */
    private val forget: suspend (workspace: String?, chat: String) -> Unit = { _, _ -> },
    /** A chat's own terminal tab, opened if it has none ([AssistantToolbox.openTab]); throws with why it cannot be. */
    private val openTab: suspend (workspace: String?, chat: String) -> String = { _, _ -> error("No terminal") },
) {
    private val page = AssistantPage(stateHolder, scope, chats)
    private val settings = AssistantProviders(stateHolder, scope, engine, providers, page)
    private val commitMessages = AssistantCommitMessages(scope, engine, providers, settings::settings, page, git)
    private val questions = AssistantQuestions(page)
    private val turns = AssistantTurns(
        page, scope, engine, providers, settings::settings, chats, AssistantTools.of(tools, mcp), describe, paths,
        attachments { location -> stateHolder.current.openDocuments.firstOrNull { it.location == location }?.text },
        storageAccess,
        questions,
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
            for (intent in intents) apply(intent)
        }
    }

    fun handle(intent: ICodeIntent.AssistantIntent) {
        intents.trySend(intent)
    }

    private suspend fun apply(intent: ICodeIntent.AssistantIntent) {
        when (intent) {
            is ICodeIntent.OpenAssistant -> {
                // Inside the workspace its chats — with none open, those shown, so the page and the bubble stay
                // in the chat the user was in; outside it, the chats in no workspace (docs/design-and-navigation.md).
                page.show(if (intent.inWorkspace) activeRoot() ?: page.current.workspace else null, turns.running)
                mcp?.handle(McpIntent.Connect)
            }
            is ICodeIntent.OpenAssistantSession -> if (page.current.opened) page.display(page.current.workspace, intent.id)
            is ICodeIntent.DeleteAssistantSession -> delete(intent.id)
            is ICodeIntent.ExportAssistantSession -> export(intent.id)
            ICodeIntent.NewAssistantChat -> page.display(here(), null)
            is ICodeIntent.SendAssistantMessage -> send(intent.text, intent.attachments, intent.aloud)
            ICodeIntent.StopAssistant -> turns.stop()
            is ICodeIntent.AnswerAssistant -> questions.answer(intent.callId, intent.answer)
            is ICodeIntent.ReplyAssistant -> questions.reply(intent.callId, intent.reply)
            ICodeIntent.ContinueAssistant -> questions.resume()
            ICodeIntent.CompactAssistantChat -> compact()
            ICodeIntent.OpenAssistantTerminal -> openTerminal()
            ICodeIntent.RetryAssistantMessage -> retry()
            ICodeIntent.TakeBackAssistantMessage -> takeBack()
            is ICodeIntent.ChooseAssistantModel -> settings.choose(AssistantChoice(intent.provider, intent.model))
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

    private fun send(text: String, attachments: List<String>, aloud: Boolean) {
        // A photo alone is a message, as it is in any chat; nothing at all is not.
        val input = text.trim()
        if (input.isEmpty() && attachments.isEmpty()) return
        val shown = page.current
        val workspace = here()
        val session = shown.session?.takeIf { shown.opened && shown.workspace == workspace } ?: chats.newSession()
        // Sent while the turn there asks its questions: the reply, not a new turn refused as working.
        if (attachments.isEmpty() && questions.replyWith(workspace, session, input)) return
        // Read aloud as Spoken replies says: decided once, so the turn's instructions and its voice agree.
        turns.send(workspace, session, input, attachments, stateHolder.current.voice.spoken.speaks(aloud))
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

    /** The mode chosen in the chip: the setting, and the chat shown's, whose own answers it replaces. */
    private fun setPermission(intent: ICodeIntent.SetAssistantPermission) {
        settings.remember { providers.setPermission(intent.permission) }
        val shown = page.current
        if (shown.opened && shown.session != null) {
            chats.forgetAnswers(shown.workspace, shown.session)
            page.refresh(shown.workspace, shown.session)
        }
    }

    private fun retry() {
        if (!page.current.opened) return
        val workspace = page.current.workspace
        val session = page.current.session ?: return
        if (chats.of(workspace, session).messages.lastOrNull() !is AssistantMessage.User) return page.tell(AssistantNotices.nothingToSendAgain())
        turns.retry(workspace, session)
    }

    /** The last message, unanswered, out of the chat for the user to edit; a chat left empty is a new one. */
    private fun takeBack() {
        if (!page.current.opened) return
        val workspace = page.current.workspace
        val session = page.current.session ?: return
        if (turns.running != null) return
        chats.takeBack(workspace, session) ?: return
        page.state { it.copy(failure = null) }
        if (chats.of(workspace, session).messages.isEmpty()) page.display(workspace, null) else page.refresh(workspace, session)
        page.listed(workspace)
    }

    private fun compact() {
        if (!page.current.opened) return
        val workspace = page.current.workspace
        val session = page.current.session ?: return
        if (AssistantRules.forModel(chats.of(workspace, session).messages).messages.isEmpty()) return page.tell(AssistantNotices.nothingToCompact())
        turns.compact(workspace, session)
    }

    private fun delete(session: String) {
        if (!page.current.opened) return
        val workspace = page.current.workspace
        if (turns.running == (workspace to session)) return page.tell(AssistantNotices.stopFirst(NoticePlace(AssistantPlace.name(workspace), workspace)))
        chats.delete(workspace, session)
        scope.launch { forget(workspace, session) }
        if (page.current.session == session) page.display(workspace, null) else page.listed(workspace)
    }

    /** One chat, shared as its transcript: the user's words, the answers, the steps and their output. */
    private fun export(session: String) {
        if (!page.current.opened) return
        val workspace = page.current.workspace
        val chat = chats.of(workspace, session)
        if (chat.messages.isEmpty()) return page.tell(AssistantNotices.nothingToShare())
        val listed = page.current.sessions.firstOrNull { it.id == session }
        val title = listed?.title ?: AssistantRules.sessionTitle(
            chat.messages.firstNotNullOfOrNull { it as? AssistantMessage.User }?.text.orEmpty(),
        )
        scope.launch { stateHolder.emit(ICodeEffect.ShareChat(title, AssistantExports.transcript(AssistantSession(session, title, listed?.updatedAt ?: 0L), AssistantPlace.name(workspace), chat.messages))) }
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
            forget = toolbox::forget,
            openTab = toolbox::openTab,
        )
    }
}
