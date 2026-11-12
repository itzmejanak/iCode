package dev.icode.mobile.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.icode.mobile.data.settings.AndroidWorkspacePreferencesRepository
import dev.icode.mobile.data.workspace.DocumentAutoSaveCoordinator
import dev.icode.mobile.designsystem.IcodeFloors
import dev.icode.mobile.designsystem.IcodeScreen
import dev.icode.mobile.designsystem.IcodeSheetHost
import dev.icode.mobile.designsystem.IcodeTheme
import dev.icode.mobile.designsystem.LocalIcodeFloors
import dev.icode.mobile.designsystem.PaletteKind
import dev.icode.mobile.domain.GitActionRules
import dev.icode.mobile.domain.GitSyncAction
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.WorkspaceTool
import dev.icode.mobile.domain.WorkspacePreferences
import dev.icode.mobile.domain.WorkspacePreferencesRepository
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.RouteGroup
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.ui.dock.rememberToolDock
import dev.icode.mobile.ui.editor.CodeEditorSessions
import dev.icode.mobile.ui.screens.entry.EntryScreenHost
import dev.icode.mobile.ui.screens.recovery.RecoveryScreenHost
import dev.icode.mobile.ui.screens.settings.SettingsScreenHost
import dev.icode.mobile.ui.screens.tools.DiscardChangesSheet
import dev.icode.mobile.ui.screens.tools.ToolScreenHost
import dev.icode.mobile.ui.screens.workspace.WorkspaceScreenHost
import dev.icode.mobile.ui.terminal.TerminalSessionSource
import java.io.File
import dev.icode.mobile.domain.notices.WebNotices

