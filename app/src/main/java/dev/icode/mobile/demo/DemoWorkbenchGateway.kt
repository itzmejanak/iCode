package dev.icode.mobile.demo

import dev.icode.mobile.domain.ContentMatch
import dev.icode.mobile.domain.ContentSearchResult
import dev.icode.mobile.domain.ContentSearchState
import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.FolderBrowserEntry
import dev.icode.mobile.domain.FolderBrowserPurpose
import dev.icode.mobile.domain.FolderBrowserState
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.data.git.GitConflictParser
import dev.icode.mobile.domain.GitAuthTestResult
import dev.icode.mobile.domain.GitCommitSummary
import dev.icode.mobile.domain.GitDiffHunk
import dev.icode.mobile.domain.GitDiffLine
import dev.icode.mobile.domain.GitDiffLineKind
import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.IndexedFile
import dev.icode.mobile.domain.LanguageService
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.RecentDocument
import dev.icode.mobile.domain.Severity
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.StorageSummary
import dev.icode.mobile.domain.StorageDetails
import dev.icode.mobile.domain.StorageMeasurement
import dev.icode.mobile.domain.SystemAccess
import dev.icode.mobile.domain.TextEncoding
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchSnapshot
import dev.icode.mobile.domain.WorkspaceSummary
import dev.icode.mobile.domain.reorderDocuments
import dev.icode.mobile.domain.selectedAfterClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Deterministic demo source of truth for static capture and previews.
 *
 * Every demo fixture lives here, never inside a screen: screens read one
 * snapshot and dispatch one intent set in both demo and production mode. That
 * keeps the post-integration cleanup a single deletion instead of a hunt for
 * `staticDemo` branches spread across product code.
 */
class DemoWorkbenchGateway : WorkbenchGateway {
    private val mutableSnapshot = MutableStateFlow(initialSnapshot())
    private val mutableEffects = MutableSharedFlow<WorkbenchEffect>(extraBufferCapacity = 8)
    private val createdFileTexts = mutableMapOf<String, String>()

    override val snapshot: StateFlow<WorkbenchSnapshot> = mutableSnapshot.asStateFlow()
    override val effects: SharedFlow<WorkbenchEffect> = mutableEffects.asSharedFlow()

