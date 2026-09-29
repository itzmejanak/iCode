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
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.ui.screens.recovery.ExternalChangePrompt
import dev.icode.mobile.ui.terminal.TerminalSessionSource
import dev.icode.mobile.data.settings.AndroidWorkspacePreferencesRepository
import dev.icode.mobile.data.workspace.DocumentAutoSaveCoordinator
import dev.icode.mobile.designsystem.IcodeScreen
import dev.icode.mobile.designsystem.IcodeSaveDiscardDialog
import dev.icode.mobile.designsystem.IcodeTheme
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.PaletteKind
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.RouteGroup
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.WorkspacePreferences
import dev.icode.mobile.domain.WorkspacePreferencesRepository
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.ui.editor.CodeEditorSessions
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import dev.icode.mobile.ui.screens.entry.EntryScreenHost
import dev.icode.mobile.ui.screens.recovery.RecoveryScreenHost
import dev.icode.mobile.ui.screens.settings.SettingsScreenHost
import dev.icode.mobile.ui.screens.tools.ToolScreenHost
import dev.icode.mobile.ui.screens.workspace.WorkspaceScreenHost
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton

@Composable
fun IcodeApp(
    initialScreen: Route = Route.Splash,
    /** Fixed state to draw instead of the device's; only the capture tool asks for it, in debug builds. */
    capture: CaptureState? = null,
    /** Called once the first real screen can be drawn, releasing the system splash. */
    onReadyToRender: () -> Unit = {},
    /** A terminal session to bring to the front, from its notification. */
    terminalSessionToShow: String? = null,
    onTerminalSessionShown: () -> Unit = {},
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
    }
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
        var pendingWorkspaceSwitch by remember { mutableStateOf<WorkbenchEffect.ConfirmWorkspaceSwitch?>(null) }
        var externalChangeAsked by remember { mutableStateOf<String?>(null) }
        // One question at a time: restoring several drafts asks about each in turn.
        var pendingDraftRecoveries by remember { mutableStateOf<List<WorkbenchEffect.ConfirmDraftRecovery>>(emptyList()) }
        var pendingRecoveredOverwrite by remember { mutableStateOf<WorkbenchEffect.ConfirmRecoveredOverwrite?>(null) }
        val editorSessions = remember(context, gateway) { CodeEditorSessions(context) }
        LaunchedEffect(editorSessions, palette) { editorSessions.applyPalette(palette) }
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
        LaunchedEffect(root, resumeGeneration, preferences, snapshot.gitRepository?.hasRemote, snapshot.runtimeHealthy) {
            val key = root?.let { it to resumeGeneration }
            if (!captureMode && resumeGeneration >= 0 && key != null && key != lastAutomaticFetch &&
                preferences.loaded && preferences.loadError == null &&
                preferences.workspaceLocation == root && preferences.autoFetch &&
                snapshot.runtimeHealthy && snapshot.gitRepository?.hasRemote == true
            ) {
                lastAutomaticFetch = key
                if (snapshot.gitOperation !is GitOperationState.Running) gateway.dispatch(WorkbenchIntent.FetchRemote)
            }
        }
        val openDocumentLocations = snapshot.openDocuments.map { it.location }.toSet()
        LaunchedEffect(editorSessions, openDocumentLocations) {
            withFrameNanos { }
            editorSessions.releaseClosed(openDocumentLocations)
        }
        // Storage access decides whether onboarding or the launcher comes
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
        // Onboarding replaces the top screen as it advances, so a terminal
        // opened during it would be replaced; wait until it is over.
        val pastOnboarding = navigation.current !in ONBOARDING_SCREENS
        LaunchedEffect(terminalSessionToShow, pastOnboarding) {
            val id = terminalSessionToShow ?: return@LaunchedEffect
            if (!pastOnboarding) return@LaunchedEffect
            gateway.dispatch(WorkbenchIntent.ShowTerminalSession(id))
            onTerminalSessionShown()
        }
        val systemPrompts = rememberSystemPrompts(gateway)

        LaunchedEffect(gateway) {
            gateway.effects.collect { effect ->
                when (effect) {
                    is WorkbenchEffect.RequestSystemPermission -> systemPrompts.request(effect.permission)
                    is WorkbenchEffect.OpenSystemSettings -> systemPrompts.openSettings(effect.permission)
                    is WorkbenchEffect.SystemPermissionResolved -> if (effect.permission == SystemPermission.Storage) {
                        // The permission screen walks on to its next step by
                        // itself. Elsewhere a grant replaces, so granting from
                        // the launcher's banner leaves one launcher behind,
                        // not a stack of them.
                        if (!effect.granted) {
                            Toast.makeText(context, "All files access was not granted", Toast.LENGTH_LONG).show()
                        } else if (navigation.current != Route.PermissionExplanation) {
                            navigation.actions.replace(Route.LauncherHome)
                        }
                    }
                    WorkbenchEffect.FolderBrowserOpened -> navigation.actions.navigate(Route.OpenFolderBrowser)
                    is WorkbenchEffect.FolderDestinationSelected -> navigation.actions.back()
                    // A finished clone's progress gives way to its workspace, so Back returns to the form.
                    is WorkbenchEffect.WorkspaceOpened -> if (navigation.current == Route.CloneProgress) {
                        navigation.actions.replace(Route.WorkspaceExplorer)
                    } else {
                        navigation.actions.navigate(Route.WorkspaceExplorer)
                    }
                    is WorkbenchEffect.WorkspaceTerminalOpened -> navigation.actions.navigate(Route.TerminalExpanded)
                    // The creation dialog consumes these request-scoped results.
                    is WorkbenchEffect.WorkspaceItemChanged, is WorkbenchEffect.WorkspaceItemFailed -> Unit
                    is WorkbenchEffect.ConfirmWorkspaceSwitch -> pendingWorkspaceSwitch = effect
                    is WorkbenchEffect.ConfirmDraftRecovery -> pendingDraftRecoveries =
                        pendingDraftRecoveries.filterNot { it.location == effect.location } + effect
                    is WorkbenchEffect.ConfirmRecoveredOverwrite -> pendingRecoveredOverwrite = effect
                    is WorkbenchEffect.ExternalChangeDetected -> externalChangeAsked = effect.location
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

        pendingWorkspaceSwitch?.let { switch ->
            IcodeSaveDiscardDialog(
                title = "Switch workspace?",
                description = "${countOf(switch.dirtyCount, "file has", "files have")} unsaved changes.",
                onSave = {
                    gateway.dispatch(WorkbenchIntent.ResolveWorkspaceSwitch(switch.location, switch.openTerminal, saveChanges = true))
                    pendingWorkspaceSwitch = null
                },
                onDiscard = {
                    gateway.dispatch(WorkbenchIntent.ResolveWorkspaceSwitch(switch.location, switch.openTerminal, saveChanges = false))
                    pendingWorkspaceSwitch = null
                },
                onDismiss = { pendingWorkspaceSwitch = null },
            )
        }
        pendingDraftRecoveries.firstOrNull()?.let { recovery ->
            val answered = { pendingDraftRecoveries = pendingDraftRecoveries.drop(1) }
            IcodeConfirmDialog(
                title = "Recover ${recovery.name}?",
                confirmLabel = "Restore draft",
                onConfirm = {
                    gateway.dispatch(WorkbenchIntent.RestoreDraft(recovery.location))
                    answered()
                },
                onDismiss = answered,
            ) {
                Text(
                    if (recovery.sourceChanged)
                        "The file changed outside iCode. Restoring the draft will keep it in the editor, but saving over the newer file needs your confirmation."
                    else "Unsaved text from the last session is available. Restore it into the editor or keep the version on disk.",
                    style = IcodeType.Body,
                )
                SecondaryButton(
                    "Keep disk version · discard draft",
                    onClick = {
                        gateway.dispatch(WorkbenchIntent.DiscardDraft(recovery.location))
                        answered()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = IcodeColors.Error,
                )
            }
        }
        ExternalChangePrompt(
            asked = externalChangeAsked,
            onAskedChange = { externalChangeAsked = it },
            snapshot = snapshot,
            gateway = gateway,
            current = navigation.current,
            navigate = navigation.actions.navigate,
        )
        pendingRecoveredOverwrite?.let { overwrite ->
            IcodeConfirmDialog(
                title = "Overwrite newer file?",
                confirmLabel = "Overwrite file",
                destructive = true,
                onConfirm = {
                    gateway.dispatch(WorkbenchIntent.ConfirmRecoveredOverwrite(overwrite.location))
                    pendingRecoveredOverwrite = null
                },
                onDismiss = { pendingRecoveredOverwrite = null },
            ) {
                Text(
                    "${overwrite.name} changed outside iCode since the recovery draft was made. This saves the recovered text over the current disk version. Cancel to keep both unchanged.",
                    style = IcodeType.Body,
                )
            }
        }

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

/** The first-run screens; each replaces the one before it. */
private val ONBOARDING_SCREENS = setOf(
    Route.Splash,
    Route.RuntimeInstalling,
    Route.PermissionExplanation,
)