@Composable
fun IcodeApp(
    initialScreen: Route = Route.Splash,
    /** Fixed state to draw instead of the device's; only the capture tool asks for it, in debug builds. */
    capture: CaptureState? = null,
    /** Called once the first real screen can be drawn, releasing the system splash. */
    onReadyToRender: () -> Unit = {},
    /** What a notification or App info asked to show; [onArrived] once it is shown. */
    arrival: Arrival? = null,
    onArrived: () -> Unit = {},
) {
    val context = LocalContext.current
    val captureMode = capture != null
    val viewModel: AndroidICodeViewModel? = if (captureMode) {
        null
    } else {
        val factory = remember(context.applicationContext) { AndroidICodeViewModelFactory(context) }
        viewModel<AndroidICodeViewModel>(factory = factory)
    }
    val gateway: ICodeGateway = capture?.gateway ?: checkNotNull(viewModel).gateway
    // A capture has no runtime, so no terminal sessions to draw.
    val terminalSessions = viewModel?.terminalSessions ?: TerminalSessionSource.None
    val snapshot by gateway.snapshot.collectAsStateWithLifecycle()
    val preferencesRepository: WorkspacePreferencesRepository = capture?.preferences
        ?: remember(context.applicationContext) { AndroidWorkspacePreferencesRepository(context.applicationContext) }
    val root = snapshot.activeWorkspaceLocation
    val preferencesFlow = remember(preferencesRepository, root) { preferencesRepository.values(root) }
    val preferences by preferencesFlow.collectAsStateWithLifecycle(
        initialValue = WorkspacePreferences(workspaceLocation = root, loaded = false),
    )
    val appearance = preferences.appearance.takeIf { preferences.loaded && preferences.loadError == null }
    val borderLines = appearance?.borderLines ?: true
    val palette = appearancePalette(appearance, isSystemInDarkTheme())
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = palette.kind == PaletteKind.Light
                isAppearanceLightNavigationBars = palette.kind == PaletteKind.Light
            }
        }
    }
    IcodeTheme(
        palette = palette,
        uiTextScalePercent = appearance?.uiTextScalePercent ?: 100,
        compactLayout = appearance?.compactLayout ?: true,
        borderLines = borderLines,
    ) {
        LaunchedEffect(gateway, preferences.editor.autoSave, preferences.loaded, preferences.loadError, captureMode) {
            if (!captureMode && preferences.loaded && preferences.loadError == null) {
                DocumentAutoSaveCoordinator(gateway, preferences.editor.autoSave).run()
            }
        }
        val questions = rememberICodeQuestions()
        val editorSessions = remember(context, gateway) { CodeEditorSessions(context, gateway.languageHighlights) }
        // What the running servers offer, not each progress message: their semantic tokens are asked again.
        val servers = remember(snapshot.languageServices) {
            snapshot.languageServices.filter { it.status == LanguageServiceStatus.Running }.map { it.id to it.capabilities }
        }
        LaunchedEffect(editorSessions, servers) { editorSessions.languageServersChanged() }
        LaunchedEffect(editorSessions, gateway) {
            gateway.languageHighlights.refreshes.collect { editorSessions.languageServersChanged() }
        }
        // Until the settings load, the palette stands in; the editor's theme is built for the real one.
        LaunchedEffect(editorSessions, palette, preferences.loaded) {
            if (preferences.loaded) editorSessions.applyPalette(palette)
        }
        LaunchedEffect(editorSessions, preferences.editor, preferences.effectiveIndentSize, preferences.effectiveIndentType) {
            editorSessions.applyPreferences(preferences)
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        var resumeGeneration by remember { mutableIntStateOf(-1) }
        DisposableEffect(lifecycleOwner, gateway) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> resumeGeneration++
                    Lifecycle.Event.ON_START -> gateway.dispatch(ICodeIntent.ScreenVisible(true))
                    Lifecycle.Event.ON_STOP -> gateway.dispatch(ICodeIntent.ScreenVisible(false))
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        var lastAutomaticFetch by remember { mutableStateOf<Pair<String, Int>?>(null) }
        val canFetch = GitActionRules.syncBlocked(GitSyncAction.Fetch, snapshot) == null
        LaunchedEffect(root, resumeGeneration, preferences, canFetch, snapshot.runtimeHealthy) {
            val key = root?.let { it to resumeGeneration }
            if (!captureMode && resumeGeneration >= 0 && key != null && key != lastAutomaticFetch &&
                preferences.loaded && preferences.loadError == null &&
                preferences.workspaceLocation == root && preferences.autoFetch &&
                snapshot.runtimeHealthy && canFetch
            ) {
                lastAutomaticFetch = key
                gateway.dispatch(ICodeIntent.FetchRemote(automatic = true))
            }
        }
        val openDocumentLocations = snapshot.openDocuments.map { it.location }.toSet()
        LaunchedEffect(editorSessions, openDocumentLocations) {
            withFrameNanos { }
            editorSessions.releaseClosed(openDocumentLocations)
        }
        // What Android allows decides whether onboarding or the launcher comes
        // first, so there is nothing honest to draw until it is known.
        LaunchedEffect(captureMode, snapshot.systemAccess) {
            if (captureMode || snapshot.systemAccess != null) onReadyToRender()
        }
        // A permission may have been changed in Android's settings meanwhile,
        // and the workspace by another app or a command left running.
        LaunchedEffect(resumeGeneration) {
            if (resumeGeneration > 0) {
                gateway.dispatch(ICodeIntent.RefreshSystemAccess)
                gateway.dispatch(ICodeIntent.Refresh)
            }
        }
        val navigation = rememberNavigationState(initialScreen, captureMode)
        // The tools' histories are the open workspace's: another's start anew (docs/dock-navigation-plan.md D3).
        LaunchedEffect(snapshot.activeWorkspaceLocation) { navigation.workspaceIs(snapshot.activeWorkspaceLocation) }
        // Coming back from the terminal, where an agent or a command may have
        // changed files, the Explorer, Source Control and open files catch up.
        val terminalShown = navigation.current == Route.TerminalExpanded && navigation.panelTab == ToolPanelTab.Terminal
        var terminalWasShown by remember { mutableStateOf(false) }
        LaunchedEffect(terminalShown) {
            if (terminalWasShown && !terminalShown) gateway.dispatch(ICodeIntent.Refresh)
            terminalWasShown = terminalShown
        }
        val notices = remember { NoticeHostState() }
        val undo = rememberUndoPill(snapshot.explorer.history.news)
        val dock = rememberToolDock(snapshot, navigation.model.tool)
        val floors = remember { IcodeFloors() }
        ArrivalEffect(arrival, navigation, pastOnboarding = navigation.current !in ONBOARDING_SCREENS, gateway, snapshot, notices, onArrived)
        val systemPrompts = rememberSystemPrompts(gateway)
        val fileViewer = remember { FileViewerState() }

        LaunchedEffect(gateway) {
            gateway.effects.collect { effect ->
                when (effect) {
                    is ICodeEffect.RequestSystemPermission -> systemPrompts.request(effect.permission)
                    is ICodeEffect.OpenSystemSettings -> systemPrompts.openSettings(effect.permission)
                    // The permission screen walks on to its next step by itself.
                    is ICodeEffect.SystemPermissionResolved -> Unit
                    // What made the workspace gives way to it, so Back returns to the launcher.
                    is ICodeEffect.WorkspaceOpened -> navigation.opened(effect.location)
                    is ICodeEffect.WorkspaceTerminalOpened -> navigation.opened(effect.location, WorkspaceTool.Terminal)
                    ICodeEffect.TerminalOutsideWorkspace -> navigation.showTerminalOutside()
                    // The creation sheet consumes these request-scoped results.
                    is ICodeEffect.WorkspaceItemChanged, is ICodeEffect.WorkspaceItemFailed -> Unit
                    is ICodeEffect.ConfirmWorkspaceSwitch -> questions.ask(effect)
                    is ICodeEffect.ConfirmDraftRecovery -> questions.ask(effect)
                    is ICodeEffect.ConfirmRecoveredOverwrite -> questions.ask(effect)
                    is ICodeEffect.ExternalChangeDetected -> questions.ask(effect)
                    ICodeEffect.RuntimeRebuildStarted -> navigation.actions.navigate(Route.RuntimeInstalling)
                    ICodeEffect.GitInitialized -> navigation.show(Route.SourceControl)
                    is ICodeEffect.DocumentOpened -> {
                        if (effect.lineNumber != null) {
                            editorSessions.get(effect.location).revealAt(effect.lineNumber, effect.column ?: 0)
                        }
                        navigation.show(Route.WorkspaceEditor)
                    }
                    is ICodeEffect.Told -> notices.arrived(effect.notice, pageOpen = navigation.current == Route.Notifications)
                    // A chat shared as its transcript: Android's share sheet, as the terminal's output is.
                    is ICodeEffect.ShareChat -> {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, effect.text)
                        context.startActivity(Intent.createChooser(send, "Share ${effect.title}"))
                    }
                    is ICodeEffect.ViewFile -> fileViewer.show(effect.location, File(effect.location).name, effect.viewer)
                    is ICodeEffect.OpenInOtherApp -> SharedFiles.open(context, effect.location, gateway::dispatch)
                    is ICodeEffect.OpenExternalUrl -> {
                        // The user's own browser opens the URL; the app never
                        // renders web content itself (architecture.md section 11.5).
                        val view = Intent(Intent.ACTION_VIEW, effect.url.toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(view) }
                            .onFailure { gateway.dispatch(NoticeIntent.Tell(WebNotices.noBrowser())) }
                    }
                }
            }
        }

        DisposableEffect(editorSessions) {
            onDispose { editorSessions.releaseAll() }
        }

        BackHandler(enabled = navigation.canGoBack) {
            navigation.back()
        }

        CompositionLocalProvider(
            LocalICodeSnapshot provides snapshot,
            LocalICodeGateway provides gateway,
            LocalCodeEditorSessions provides editorSessions,
            LocalTerminalSessions provides terminalSessions,
            LocalWorkspacePreferencesRepository provides preferencesRepository,
            LocalWorkspacePreferences provides preferences,
            LocalIcodeFloors provides floors,
        ) {
            IcodeScreen {
                // Every sheet opened from anywhere — iCode has no sheets — is drawn over all of it.
                IcodeSheetHost {
                    ICodeQuestionSheets(questions, snapshot, gateway, navigation.current, navigation.actions.navigate)
                    TaskSheets(snapshot.tasks, gateway, asking = snapshot.explorer.question != null)
                    // A discard is confirmed over whatever asked for it, in the app's one confirmation sheet.
                    snapshot.pendingDiscard?.let { DiscardChangesSheet(it, gateway) }
                    // gpg-agent's question, over whatever asked it: a commit, the terminal, the assistant.
                    snapshot.gpgPrompts.firstOrNull()?.let { GpgPromptSheet(it, gateway) }
                    FileViewerHost(fileViewer) {
                        val pageOpen = navigation.current == Route.Notifications
                        val toAll = { navigation.actions.navigate(Route.Notifications) }
                        NoticeHost(notices, snapshot.notices, pageOpen, onAll = toAll, onAct = { act(it, gateway, navigation.actions) }) {
                            NavigationHost(navigation) { route -> ScreenHost(screen = route, actions = navigation.actions) }
                            FileAsks(snapshot.explorer, gateway)
                            // Over everything but the floating stack, which is hidden while it is open.
                            UndoSheet(undo, snapshot.explorer.history)
                        }
                        FloatingControls(floors, visible = !pageOpen, undo, notices, onAll = toAll, dock, navigation)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenHost(screen: Route, actions: NavigationActions) {
    when (screen.group) {
        RouteGroup.ENTRY -> EntryScreenHost(screen, actions)
        RouteGroup.WORKSPACE -> WorkspaceScreenHost(screen, actions)
        RouteGroup.TOOLS -> ToolScreenHost(screen, actions)
        RouteGroup.SETTINGS -> SettingsScreenHost(screen, actions)
        RouteGroup.RECOVERY -> RecoveryScreenHost(screen, actions)
    }
}

/** The first-run screens; each replaces the one before it. */
private val ONBOARDING_SCREENS = setOf(
    Route.Splash,
    Route.RuntimeInstalling,
    Route.PermissionExplanation,
)