    override fun dispatch(intent: WorkbenchIntent) {
        when (intent) {
            is WorkbenchIntent.RequestSystemPermission ->
                emit(WorkbenchEffect.SystemPermissionResolved(intent.permission, granted = true))
            is WorkbenchIntent.SystemPermissionAnswered,
            is WorkbenchIntent.DeclineSystemPermission,
            is WorkbenchIntent.OpenSystemSettings,
            WorkbenchIntent.RefreshSystemAccess,
            WorkbenchIntent.MarkNoticesRead,
            is WorkbenchIntent.DeleteNotice,
            WorkbenchIntent.ClearNotices,
            -> Unit
            is WorkbenchIntent.StartFolderBrowser -> {
                mutableSnapshot.update { it.copy(folderBrowser = demoBrowser(intent.purpose)) }
                emit(WorkbenchEffect.FolderBrowserOpened)
            }
            is WorkbenchIntent.ShowWorkspaceLocation -> {
                val workspace = mutableSnapshot.value.workspaces.firstOrNull { it.location == intent.path }
                if (workspace?.available == true) {
                    mutableSnapshot.update { state ->
                        state.copy(folderBrowser = demoBrowser(FolderBrowserPurpose.OpenWorkspace).copy(
                            currentPath = workspace.location,
                            displayPath = workspace.path,
                        ))
                    }
                    emit(WorkbenchEffect.FolderBrowserOpened)
                } else {
                    emit(WorkbenchEffect.Message("This folder is unavailable"))
                }
            }
            is WorkbenchIntent.BrowseFolder -> mutableSnapshot.update { state ->
                val browser = state.folderBrowser ?: return@update state
                val entry = browser.folders.firstOrNull { it.path == intent.path } ?: return@update state
                state.copy(
                    folderBrowser = browser.copy(
                        currentPath = entry.path,
                        displayPath = "${browser.displayPath} / ${entry.name}",
                        folders = emptyList(),
                    ),
                )
            }
            WorkbenchIntent.BrowseParentFolder -> mutableSnapshot.update { state ->
                val browser = state.folderBrowser ?: return@update state
                state.copy(folderBrowser = demoBrowser(browser.purpose))
            }
            WorkbenchIntent.ConfirmFolderSelection -> mutableSnapshot.update { state ->
                val browser = state.folderBrowser ?: return@update state
                when (browser.purpose) {
                    FolderBrowserPurpose.OpenWorkspace -> {
                        emit(WorkbenchEffect.WorkspaceOpened(browser.currentPath))
                        state
                    }
                    FolderBrowserPurpose.CreateFolderDestination -> {
                        emit(WorkbenchEffect.FolderDestinationSelected(browser.currentPath))
                        state.copy(
                            createFolderDestination = browser.currentPath,
                            createFolderDestinationDisplay = DEMO_CREATE_DESTINATION,
                        )
                    }
                }
            }
            is WorkbenchIntent.RequestCreateFolder -> emit(WorkbenchEffect.WorkspaceOpened(intent.name))
            is WorkbenchIntent.OpenWorkspace -> switchDemoWorkspace(intent.path, openTerminal = false)
            is WorkbenchIntent.OpenWorkspaceTerminal -> switchDemoWorkspace(intent.path, openTerminal = true)
            is WorkbenchIntent.ResolveWorkspaceSwitch -> switchDemoWorkspace(
                intent.path, intent.openTerminal, confirmed = true,
            )
            is WorkbenchIntent.RemoveRecentWorkspace -> mutableSnapshot.update { state ->
                state.copy(workspaces = state.workspaces.filterNot { it.location == intent.path })
            }
            is WorkbenchIntent.OpenFile -> mutableSnapshot.update { state ->
                val indexed = state.fileIndex.firstOrNull { it.path == intent.path }
                val document = state.openDocuments.firstOrNull { it.location == intent.path }
                    ?: demoDocument(indexed?.name ?: intent.path.substringAfterLast('/'), intent.path).let { fixture ->
                        if (createdFileTexts.containsKey(intent.path)) fixture.copy(text = createdFileTexts.getValue(intent.path))
                        else fixture
                    }
                emit(WorkbenchEffect.DocumentOpened(intent.path, intent.lineNumber, intent.column))
                state.copy(
                    openDocuments = if (state.openDocuments.any { it.location == intent.path }) state.openDocuments else state.openDocuments + document,
                    activeDocument = document,
                    recentDocuments = listOf(
                        RecentDocument(document.name, intent.path, intent.path.removePrefix("$DEMO_ROOT/"), "now"),
                    ) + state.recentDocuments.filterNot { it.location == intent.path },
                )
            }
            is WorkbenchIntent.CreateFile -> {
                val name = intent.name.trim()
                val root = mutableSnapshot.value.activeWorkspaceLocation ?: DEMO_ROOT
                val parent = intent.parentLocation ?: root
                val location = "$parent/$name"
                val error = when {
                    dev.icode.mobile.domain.WorkspaceNameRules.fileValidationError(name) != null ->
                        dev.icode.mobile.domain.WorkspaceNameRules.fileValidationError(name)
                    intent.expectedWorkspace != null && intent.expectedWorkspace != root ->
                        "The active workspace changed. Choose the destination again"
                    parent != root && mutableSnapshot.value.files.none {
                        it.path == parent && it.kind == FileKind.Folder
                    } -> "Choose a folder inside the active workspace"
                    mutableSnapshot.value.files.any { it.path == location } || mutableSnapshot.value.fileIndex.any { it.path == location } ->
                        "A file or folder with this name already exists"
                    else -> null
                }
                if (error != null) {
                    emitWorkspaceItemFailure(intent.requestId, error)
                } else {
                    createdFileTexts[location] = ""
                    val kind = when (name.substringAfterLast('.', "").lowercase()) {
                        "md", "markdown" -> FileKind.Markdown
                        "ts", "tsx" -> FileKind.TypeScript
                        "json" -> FileKind.Json
                        else -> FileKind.Other
                    }
                    mutableSnapshot.update { state -> state.copy(
                        fileIndex = state.fileIndex + IndexedFile(name, location, kind),
                        files = insertDemoWorkspaceItem(state.files, parent, root, FileEntry(name = name, path = location, kind = kind)),
                    ) }
                    if (intent.requestId != null) emit(WorkbenchEffect.WorkspaceItemCreated(intent.requestId, location))
                    dispatch(WorkbenchIntent.OpenFile(location))
                }
            }
            is WorkbenchIntent.CreateWorkspaceFolder -> {
                val name = intent.name.trim()
                val root = mutableSnapshot.value.activeWorkspaceLocation ?: DEMO_ROOT
                val parent = intent.parentLocation
                val location = "$parent/$name"
                val error = when {
                    dev.icode.mobile.domain.WorkspaceNameRules.workspaceFolderValidationError(name) != null ->
                        dev.icode.mobile.domain.WorkspaceNameRules.workspaceFolderValidationError(name)
                    intent.expectedWorkspace != root -> "The active workspace changed. Choose the destination again"
                    parent != root && mutableSnapshot.value.files.none {
                        it.path == parent && it.kind == FileKind.Folder
                    } -> "Choose a folder inside the active workspace"
                    mutableSnapshot.value.files.any { it.path == location } -> "A file or folder with this name already exists"
                    else -> null
                }
                if (error != null) {
                    emitWorkspaceItemFailure(intent.requestId, error)
                } else {
                    mutableSnapshot.update { state -> state.copy(
                        files = insertDemoWorkspaceItem(state.files, parent, root, FileEntry(name, location, FileKind.Folder)),
                    ) }
                    emit(WorkbenchEffect.WorkspaceItemCreated(intent.requestId, location))
                }
            }
            is WorkbenchIntent.UpdateDocumentText -> mutableSnapshot.update { state ->
                val document = state.openDocuments.firstOrNull { it.location == intent.location } ?: return@update state
                val changed = document.copy(text = intent.text, dirty = true)
                state.copy(
                    openDocuments = state.openDocuments.map { if (it.location == intent.location) changed else it },
                    activeDocument = if (state.activeDocument?.location == intent.location) changed else state.activeDocument,
                )
            }
            is WorkbenchIntent.SaveDocument -> mutableSnapshot.update { state ->
                val document = state.openDocuments.firstOrNull { it.location == intent.location } ?: return@update state
                if (createdFileTexts.containsKey(intent.location)) createdFileTexts[intent.location] = document.text
                emit(WorkbenchEffect.DocumentSaved(intent.location, intent.automatic))
                val saved = document.copy(dirty = false)
                state.copy(
                    openDocuments = state.openDocuments.map { if (it.location == intent.location) saved else it },
                    activeDocument = if (state.activeDocument?.location == intent.location) saved else state.activeDocument,
                )
            }
            // Static capture has no app-private draft store; these are real-gateway-only actions.
            is WorkbenchIntent.RestoreDraft,
            is WorkbenchIntent.DiscardDraft,
            is WorkbenchIntent.ConfirmRecoveredOverwrite -> Unit
            is WorkbenchIntent.SelectDocument -> mutableSnapshot.update { state ->
                state.copy(activeDocument = state.openDocuments.firstOrNull { it.location == intent.location } ?: state.activeDocument)
            }
            is WorkbenchIntent.ReorderDocument -> mutableSnapshot.update { state ->
                state.copy(openDocuments = reorderDocuments(state.openDocuments, intent.movingLocation, intent.targetLocation))
            }
            is WorkbenchIntent.CloseDocument -> mutableSnapshot.update { state ->
                val document = state.openDocuments.firstOrNull { it.location == intent.location } ?: return@update state
                if (document.dirty && intent.saveChanges == null) {
                    emit(WorkbenchEffect.Message("Choose Save, Discard or Cancel before closing ${document.name}"))
                    return@update state
                }
                val selected = selectedAfterClose(state.openDocuments, state.activeDocument?.location, intent.location)
                val remaining = state.openDocuments.filterNot { it.location == intent.location }
                state.copy(openDocuments = remaining, activeDocument = remaining.firstOrNull { it.location == selected })
            }
            is WorkbenchIntent.CloseOtherDocuments -> mutableSnapshot.update { state ->
                val others = state.openDocuments.filterNot { it.location == intent.keepLocation }
                if (others.any { it.dirty } && intent.saveChanges == null) {
                    emit(WorkbenchEffect.Message("Choose Save, Discard or Cancel before closing dirty tabs"))
                    return@update state
                }
                val kept = state.openDocuments.firstOrNull { it.location == intent.keepLocation }
                state.copy(openDocuments = listOfNotNull(kept), activeDocument = kept)
            }
            WorkbenchIntent.CloseSavedDocuments -> mutableSnapshot.update { state ->
                val remaining = state.openDocuments.filter { it.dirty }
                state.copy(
                    openDocuments = remaining,
                    activeDocument = remaining.firstOrNull { it.location == state.activeDocument?.location } ?: remaining.firstOrNull(),
                )
            }
            WorkbenchIntent.InitializeGit -> emit(WorkbenchEffect.GitInitialized)
            is WorkbenchIntent.ToggleFolder -> mutableSnapshot.update { state ->
                state.copy(files = state.files.map { file ->
                    if (file.path == intent.path) file.copy(expanded = !file.expanded) else file
                })
            }
            is WorkbenchIntent.StageFile -> mutableSnapshot.update { state ->
                state.copy(gitChanges = state.gitChanges.map { change ->
                    if (change.path == intent.path) change.copy(staged = true) else change
                })
            }
            is WorkbenchIntent.UnstageFile -> mutableSnapshot.update { state ->
                state.copy(gitChanges = state.gitChanges.map { change ->
                    if (change.path == intent.path) change.copy(staged = false) else change
                })
            }
            WorkbenchIntent.StageAllFiles -> mutableSnapshot.update { state ->
                state.copy(gitChanges = state.gitChanges.map { it.copy(staged = true) })
            }
            WorkbenchIntent.UnstageAllFiles -> mutableSnapshot.update { state ->
                state.copy(gitChanges = state.gitChanges.map { it.copy(staged = false) })
            }
            is WorkbenchIntent.CommitStaged -> mutableSnapshot.update { state ->
                emit(WorkbenchEffect.GitCommitted)
                state.copy(
                    gitChanges = state.gitChanges.filterNot { it.staged },
                    gitRepository = state.gitRepository?.let { repository ->
                        repository.copy(
                            ahead = repository.ahead + 1,
                            lastCommit = GitCommitSummary(
                                id = DEMO_COMMIT_ID,
                                message = intent.message,
                                author = DEMO_AUTHOR,
                                authoredAtEpochMillis = System.currentTimeMillis(),
                            ),
                        )
                    },
                )
            }
            is WorkbenchIntent.ConfigureGitIdentity -> mutableSnapshot.update { state ->
                state.copy(
                    localGitAuthorName = intent.name,
                    localGitAuthorEmail = intent.email,
                    gitRepository = state.gitRepository?.copy(
                        authorName = intent.name,
                        authorEmail = intent.email,
                    ),
                )
            }
            is WorkbenchIntent.ConfigureGlobalGitIdentity -> mutableSnapshot.update { state ->
                state.copy(
                    globalGitAuthorName = intent.name,
                    globalGitAuthorEmail = intent.email,
                    gitRepository = if (state.localGitAuthorName == null && state.localGitAuthorEmail == null) {
                        state.gitRepository?.copy(authorName = intent.name, authorEmail = intent.email)
                    } else state.gitRepository,
                )
            }
            WorkbenchIntent.ClearRepositoryGitIdentity -> mutableSnapshot.update { state ->
                state.copy(
                    localGitAuthorName = null,
                    localGitAuthorEmail = null,
                    gitRepository = state.gitRepository?.copy(
                        authorName = state.globalGitAuthorName,
                        authorEmail = state.globalGitAuthorEmail,
                    ),
                )
            }
            is WorkbenchIntent.SwitchBranch -> mutableSnapshot.update { state ->
                state.copy(
                    activeBranch = intent.branch,
                    gitRepository = state.gitRepository?.copy(branch = intent.branch),
                )
            }
            is WorkbenchIntent.DeleteBranch -> mutableSnapshot.update { state ->
                state.copy(
                    gitRepository = state.gitRepository?.let { repository ->
                        repository.copy(branches = repository.branches.filterNot { it == intent.branch })
                    },
                )
            }
            WorkbenchIntent.ProvisionGit -> mutableSnapshot.update { it.copy(gitAvailable = true) }
            WorkbenchIntent.RefreshGit -> Unit
            is WorkbenchIntent.OpenDiff -> mutableSnapshot.update { it.copy(activeDiff = DEMO_DIFF) }
            WorkbenchIntent.CloseDiff -> mutableSnapshot.update { it.copy(activeDiff = null) }
            is WorkbenchIntent.RequestDiscardChanges -> mutableSnapshot.update { state ->
                state.copy(pendingDiscard = state.gitChanges.firstOrNull { it.path == intent.path })
            }
            WorkbenchIntent.CancelDiscardChanges -> mutableSnapshot.update { it.copy(pendingDiscard = null) }
            is WorkbenchIntent.DiscardChanges -> mutableSnapshot.update { state ->
                state.copy(
                    gitChanges = state.gitChanges.filterNot { it.path == intent.path },
                    pendingDiscard = null,
                )
            }
            WorkbenchIntent.AbortInProgress -> Unit
            WorkbenchIntent.FetchRemote -> demoRemote("Fetching")
            WorkbenchIntent.PullRemote -> demoRemote("Pulling")
            WorkbenchIntent.PushRemote -> demoRemote("Pushing")
            is WorkbenchIntent.OpenConflict -> mutableSnapshot.update {
                it.copy(activeConflict = DEMO_CONFLICT)
            }
            WorkbenchIntent.CloseConflict -> mutableSnapshot.update { it.copy(activeConflict = null) }
            is WorkbenchIntent.ResolveConflict -> mutableSnapshot.update { state ->
                state.copy(gitChanges = state.gitChanges.filterNot { it.path == intent.path })
            }
            is WorkbenchIntent.MarkConflictResolved -> mutableSnapshot.update { state ->
                state.copy(
                    gitChanges = state.gitChanges.map {
                        if (it.path == intent.path) it.copy(status = GitStatusCodes.MODIFIED, staged = true) else it
                    },
                )
            }
            is WorkbenchIntent.UpdateCloneForm -> mutableSnapshot.update {
                it.copy(cloneUrl = intent.url, cloneBranch = intent.branch)
            }
            is WorkbenchIntent.CloneRepository -> emit(WorkbenchEffect.WorkspaceOpened(intent.directoryName))
            is WorkbenchIntent.SelectGitProvider -> mutableSnapshot.update {
                it.copy(gitAuth = it.gitAuth.copy(provider = intent.provider))
            }
            is WorkbenchIntent.SelectGitAuthMethod -> mutableSnapshot.update {
                it.copy(gitAuth = it.gitAuth.copy(method = intent.method))
            }
            is WorkbenchIntent.SaveGitToken -> mutableSnapshot.update {
                // The demo never holds a real secret; only the account shows.
                it.copy(gitAuth = it.gitAuth.copy(httpsAccount = "${intent.username}@${intent.host}"))
            }
            WorkbenchIntent.ClearGitToken -> mutableSnapshot.update {
                it.copy(gitAuth = it.gitAuth.copy(httpsAccount = null))
            }
            WorkbenchIntent.GenerateSshKey -> mutableSnapshot.update {
                it.copy(gitAuth = it.gitAuth.copy(hasSshKey = true, sshPublicKey = DEMO_SSH_PUBLIC_KEY))
            }
            is WorkbenchIntent.TestGitConnection -> mutableSnapshot.update {
                it.copy(
                    gitAuth = it.gitAuth.copy(
                        lastTest = GitAuthTestResult(true, "Connected — 3 branch(es) visible"),
                    ),
                )
            }
            WorkbenchIntent.RefreshGitAuth -> Unit
            is WorkbenchIntent.CreateBranch -> mutableSnapshot.update { state ->
                state.copy(
                    activeBranch = intent.branch,
                    gitRepository = state.gitRepository?.let { repository ->
                        repository.copy(
                            branch = intent.branch,
                            branches = (repository.branches + intent.branch).distinct().sorted(),
                        )
                    },
                )
            }
            is WorkbenchIntent.SetLanguageServiceRunning -> mutableSnapshot.update { state ->
                state.copy(languageServices = state.languageServices.map { service ->
                    if (service.name == intent.name) service.copy(
                        running = intent.running,
                        status = if (intent.running) LanguageServiceStatus.Running else LanguageServiceStatus.Stopped,
                    ) else service
                })
            }
            is WorkbenchIntent.SelectLanguageService -> mutableSnapshot.update {
                it.copy(selectedLanguageServiceId = intent.id.takeIf { id ->
                    it.languageServices.any { service -> service.id == id }
                })
            }
            is WorkbenchIntent.SaveLanguageService -> mutableSnapshot.update { state ->
                val id = intent.id ?: "demo-${state.languageServices.size + 1}"
                val service = LanguageService(
                    initials = intent.name.take(2).uppercase(),
                    name = intent.name,
                    patterns = intent.patterns,
                    running = false,
                    id = id,
                    command = intent.command,
                    rootMarkers = intent.rootMarkers,
                    initializationOptions = intent.initializationOptions,
                )
                state.copy(
                    languageServices = state.languageServices.filterNot { it.id == id } + service,
                    selectedLanguageServiceId = id,
                )
            }
            is WorkbenchIntent.RemoveLanguageService -> mutableSnapshot.update { state ->
                state.copy(
                    languageServices = state.languageServices.filterNot { it.id == intent.id },
                    selectedLanguageServiceId = null,
                )
            }
            is WorkbenchIntent.StartLanguageService -> mutableSnapshot.update { state ->
                state.copy(languageServices = state.languageServices.map { service ->
                    if (service.id == intent.id) service.copy(running = true, status = LanguageServiceStatus.Running)
                    else service
                })
            }
            is WorkbenchIntent.StopLanguageService -> mutableSnapshot.update { state ->
                state.copy(languageServices = state.languageServices.map { service ->
                    if (service.id == intent.id) service.copy(running = false, status = LanguageServiceStatus.Stopped)
                    else service
                })
            }
            is WorkbenchIntent.RestartLanguageService -> Unit
            WorkbenchIntent.InstallRuntime -> Unit
            WorkbenchIntent.RefreshRuntimeDetails -> Unit
            WorkbenchIntent.RefreshStorage -> Unit
            WorkbenchIntent.ClearAppCache -> mutableSnapshot.update { state ->
                state.copy(storageDetails = state.storageDetails?.copy(cache = StorageMeasurement(0)))
            }
            WorkbenchIntent.RemoveStaleRecents -> mutableSnapshot.update { state ->
                state.copy(workspaces = state.workspaces.filter { it.available })
            }
            WorkbenchIntent.RepairRuntime -> Unit
            WorkbenchIntent.ResetRuntime -> Unit
            // Static capture has no runtime, so there is no shell to open.
            WorkbenchIntent.OpenTerminalSession,
            is WorkbenchIntent.SelectTerminalSession,
            is WorkbenchIntent.ShowTerminalSession,
            is WorkbenchIntent.ReorderTerminalSession,
            is WorkbenchIntent.CloseTerminalSession,
            -> Unit
            is WorkbenchIntent.OpenExternalUrl -> emit(WorkbenchEffect.OpenExternalUrl(intent.url))
            is WorkbenchIntent.RunContentSearch -> mutableSnapshot.update { state ->
                state.copy(contentSearch = demoSearch(intent))
            }
            WorkbenchIntent.ClearContentSearch -> mutableSnapshot.update { state ->
                state.copy(contentSearch = ContentSearchState.Empty)
            }
            WorkbenchIntent.Refresh -> Unit
        }
    }

