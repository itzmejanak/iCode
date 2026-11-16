package dev.icode.mobile.domain

import kotlinx.coroutines.flow.first

/**
 * What the user asked that iCode is doing now (docs/design-and-navigation.md): published the moment the tap
 * is dispatched, before the work waits for anything, and gone when it is done — so a button shows it
 * is working at once, and a second tap on it does nothing. One kind of "busy" for every action; an
 * action with progress of its own (a transfer, the runtime, a task) keeps its own state beside it.
 */
data class Busy(val action: BusyAction, val subject: String? = null)

enum class BusyAction(
    /** A tap again while it runs: [Repeat.Drop] does nothing; [Repeat.Queue] runs after it (a save of newer text). */
    val repeat: Repeat = Repeat.Drop,
) {
    OpenWorkspace,
    /** A file into the editor; a second tap — another line of it, from Find — goes after it. */
    OpenFile(Repeat.Queue),
    OpenTerminal,
    /** A tab closing — a terminal session's, by its id; a document's, by its location — after its save when asked. */
    CloseTab,
    SaveDocument(Repeat.Queue),
    Draft,
    ExternalChange,
    InitializeGit,
    Stage,
    Commit,
    Branch,
    RefreshGit,
    /** A diff being read for its page: Git's, or the editor's text against the disk's. */
    OpenDiff,
    OpenConflict,
    Discard,
    Abort,
    AddRemote,
    Conflict,
    GitIdentity,
    GitToken,
    Runtime,
    ClearCache,
    RunStart,
    LanguageService,
    ;

    enum class Repeat { Drop, Queue }
}

