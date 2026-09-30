package dev.icode.mobile.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
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
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.ui.terminal.TerminalSessionSource
import dev.icode.mobile.data.settings.AndroidWorkspacePreferencesRepository
import dev.icode.mobile.data.workspace.DocumentAutoSaveCoordinator
import dev.icode.mobile.designsystem.IcodeScreen
import dev.icode.mobile.designsystem.IcodeTheme
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.PaletteKind
import dev.icode.mobile.designsystem.withAccent
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.RouteGroup
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
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
    val workbench: AndroidWorkbenchViewModel? = if (captureMode) {
        null
    } else {
        val factory = remember(context.applicationContext) { AndroidWorkbenchViewModelFactory(context) }
        viewModel<AndroidWorkbenchViewModel>(factory = factory)
    }
    val gateway: WorkbenchGateway = capture?.gateway ?: checkNotNull(workbench).gateway
    // A capture has no runtime, so no terminal sessions to draw.
    val terminalSessions = workbench?.terminalSessions ?: TerminalSessionSource.None
    val snapshot by gateway.snapshot.collectAsStateWithLifecycle()
    val preferencesRepository: WorkspacePreferencesRepository = capture?.preferences
        ?: remember(context.applicationContext) { AndroidWorkspacePreferencesRepository(context.applicationContext) }
    val root = snapshot.activeWorkspaceLocation
    val preferencesFlow = remember(preferencesRepository, root) { preferencesRepository.values(root) }
    val preferences by preferencesFlow.collectAsStateWithLifecycle(
        initialValue = WorkspacePreferences(workspaceLocation = root, loaded = false),
    )
    val appearance = preferences.appearance.takeIf { preferences.loaded && preferences.loadError == null }
    val palette = when (appearance?.theme) {
        AppTheme.Light -> IcodePalette.Light
        AppTheme.Amoled -> IcodePalette.Amoled
        else -> IcodePalette.Dark
    }.withAccent(appearance?.accentColor)
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
    ) {
        LaunchedEffect(gateway, preferences.editor.autoSave, preferences.loaded, preferences.loadError, captureMode) {
            if (!captureMode && preferences.loaded && preferences.loadError == null) {
                DocumentAutoSaveCoordinator(gateway, preferences.editor.autoSave).run()
            }
        }
        val questions = rememberWorkbenchQuestions()
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
                    Lifecycle.Event.ON_START -> gateway.dispatch(WorkbenchIntent.ScreenVisible(true))
                    Lifecycle.Event.ON_STOP -> gateway.dispatch(WorkbenchIntent.ScreenVisible(false))
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
                gateway.dispatch(WorkbenchIntent.FetchRemote)
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
                gateway.dispatch(WorkbenchIntent.RefreshSystemAccess)
                gateway.dispatch(WorkbenchIntent.Refresh)
            }
        }
        val navigation = rememberNavigationState(initialScreen, captureMode)
        // Coming back from the terminal, where an agent or a command may have
        // changed files, the Explorer, Source Control and open files catch up.
        val terminalShown = navigation.current == Route.TerminalExpanded && navigation.panelTab == ToolPanelTab.Terminal
        var terminalWasShown by remember { mutableStateOf(false) }
        LaunchedEffect(terminalShown) {
            if (terminalWasShown && !terminalShown) gateway.dispatch(WorkbenchIntent.Refresh)
            terminalWasShown = terminalShown
        }
        ArrivalEffect(arrival, navigation, pastOnboarding = navigation.current !in ONBOARDING_SCREENS, gateway, onArrived)
        val systemPrompts = rememberSystemPrompts(gateway)

        LaunchedEffect(gateway) {
            gateway.effects.collect { effect ->
                when (effect) {
                    is WorkbenchEffect.RequestSystemPermission -> systemPrompts.request(effect.permission)
                    is WorkbenchEffect.OpenSystemSettings -> systemPrompts.openSettings(effect.permission)
                    // The permission screen walks on to its next step by itself.
                    is WorkbenchEffect.SystemPermissionResolved -> Unit
                    // Change on the Import screen picks again; it stays, showing the new folder.
                    WorkbenchEffect.ImportSourceChosen -> if (navigation.current != Route.ImportWorkspace) {
                        navigation.actions.navigate(Route.ImportWorkspace)
                    }
                    is WorkbenchEffect.ShowInFiles -> systemPrompts.showInFiles(effect)
                    // What made the workspace gives way to it, so Back returns to the launcher.
                    is WorkbenchEffect.WorkspaceOpened -> if (navigation.current in MAKES_A_WORKSPACE) {
                        navigation.actions.replace(Route.WorkspaceExplorer)
                    } else {
                        navigation.actions.navigate(Route.WorkspaceExplorer)
                    }
                    is WorkbenchEffect.WorkspaceTerminalOpened -> navigation.actions.navigate(Route.TerminalExpanded)
                    // The creation dialog consumes these request-scoped results.
                    is WorkbenchEffect.WorkspaceItemChanged, is WorkbenchEffect.WorkspaceItemFailed -> Unit
                    is WorkbenchEffect.ConfirmWorkspaceSwitch -> questions.ask(effect)
                    is WorkbenchEffect.ConfirmDraftRecovery -> questions.ask(effect)
                    is WorkbenchEffect.ConfirmRecoveredOverwrite -> questions.ask(effect)
                    is WorkbenchEffect.ExternalChangeDetected -> questions.ask(effect)
                    WorkbenchEffect.RuntimeRebuildStarted -> navigation.actions.navigate(Route.RuntimeInstalling)
                    WorkbenchEffect.GitInitialized -> navigation.actions.navigate(Route.SourceControl)
                    WorkbenchEffect.GitCommitted -> Toast.makeText(context, "Commit created", Toast.LENGTH_SHORT).show()
                    is WorkbenchEffect.DocumentOpened -> {
                        if (effect.lineNumber != null) {
                            editorSessions.get(effect.location).revealAt(effect.lineNumber, effect.column ?: 0)
                        }
                        navigation.actions.navigate(Route.WorkspaceEditor)
                    }
                    is WorkbenchEffect.DocumentSaved -> if (!effect.automatic) {
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    }
                    is WorkbenchEffect.Message -> Toast.makeText(context, effect.text, Toast.LENGTH_LONG).show()
                    is WorkbenchEffect.OpenExternalUrl -> {
                        // The user's own browser opens the URL; the app never
                        // renders web content itself (architecture.md section 11.5).
                        val view = Intent(Intent.ACTION_VIEW, effect.url.toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(view) }
                            .onFailure { Toast.makeText(context, "No browser is available", Toast.LENGTH_LONG).show() }
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

        WorkbenchQuestionDialogs(questions, snapshot, gateway, navigation.current, navigation.actions.navigate)
        TaskDialogs(snapshot.tasks, gateway)

        CompositionLocalProvider(
            LocalWorkbenchSnapshot provides snapshot,
            LocalWorkbenchGateway provides gateway,
            LocalCodeEditorSessions provides editorSessions,
            LocalTerminalSessions provides terminalSessions,
            LocalWorkspacePreferencesRepository provides preferencesRepository,
            LocalWorkspacePreferences provides preferences,
        ) {
            IcodeScreen {
                ScreenHost(screen = navigation.current, actions = navigation.actions)
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
private val MAKES_A_WORKSPACE = setOf(Route.CreateFolder, Route.ImportWorkspace, Route.CloneProgress)

/** The first-run screens; each replaces the one before it. */
private val ONBOARDING_SCREENS = setOf(
    Route.Splash,
    Route.RuntimeInstalling,
    Route.PermissionExplanation,
)