    private fun switchDemoWorkspace(path: String, openTerminal: Boolean, confirmed: Boolean = false) {
        val state = mutableSnapshot.value
        val workspace = state.workspaces.firstOrNull { it.location == path }
        if (workspace?.available == false) {
            emit(WorkbenchEffect.Message("This folder is unavailable"))
            return
        }
        if (path != state.activePath) {
            val dirtyCount = state.openDocuments.count { it.dirty }
            if (dirtyCount > 0 && !confirmed) {
                emit(WorkbenchEffect.ConfirmWorkspaceSwitch(path, openTerminal, dirtyCount))
                return
            }
            mutableSnapshot.update { current -> current.copy(
                activeWorkspace = workspace?.name ?: path.substringAfterLast('/'),
                activePath = workspace?.path ?: path,
                activeWorkspaceLocation = path,
                localGitAuthorName = null,
                localGitAuthorEmail = null,
                activeBranch = workspace?.branch.orEmpty(),
                activeDocument = null,
                openDocuments = emptyList(),
                recentDocuments = emptyList(),
                contentSearch = ContentSearchState.Empty,
            ) }
        }
        emit(if (openTerminal) WorkbenchEffect.WorkspaceTerminalOpened(path) else WorkbenchEffect.WorkspaceOpened(path))
    }