object BusyRules {
    /** The busy [intent] makes while it runs; null for what is instant or has its own state. */
    fun of(intent: ICodeIntent): Busy? = when (intent) {
        is ICodeIntent.OpenWorkspace -> Busy(BusyAction.OpenWorkspace, intent.path)
        is ICodeIntent.OpenWorkspaceTerminal -> Busy(BusyAction.OpenWorkspace, intent.path)
        is ICodeIntent.ResolveWorkspaceSwitch -> Busy(BusyAction.OpenWorkspace, intent.path)
        is ICodeIntent.OpenFile -> Busy(BusyAction.OpenFile, intent.path)
        is ICodeIntent.OpenRecoverableDraft -> Busy(BusyAction.OpenFile, intent.location)
        is ICodeIntent.OpenTerminalSession, is ICodeIntent.EnsureTerminalSession -> Busy(BusyAction.OpenTerminal)
        is ICodeIntent.CloseTerminalSession -> Busy(BusyAction.CloseTab, intent.id)
        is ICodeIntent.CloseDocument -> Busy(BusyAction.CloseTab, intent.location)
        // An automatic save is no one's tap: nothing shows it.
        is ICodeIntent.SaveDocument -> if (intent.automatic) null else Busy(BusyAction.SaveDocument, intent.location)
        is ICodeIntent.ConfirmRecoveredOverwrite -> Busy(BusyAction.SaveDocument, intent.location)
        is ICodeIntent.RestoreDraft -> Busy(BusyAction.Draft, intent.location)
        is ICodeIntent.DiscardDraft -> Busy(BusyAction.Draft, intent.location)
        is ICodeIntent.ReloadFromDisk -> Busy(BusyAction.ExternalChange, intent.location)
        is ICodeIntent.OverwriteDisk -> Busy(BusyAction.ExternalChange, intent.location)
        is ICodeIntent.CompareWithDisk -> Busy(BusyAction.OpenDiff, intent.location)
        ICodeIntent.InitializeGit -> Busy(BusyAction.InitializeGit)
        is ICodeIntent.StageFile -> Busy(BusyAction.Stage, intent.path)
        is ICodeIntent.UnstageFile -> Busy(BusyAction.Stage, intent.path)
        ICodeIntent.StageAllFiles, ICodeIntent.UnstageAllFiles -> Busy(BusyAction.Stage, ALL)
        is StageLineChange -> Busy(BusyAction.Stage, intent.location)
        is ICodeIntent.CommitStaged -> Busy(BusyAction.Commit)
        is ICodeIntent.SwitchBranch -> Busy(BusyAction.Branch, intent.branch)
        is ICodeIntent.CreateBranch -> Busy(BusyAction.Branch, intent.branch)
        is ICodeIntent.DeleteBranch -> Busy(BusyAction.Branch, intent.branch)
        is ICodeIntent.CheckoutRemoteBranch -> Busy(BusyAction.Branch, intent.remoteBranch)
        ICodeIntent.RefreshGit -> Busy(BusyAction.RefreshGit)
        is ICodeIntent.OpenDiff -> Busy(BusyAction.OpenDiff, intent.path)
        is ICodeIntent.OpenConflict -> Busy(BusyAction.OpenConflict, intent.path)
        ICodeIntent.ConfirmDiscardChanges -> Busy(BusyAction.Discard)
        ICodeIntent.AbortInProgress -> Busy(BusyAction.Abort)
        is ICodeIntent.AddRemote -> Busy(BusyAction.AddRemote)
        is ICodeIntent.ResolveConflict -> Busy(BusyAction.Conflict, intent.path)
        // Marking it resolved stages it: its row in Source Control says so.
        is ICodeIntent.MarkConflictResolved -> Busy(BusyAction.Stage, intent.path)
        is ICodeIntent.ConfigureGitIdentity, is ICodeIntent.ConfigureGlobalGitIdentity, ICodeIntent.ClearRepositoryGitIdentity -> Busy(BusyAction.GitIdentity)
        is ICodeIntent.SaveGitToken, ICodeIntent.ClearGitToken -> Busy(BusyAction.GitToken)
        ICodeIntent.InstallRuntime, ICodeIntent.RepairRuntime, ICodeIntent.ResetRuntime -> Busy(BusyAction.Runtime)
        ICodeIntent.ClearAppCache -> Busy(BusyAction.ClearCache)
        is ICodeIntent.StartRun -> Busy(BusyAction.RunStart, intent.configuration)
        is LanguageIntent.Save -> Busy(BusyAction.LanguageService, intent.configuration.id.ifEmpty { NEW })
        is LanguageIntent.Remove -> Busy(BusyAction.LanguageService, intent.id)
        is LanguageIntent.Start -> Busy(BusyAction.LanguageService, intent.id)
        is LanguageIntent.Stop -> Busy(BusyAction.LanguageService, intent.id)
        is LanguageIntent.Restart -> Busy(BusyAction.LanguageService, intent.id)
        is LanguageIntent.Install -> Busy(BusyAction.LanguageService, intent.command)
        else -> null
    }

    /** The subject of every file at once: Stage All, Unstage All. */
    const val ALL = "*"

    /** The subject of a language service not saved yet. */
    const val NEW = ""
}

/** The subjects [action] runs for now: the files being staged. */
fun ICodeSnapshot.busySubjects(action: BusyAction): Set<String> = busy.filter { it.action == action }.mapNotNull { it.subject }.toSet()

/** Whether [action] runs now, for [subject] — or, without one, for any subject. */
fun ICodeSnapshot.isBusy(action: BusyAction, subject: String? = null): Boolean =
    if (subject == null) busy.any { it.action == action } else Busy(action, subject) in busy

/**
 * Sends [intent] and returns once the work it started has ended: at once when it makes no busy, or
 * was done before this looks. Its busy is published before [ICodeGateway.dispatch] returns, so the
 * wait cannot miss it. For a question held open, its button busy, until its answer is carried out.
 */
suspend fun ICodeGateway.dispatchAndWait(intent: ICodeIntent) {
    dispatch(intent)
    val busy = BusyRules.of(intent) ?: return
    snapshot.first { busy !in it.busy }
}

/** Whether [location] — a workspace or a file — is being opened now: its row says so. */
fun ICodeSnapshot.isOpening(location: String): Boolean = isBusy(BusyAction.OpenWorkspace, location) || isBusy(BusyAction.OpenFile, location)
