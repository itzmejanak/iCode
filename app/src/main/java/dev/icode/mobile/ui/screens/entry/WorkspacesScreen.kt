package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkspaceListRules
import dev.icode.mobile.domain.WorkspaceSort
import dev.icode.mobile.domain.storageSizeLabel
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import dev.icode.mobile.ui.WorkspaceRow

/**
 * Every workspace, and what can be done with each (docs/workspaces-page-plan.md §2.2): make one
 * (New Folder), find one, order them — Recent, Name, Size — and open, rename or delete each through
 * its one menu. The home screen shows the recent five of this same list.
 */
@Composable
fun WorkspacesScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(WorkspaceSort.Recent) }
    // Measured on arrival, and again when the list itself changes (one made, renamed, deleted).
    LaunchedEffect(state.workspaces.map { it.location }) { gateway.dispatch(WorkbenchIntent.MeasureWorkspaces) }
    val shown = WorkspaceListRules.sorted(WorkspaceListRules.matching(state.workspaces, query), sort, state.workspaceSizes)
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Workspaces",
            subtitle = WorkspaceListRules.summary(state.workspaces, state.workspaceSizes),
            onBack = actions.back,
            actions = {
                IconButton(onClick = { actions.navigate(Route.CreateFolder) }) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New Folder", tint = IcodeColors.TextPrimary)
                }
            },
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            IcodeTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Search workspaces",
                leadingIcon = Icons.Outlined.Search,
            )
            SegmentedControl(WorkspaceSort.entries.map { it.name }, sort.name) { sort = WorkspaceSort.valueOf(it) }
        }
        when {
            state.workspaces.isEmpty() -> EmptyState(
                title = "No workspaces yet",
                description = "New Folder above makes one; Import and Clone are on the home screen.",
                icon = Icons.Outlined.Folder,
                modifier = Modifier.padding(top = IcodeSpacing.Space6),
            )
            shown.isEmpty() -> EmptyState(
                title = "No workspace matches “${query.trim()}”",
                icon = Icons.Outlined.Search,
                modifier = Modifier.padding(top = IcodeSpacing.Space6),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = IcodeSpacing.Space4, end = IcodeSpacing.Space4, bottom = IcodeSpacing.Space4),
                verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
            ) {
                items(shown, key = { it.location }) { workspace ->
                    WorkspaceRow(workspace, size = storageSizeLabel(state.workspaceSizes[workspace.location]))
                }
            }
        }
    }
}