    private fun demoRemote(label: String) {
        mutableSnapshot.update { it.copy(gitOperation = GitOperationState.Succeeded(label)) }
    }

    private fun emitWorkspaceItemFailure(requestId: String?, message: String) {
        if (requestId == null) emit(WorkbenchEffect.Message(message))
        else emit(WorkbenchEffect.WorkspaceItemCreationFailed(requestId, message))
    }

    private fun insertDemoWorkspaceItem(
        files: List<FileEntry>,
        parent: String,
        root: String,
        entry: FileEntry,
    ): List<FileEntry> {
        if (parent == root) return files + entry
        val parentIndex = files.indexOfFirst { it.path == parent && it.kind == FileKind.Folder }
        if (parentIndex < 0) return files
        val parentDepth = files[parentIndex].depth
        var insertionIndex = parentIndex + 1
        while (insertionIndex < files.size && files[insertionIndex].depth > parentDepth) insertionIndex++
        val result = files.toMutableList()
        result[parentIndex] = result[parentIndex].copy(expanded = true)
        result.add(insertionIndex, entry.copy(depth = parentDepth + 1))
        return result
    }

    private fun emit(effect: WorkbenchEffect) {
        mutableEffects.tryEmit(effect)
    }

    private fun demoBrowser(purpose: FolderBrowserPurpose) = FolderBrowserState(
        purpose = purpose,
        rootPath = DEMO_ROOT,
        currentPath = DEMO_ROOT,
        displayPath = "root / projects / icode-subsystem",
        folders = listOf(
            FolderBrowserEntry("src", "$DEMO_ROOT/src", directoryCount = 12, fileCount = 4),
            FolderBrowserEntry("public", "$DEMO_ROOT/public", directoryCount = 2, fileCount = 15),
            FolderBrowserEntry("tests", "$DEMO_ROOT/tests", directoryCount = 1, fileCount = 6),
        ),
    )

