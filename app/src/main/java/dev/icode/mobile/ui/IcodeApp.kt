package dev.icode.mobile.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.ui.terminal.TerminalSessionSource
import dev.icode.mobile.data.settings.AndroidWorkspacePreferencesRepository
import dev.icode.mobile.data.workspace.DocumentAutoSaveCoordinator
import dev.icode.mobile.designsystem.IcodeFloatingStack
import dev.icode.mobile.designsystem.IcodeFloors
import dev.icode.mobile.designsystem.IcodeScreen
import dev.icode.mobile.designsystem.IcodeTheme
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.PaletteKind
import dev.icode.mobile.designsystem.LocalIcodeFloors
import dev.icode.mobile.designsystem.withAccent
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.RouteGroup
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.screens.tools.DiscardChangesDialog
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.WorkspacePreferences
import dev.icode.mobile.domain.WorkspacePreferencesRepository
import dev.icode.mobile.domain.GitActionRules
import dev.icode.mobile.domain.GitSyncAction
import dev.icode.mobile.ui.editor.CodeEditorSessions
import androidx.compose.runtime.withFrameNanos
import dev.icode.mobile.ui.screens.entry.EntryScreenHost
import dev.icode.mobile.ui.screens.recovery.RecoveryScreenHost
import dev.icode.mobile.ui.screens.settings.SettingsScreenHost
import dev.icode.mobile.ui.screens.tools.ToolScreenHost
import dev.icode.mobile.ui.screens.workspace.WorkspaceScreenHost
import java.io.File

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
    val palette = IcodePalette.of(appearance?.theme ?: AppTheme.Dark, borderLines).withAccent(appearance?.accentColor)
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
        val editorSessions = remember(context, gateway) { CodeEditorSessions(context) }
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
                gateway.dispatch(ICodeIntent.FetchRemote)
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
        val floors = remember { IcodeFloors() }
        ArrivalEffect(arrival, navigation, pastOnboarding = navigation.current !in ONBOARDING_SCREENS, gateway, notices, onArrived)
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
                    is ICodeEffect.WorkspaceOpened -> if (navigation.current in MAKES_A_WORKSPACE) {
                        navigation.actions.replace(Route.WorkspaceExplorer)
                    } else {
                        navigation.actions.navigate(Route.WorkspaceExplorer)
                    }
                    is ICodeEffect.WorkspaceTerminalOpened -> navigation.actions.navigate(Route.TerminalExpanded)
                    // The creation dialog consumes these request-scoped results.
                    is ICodeEffect.WorkspaceItemChanged, is ICodeEffect.WorkspaceItemFailed -> Unit
                    is ICodeEffect.ConfirmWorkspaceSwitch -> questions.ask(effect)
                    is ICodeEffect.ConfirmDraftRecovery -> questions.ask(effect)
                    is ICodeEffect.ConfirmRecoveredOverwrite -> questions.ask(effect)
                    is ICodeEffect.ExternalChangeDetected -> questions.ask(effect)
                    ICodeEffect.RuntimeRebuildStarted -> navigation.actions.navigate(Route.RuntimeInstalling)
                    ICodeEffect.GitInitialized -> navigation.actions.navigate(Route.SourceControl)
                    is ICodeEffect.DocumentOpened -> {
                        if (effect.lineNumber != null) {
                            editorSessions.get(effect.location).revealAt(effect.lineNumber, effect.column ?: 0)
                        }
                        navigation.actions.navigate(Route.WorkspaceEditor)
                    }
                    is ICodeEffect.Notice -> notices.arrived(effect.notice)
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
                            .onFailure { gateway.dispatch(NoticeIntent.Notify(NoticeTone.HeadsUp, "No browser is available")) }
                    }
                }
            }
        }

        DisposableEffect(editorSessions) {
            onDispose { editorSessions.releaseAll() }
        }

        BackHandler(enabled = navigation.canGoBack) {
            navigation.actions.back()
        }

        ICodeQuestionDialogs(questions, snapshot, gateway, navigation.current, navigation.actions.navigate)
        TaskDialogs(snapshot.tasks, gateway, asking = snapshot.explorer.question != null)
        // A discard is confirmed over whatever asked for it, in the app's one confirmation dialog.
        snapshot.pendingDiscard?.let { DiscardChangesDialog(it, gateway) }
        // gpg-agent's question, over whatever asked it: a commit, the terminal, the assistant.
        snapshot.gpgPrompts.firstOrNull()?.let { GpgPromptDialog(it, gateway) }

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
                FileViewerHost(fileViewer) {
                    val pageOpen = navigation.current == Route.Notifications
                    val toAll = { navigation.actions.navigate(Route.Notifications) }
                    NoticeHost(notices, snapshot.notices, pageOpen, onAll = toAll) {
                        NavigationHost(navigation) { route -> ScreenHost(screen = route, actions = navigation.actions) }
                        FileAsks(snapshot.explorer, gateway)
                    }
                    // The undo pill above the bell, over every screen but the Notifications page (docs/explorer-gaps-plan.md §3.8).
                    IcodeFloatingStack(floors, visible = !pageOpen) { lineWidth ->
                        UndoPill(undo, snapshot.explorer.history, lineWidth)
                        NoticeBell(notices, snapshot.notices, lineWidth, onAll = toAll)
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

/** Screens that make a workspace: once it opens, it takes their place. */
private val MAKES_A_WORKSPACE = setOf(Route.CloneProgress)

/** The first-run screens; each replaces the one before it. */
private val ONBOARDING_SCREENS = setOf(
    Route.Splash,
    Route.RuntimeInstalling,
    Route.PermissionExplanation,
)
