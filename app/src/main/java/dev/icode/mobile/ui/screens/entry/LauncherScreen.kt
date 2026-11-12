package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeDockedAction
import dev.icode.mobile.designsystem.IcodeIcons
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSkeletonRows
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeWatermark
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.designsystem.floor
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.ToolBadges
import dev.icode.mobile.domain.WorkspaceListRules
import dev.icode.mobile.domain.WorkspaceTool
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.WorkspaceRow
import dev.icode.mobile.ui.WorkspaceSheet
import dev.icode.mobile.ui.dot
import dev.icode.mobile.ui.icon
import dev.icode.mobile.ui.spoken
import dev.icode.mobile.ui.tint

/**
 * The launcher, as VS Code's Welcome page (docs/home-plan.md): Start — Import, Clone and the way to
 * the Workspaces page, where every workspace is managed and New Folder lives — then the recently
 * opened workspaces (docs/workspaces-page-plan.md §2.1), over the logo as a letterpress, and VS Code's
 * status bar at the foot ([HomeStatusBar]). [empty] renders the first-run empty state.
 */
@Composable
fun LauncherScreen(actions: NavigationActions, empty: Boolean) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val workspaces = state.workspaces
    // Before the list is read once, its rows' places are drawn: "No workspaces yet" would be untrue.
    val loading = !empty && !state.workspacesLoaded
    val showEmpty = !loading && (empty || workspaces.isEmpty())
    val explore = { place: StoragePlace ->
        gateway.dispatch(ICodeIntent.BrowsePlace(place))
        actions.navigate(Route.Workspaces)
    }
    val showIn = { folder: String ->
        gateway.dispatch(ICodeIntent.BrowseFiles(folder))
        actions.navigate(Route.Workspaces)
    }
    // The workspace whose sheet is shown; gone with the workspace.
    var actionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    val sheetFor = workspaces.firstOrNull { it.location == actionsFor }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(sheetFor != null)) {
            IcodeTopBar(
                title = "iCode",
                actions = {
                    IconButton(onClick = { actions.navigate(Route.SettingsRoot) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = IcodeColors.TextPrimary)
                    }
                },
            )
            // The logo as a letterpress in the free space, as VS Code's empty editor: the lists cover it as they fill the page.
            Box(Modifier.fillMaxWidth().weight(1f)) {
                IcodeWatermark(Modifier.align(Alignment.Center))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = IcodeSpacing.RowGutter, top = IcodeSpacing.Space2, end = IcodeSpacing.RowGutter),
                ) {
                    item { HomeLabel("Start") }
                    // Import and Workspaces open the one file explorer, at phone storage and at iCode's (docs/storage-explorer-plan.md §1).
                    item { StartLink(Icons.Outlined.FolderOpen, "Import…") { explore(StoragePlace.Shared) } }
                    item { StartLink(Icons.Outlined.AccountTree, "Clone Repository…") { actions.navigate(Route.CloneRepository) } }
                    item { StartLink(Icons.Outlined.FolderCopy, "Workspaces") { explore(StoragePlace.Private) } }
                    item { HomeLabel("Recent", Modifier.padding(top = IcodeSpacing.SectionGap)) }
                    when {
                        loading -> item { IcodeSkeletonRows(count = 3, Modifier.padding(horizontal = IcodeSpacing.RowInset), description = "Loading workspaces") }
                        showEmpty -> item {
                            EmptyState(
                                title = "No workspaces yet",
                                description = "Copy a folder in from phone storage with Import, clone\na repository, or make a new one in Workspaces.",
                                icon = Icons.Outlined.Folder,
                                modifier = Modifier.padding(top = IcodeSpacing.SectionGap),
                            )
                        }
                        else -> items(WorkspaceListRules.recent(workspaces), key = { it.location }) { workspace ->
                            WorkspaceRow(workspace, onActions = { actionsFor = workspace.location })
                        }
                    }
                }
            }
            HomeStatusBar(state, actions)
        }
        sheetFor?.let { workspace ->
            val close = { actionsFor = null }
            IcodeSheetOverlay(onClose = close) {
                WorkspaceSheet(
                    workspace,
                    // Shown where it is listed: the top of iCode's storage, the folder holding it.
                    onShowInList = { showIn(workspace.location.substringBeforeLast('/')) },
                    onClose = close,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** A section of the home, its label in line with the rows' text. */
@Composable
private fun HomeLabel(text: String, modifier: Modifier = Modifier) {
    SectionLabel(text, modifier.padding(start = IcodeSpacing.RowInset, bottom = IcodeSpacing.Space1))
}

/** One way in under Start, as VS Code's Welcome page draws them: a link in the accent. */
@Composable
private fun StartLink(icon: ImageVector, label: String, onClick: () -> Unit) {
    IcodeListRow(
        title = label,
        titleColor = IcodeColors.Accent,
        leading = { Icon(icon, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd)) },
        onClick = onClick,
    )
}

/**
 * VS Code's status bar (docs/home-plan.md §1), a floor the floating stack stands above. On its left the
 * runtime as VS Code's remote button: the accent while it is healthy, amber while it needs installing
 * (owner, 2026-09-30: what the user can act on is the way in). Then Terminal and Assistant, drawn and
 * badged as the dock draws them, opening their own pages as the Explorer's rail does; the bell on the right.
 */
@Composable
private fun HomeStatusBar(state: ICodeSnapshot, actions: NavigationActions) {
    Row(
        Modifier.floor().fillMaxWidth().height(IcodeSizes.BottomBar).background(IcodeColors.Surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val healthy = state.runtimeHealthy
        Box(
            Modifier
                .fillMaxHeight()
                .width(IcodeSizes.TouchTarget)
                .background(if (healthy) IcodeColors.Accent else IcodeColors.Amber)
                .clickable(role = Role.Button) { actions.navigate(if (healthy) Route.SettingsRuntime else Route.RuntimeInstalling) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                IcodeIcons.Runtime,
                contentDescription = if (healthy) "Runtime healthy" else "Runtime unavailable, install",
                tint = IcodeColors.OnAccent,
                modifier = Modifier.size(IcodeSizes.IconLg),
            )
        }
        StatusTool(WorkspaceTool.Terminal, state) { actions.openPanel(ToolPanelTab.Terminal) }
        // The assistant works in a workspace: with none there is nothing to open it in.
        StatusTool(WorkspaceTool.Assistant, state, enabled = state.workspaces.isNotEmpty()) { actions.navigate(Route.Assistant) }
        Spacer(Modifier.weight(1f))
        // Unsaved work waiting in Document Recovery keeps the dot until it is resolved.
        val unread = state.notices.any { !it.read } || state.recoverableDrafts.isNotEmpty()
        IcodeDockedAction(
            Icons.Outlined.Notifications,
            description = if (unread) "Notifications, unread" else "Notifications",
            tint = IcodeColors.TextSecondary,
            onClick = { actions.navigate(Route.Notifications) },
            dot = IcodeColors.Accent.takeIf { unread },
        )
    }
}

/** [tool] on the status bar, with the badge the dock gives it ([ToolBadges]). */
@Composable
private fun StatusTool(tool: WorkspaceTool, state: ICodeSnapshot, enabled: Boolean = true, onClick: () -> Unit) {
    val badge = ToolBadges.of(tool, state)
    IcodeDockedAction(
        tool.icon,
        description = badge.spoken(tool),
        tint = badge.tint ?: IcodeColors.TextSecondary,
        onClick = onClick,
        enabled = enabled,
        badge = badge.count.takeIf { it > 0 },
        dot = badge.dot,
    )
}