    private fun demoSearch(intent: WorkbenchIntent.RunContentSearch) = ContentSearchState(
        query = intent.query,
        matchCase = intent.matchCase,
        wholeWord = intent.wholeWord,
        regex = intent.regex,
        results = listOf(
            ContentSearchResult(
                path = "src/App.tsx",
                name = "App.tsx",
                matches = listOf(
                    ContentMatch(1, 12, "import React, { useState } from 'react';"),
                    ContentMatch(5, 22, "const [active, setActive] = useState(true);"),
                    ContentMatch(6, 22, "const [loading, setLoading] = useState(false);"),
                ),
            ),
            ContentSearchResult(
                path = "src/hooks/useAuth.ts",
                name = "useAuth.ts",
                matches = listOf(
                    ContentMatch(2, 10, "import { useState, useEffect } from 'react';"),
                    ContentMatch(8, 18, "const [user, setUser] = useState(null);"),
                ),
            ),
        ),
        searchedFiles = 2,
        truncated = false,
    )

    private fun demoDocument(name: String, location: String, dirty: Boolean = false) = OpenDocument(
        name = name,
        location = location,
        text = when {
            name.endsWith(".json") -> """{ "name": "icode-subsystem", "version": "0.1.0" }"""
            name.endsWith(".md") -> "# iCode\n\nA mobile workspace with **real files** and a Linux terminal.\n"
            else -> DEMO_DOCUMENT_TEXT
        },
        encoding = TextEncoding.Utf8,
        lineEnding = LineEnding.Lf,
        dirty = dirty,
        readOnly = false,
    )

