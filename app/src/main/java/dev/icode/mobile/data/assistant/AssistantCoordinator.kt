package dev.icode.mobile.data.assistant

import android.content.Context
import dev.icode.mobile.data.assistant.koog.KoogAssistantEngine
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
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSession
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.NoticeTone
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
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val engine: AssistantEngine,
    private val providers: AssistantProviderStore,
    private val chats: AssistantChats,
    /** The tools a turn may use, made for the workspace it works in ([AssistantToolbox.forWorkspace]). */
    tools: (AssistantWorkspace, chat: String) -> List<AssistantTool>,
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
) {
    private val page = AssistantPage(stateHolder, scope, chats)
    private val settings = AssistantProviders(stateHolder, scope, engine, providers, page)
    private val commitMessages = AssistantCommitMessages(scope, engine, providers, settings::settings, page, git)
    private val turns = AssistantTurns(
        page, scope, engine, providers, settings::settings, chats, tools, describe, paths,
        attachments { location -> stateHolder.current.openDocuments.firstOrNull { it.location == location }?.text },
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
            for (intent in intents) apply(intent)
        }
    }

    fun handle(intent: ICodeIntent.AssistantIntent) {
        intents.trySend(intent)
    }

    private suspend fun apply(intent: ICodeIntent.AssistantIntent) {
        when (intent) {
            ICodeIntent.OpenAssistant -> page.show(activeRoot(), turns.running)
            is ICodeIntent.OpenAssistantSession -> page.current.workspace?.let { page.display(it, intent.id) }
            is ICodeIntent.DeleteAssistantSession -> delete(intent.id)
            is ICodeIntent.ExportAssistantSession -> export(intent.id)
            ICodeIntent.NewAssistantChat -> (page.current.workspace ?: activeRoot())?.let { page.display(it, null) }
            is ICodeIntent.SendAssistantMessage -> send(intent.text, intent.attachments)
            ICodeIntent.StopAssistant -> turns.stop()
            is ICodeIntent.AnswerAssistant -> turns.answer(intent.callId, intent.answer)
            ICodeIntent.CompactAssistantChat -> compact()
            ICodeIntent.RetryAssistantMessage -> retry()
            is ICodeIntent.ChooseAssistantModel -> settings.choose(AssistantChoice(intent.provider, intent.model))
            is ICodeIntent.ChooseAssistantEffort -> settings.remember { providers.chooseEffort(intent.effort) }
            is ICodeIntent.ChooseAssistantMode -> settings.remember { providers.chooseMode(intent.mode) }
            is ICodeIntent.SetAssistantAskFirst -> settings.remember { providers.setAskFirst(intent.ask) }
            is ICodeIntent.EditAssistantProvider -> settings.edit(intent.id)
            is ICodeIntent.SaveAssistantProvider -> settings.save(intent)
            is ICodeIntent.RemoveAssistantProvider -> settings.remove(intent.id)
            is ICodeIntent.ListAssistantModels -> settings.list(intent)
            ICodeIntent.WriteCommitMessage -> activeRoot()?.let(commitMessages::write) ?: page.say(NoticeTone.HeadsUp, "Open a workspace first")
            ICodeIntent.CommitMessageTaken -> commitMessages.taken()
            is ICodeIntent.ChooseCommitModel -> settings.remember { providers.chooseCommitModel(intent.choice) }
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
        // A photo alone is a message, as it is in any chat; nothing at all is not.
        val input = text.trim()
        if (input.isEmpty() && attachments.isEmpty()) return
        val shown = page.current
        val workspace = shown.workspace ?: activeRoot() ?: return page.say(NoticeTone.HeadsUp, "Open a workspace to ask the assistant")
        val session = shown.session?.takeIf { shown.workspace == workspace } ?: chats.newSession()
        turns.send(workspace, session, input, attachments)
    }

    private fun retry() {
        val workspace = page.current.workspace ?: return
        val session = page.current.session ?: return
        if (chats.of(workspace, session).messages.lastOrNull() !is AssistantMessage.User) return page.say(NoticeTone.HeadsUp, "Nothing to send again")
        turns.retry(workspace, session)
    }

    private fun compact() {
        val workspace = page.current.workspace ?: return
        val session = page.current.session ?: return
        if (AssistantRules.forModel(chats.of(workspace, session).messages).messages.isEmpty()) return page.say(NoticeTone.HeadsUp, "Nothing to compact yet")
        turns.compact(workspace, session)
    }

    private fun delete(session: String) {
        val workspace = page.current.workspace ?: return
        if (turns.running == (workspace to session)) return page.say(NoticeTone.HeadsUp, "Stop the assistant before deleting this chat")
        chats.delete(workspace, session)
        if (page.current.session == session) page.display(workspace, null) else page.listed(workspace)
    }

    /** One chat, shared as its transcript: the user's words, the answers, the steps and their output. */
    private fun export(session: String) {
        val workspace = page.current.workspace ?: return
        val chat = chats.of(workspace, session)
        if (chat.messages.isEmpty()) return page.say(NoticeTone.HeadsUp, "Nothing to share yet")
        val listed = page.current.sessions.firstOrNull { it.id == session }
        val title = listed?.title ?: AssistantRules.sessionTitle(
            chat.messages.firstNotNullOfOrNull { it as? AssistantMessage.User }?.text.orEmpty(),
        )
        scope.launch { stateHolder.emit(ICodeEffect.ShareChat(title, AssistantExports.transcript(AssistantSession(session, title, listed?.updatedAt ?: 0L), AssistantRules.workspaceName(workspace), chat.messages))) }
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
        )
    }
}