    private fun initialSnapshot() = WorkbenchSnapshot(
        workspaces = listOf(
            WorkspaceSummary("icode-subsystem", "~/projects/icode-subsystem", "main", "2h ago"),
            WorkspaceSummary("external-storage-driver", "~/sdcard/dev/storage-driver", "dev-v2", "5h ago"),
            WorkspaceSummary("legacy-compiler-plugin", "~/storage/shared/legacy-plugin", null, "1d ago", available = false),
        ),
        files = listOf(
            FileEntry("src", "src", FileKind.Folder, expanded = true),
            FileEntry("components", "src/components", FileKind.Folder, depth = 1, gitStatus = GitStatusCodes.ADDED),
            FileEntry("utils", "src/utils", FileKind.Folder, depth = 1, expanded = true),
            FileEntry("helpers.ts", "src/utils/helpers.ts", FileKind.TypeScript, depth = 2, gitStatus = GitStatusCodes.MODIFIED),
            FileEntry("tests", "tests", FileKind.Folder, expanded = false),
            FileEntry("README.md", "README.md", FileKind.Markdown),
            FileEntry("package.json", "package.json", FileKind.Json, gitStatus = "•"),
            FileEntry("index.ts", "index.ts", FileKind.TypeScript),
        ),
        gitChanges = listOf(
            GitChange(GitStatusCodes.ADDED, "auth-guard.tsx", "src/components/auth-guard.tsx", staged = true),
            GitChange(GitStatusCodes.ADDED, "token-utils.ts", "src/utils/token-utils.ts", staged = true),
            GitChange(GitStatusCodes.MODIFIED, "App.tsx", "src/App.tsx", staged = false),
            GitChange(GitStatusCodes.DELETED, "old-auth-helper.ts", "src/utils/old-auth-helper.ts", staged = false),
            GitChange(GitStatusCodes.UNTRACKED, "config-schema.json", "src/config-schema.json", staged = false),
            GitChange(GitStatusCodes.CONFLICTED, "session-store.ts", "src/utils/session-store.ts", staged = false),
        ),
        // The capture opens the diff, conflict and discard frames directly, without the
        // tap in Source Control that selects their subject; only those frames read these.
        activeDiff = DEMO_DIFF,
        activeConflict = DEMO_CONFLICT,
        pendingDiscard = GitChange(GitStatusCodes.DELETED, "old-auth-helper.ts", "src/utils/old-auth-helper.ts", staged = false),
        gitRepository = GitRepositoryState(
            workTreePath = DEMO_ROOT,
            branch = DemoContent.activeBranch,
            hasCommits = true,
            remoteName = "origin",
            authorName = null,
            authorEmail = null,
            branches = DemoContent.branches,
            lastCommit = GitCommitSummary(
                id = DEMO_COMMIT_ID,
                message = "feat: implement secure auth flow on Android",
                author = DEMO_AUTHOR,
                authoredAtEpochMillis = System.currentTimeMillis() - FIVE_MINUTES_MILLIS,
            ),
            upstream = "origin/${DemoContent.activeBranch}",
            ahead = 2,
            behind = 1,
        ),
        diagnostics = listOf(
            Diagnostic(Severity.Error, "src/App.tsx", "Type error: Property name does not exist on type User", "line 42:15"),
            Diagnostic(Severity.Warning, "src/App.tsx", "Unused variable: tempData", "line 15:8"),
            Diagnostic(Severity.Error, "src/utils/api.ts", "NetworkError: Call does not handle API timeout failures", "line 108:3"),
        ),
        languageServices = listOf(
            LanguageService(
                "TS", "TypeScript Language Server", "*.ts, *.tsx", running = true,
                id = "demo-typescript",
                command = "typescript-language-server --stdio",
                rootMarkers = "tsconfig.json, package.json",
                initializationOptions = "{\"preferences\":{\"importModuleSpecifier\":\"non-relative\"}}",
                processId = 24018,
            ),
            LanguageService("PY", "Pylsp (Python)", "*.py", running = false),
            LanguageService("RS", "Rust Analyzer", "*.rs", running = true),
        ),
        selectedLanguageServiceId = "demo-typescript",
        activeWorkspace = "icode-subsystem",
        initialExplorerSelection = DemoContent.selectedExplorerPath,
        activePath = "~/projects/icode-subsystem",
        activeWorkspaceLocation = DEMO_ROOT,
        activeBranch = DemoContent.activeBranch,
        gitIdentityLoaded = true,
        gitAvailable = true,
        runtimeHealthy = true,
        runtime = RuntimeState.Ready("Ubuntu 24.04.5 LTS"),
        runtimeDetails = dev.icode.mobile.domain.RuntimeDetails(
            release = "Ubuntu 24.04.5 LTS (noble) base",
            architecture = "arm64-v8a",
            rootfsPath = "/data/user/0/dev.icode.mobile/files/runtime/rootfs",
            installedPackages = 247,
            rootfsBytes = 1_932_735_283L,
            availableBytes = 16_106_127_360L,
            prootVersion = "5.1.107.92",
        ),
        systemAccess = SystemAccess(storage = true, notifications = true, unrestrictedBattery = true),
        activeDocument = demoDocument(DemoContent.appTsx, "src/App.tsx", dirty = true),
        openDocuments = listOf(
            demoDocument(DemoContent.appTsx, "src/App.tsx", dirty = true),
            demoDocument(DemoContent.packageJson, "package.json"),
            demoDocument("README.md", "README.md"),
        ),
        recentDocuments = listOf(
            RecentDocument("auth-guard.tsx", "$DEMO_ROOT/src/components/auth-guard.tsx", "src/components/auth-guard.tsx", "10m ago"),
            RecentDocument("token-utils.ts", "$DEMO_ROOT/src/utils/token-utils.ts", "src/utils/token-utils.ts", "2h ago"),
        ),
        createFolderDestination = "$DEMO_ROOT/src",
        createFolderDestinationDisplay = DEMO_CREATE_DESTINATION,
        storage = StorageSummary(availableBytes = 13314398617L, totalBytes = 137438953472L),
        storageDetails = StorageDetails(
            device = StorageSummary(
                availableBytes = (14.2 * 1024 * 1024 * 1024).toLong(),
                totalBytes = 16L * 1024 * 1024 * 1024,
            ),
            runtime = StorageMeasurement((1.1 * 1024 * 1024 * 1024).toLong()),
            cache = StorageMeasurement(220L * 1024 * 1024),
            workspaceSizes = mapOf(
                "~/projects/icode-subsystem" to StorageMeasurement(244L * 1024 * 1024),
                "~/sdcard/dev/storage-driver" to StorageMeasurement((1.2 * 1024 * 1024 * 1024).toLong()),
            ),
        ),
        fileIndex = listOf(
            IndexedFile(DemoContent.appTsx, "src/App.tsx", FileKind.TypeScript),
            IndexedFile("useAuth.ts", "src/hooks/useAuth.ts", FileKind.TypeScript),
            IndexedFile(DemoContent.packageJson, "package.json", FileKind.Json),
        ),
    )

    private companion object {
        const val DEMO_ROOT = "~/projects/icode-subsystem"
        const val DEMO_CREATE_DESTINATION = "~/projects/icode-subsystem/src"
        const val DEMO_COMMIT_ID = "a9d2b1f8c7e923"
        const val DEMO_AUTHOR = "Dev Android <dev@icode.com>"
        const val FIVE_MINUTES_MILLIS = 5L * 60L * 1000L

        /** Matches the conflict frame so static capture stays deterministic; markers as Git writes them. */
        val DEMO_CONFLICT = GitConflictParser.parse(
            "src/utils/session-store.ts",
            listOf(
                "export const validateToken = (token: string) => {",
                "<<<<<<< HEAD",
                "  const decoded = jwt.verify(token, SECRET);",
                "  return decoded.sub !== undefined;",
                "=======",
                "  const verified = oauth.decode(token);",
                "  return verified.isValid && verified.scope === 'write';",
                ">>>>>>> feature/oauth",
                "};",
            ).joinToString("\n"),
        )
        const val DEMO_SSH_PUBLIC_KEY =
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIDemoKeyForCaptureOnly0000000000 icode"

        /** Matches the diff frame so static capture stays deterministic. */
        val DEMO_DIFF = GitFileDiff(
            path = "src/App.tsx",
            hunks = listOf(
                GitDiffHunk(
                    oldStart = 15, oldCount = 8, newStart = 15, newCount = 12,
                    header = "@@ -15,8 +15,12 @@",
                    lines = listOf(
                        GitDiffLine(GitDiffLineKind.Context, 15, 15, "const config = {"),
                        GitDiffLine(GitDiffLineKind.Removed, 16, null, "  secure: false,"),
                        GitDiffLine(GitDiffLineKind.Added, null, 16, "  secure: true,"),
                        GitDiffLine(GitDiffLineKind.Added, null, 17, "  algorithm: 'RS256',"),
                        GitDiffLine(GitDiffLineKind.Context, 17, 18, "subscribe();"),
                    ),
                ),
            ),
        )
        val DEMO_DOCUMENT_TEXT = """
            import React from 'components';
            const ActiveComponent = () => {
              return <div>Active Workspace</div>;
            };
            export default ActiveComponent;
        """.trimIndent()
    }
}
