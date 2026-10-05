package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.BottomPanelBar
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeNavigationLink
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** Empty editor state with real quick actions and recent files. */
@Composable
fun EditorEmptyScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    var showCreateFile by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = state.activeWorkspace,
            subtitle = state.activePath,
            onBack = { actions.navigate(Route.LauncherHome) },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space4),
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            EmptyActionTile(Icons.Outlined.Search, "Quick Open", Modifier.weight(1f)) { actions.navigate(Route.QuickOpen) }
            EmptyActionTile(Icons.Outlined.Add, "New File", Modifier.weight(1f)) { showCreateFile = true }
            EmptyActionTile(Icons.Outlined.Terminal, "Open Terminal", Modifier.weight(1f)) { actions.openPanel(ToolPanelTab.Terminal) }
        }
        IcodeNavigationLink(
            label = "Search Workspace",
            detail = "Find text inside this project's files",
            icon = Icons.Outlined.Search,
            onClick = { actions.navigate(Route.WorkspaceSearch) },
            modifier = Modifier.padding(horizontal = IcodeSpacing.Space4),
        )
        EmptyState(
            title = "Select a file to begin coding",
            mark = {
                IcodeBrandMark(Modifier.size(IcodeSizes.MarkLg))
            },
            modifier = Modifier.padding(top = IcodeSpacing.Space6 + IcodeSpacing.Space4),
        )
        Column(
            modifier = Modifier.padding(IcodeSpacing.Space4).weight(1f),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            SectionLabel(
                "Recent Files",
                Modifier.padding(top = IcodeSpacing.Space6 + IcodeSpacing.Micro, bottom = IcodeSpacing.Space1),
            )
            if (state.recentDocuments.isEmpty()) {
                Text("Files you open will appear here", style = IcodeType.Caption, color = IcodeColors.TextSecondary)
            }
            state.recentDocuments.forEach { document ->
                RecentFile(document.name, document.path, if (document.available) RelativeTime.since(document.lastOpenedAt) else "Missing") {
                    gateway.dispatch(ICodeIntent.OpenFile(document.location))
                }
            }
        }
        BottomPanelBar(
            problemCount = state.diagnostics.size,
            onTerminal = { actions.openPanel(ToolPanelTab.Terminal) },
            onProblems = { actions.openPanel(ToolPanelTab.Problems) },
        )
    }
    if (showCreateFile) CreateFileDialog(parentLocation = null, onDismiss = { showCreateFile = false })
}

@Composable
private fun EmptyActionTile(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    IcodeCard(modifier = modifier, padding = PaddingValues(IcodeSpacing.Space2 + IcodeSpacing.Micro), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro)) {
            Icon(icon, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm))
            Text(text, style = IcodeType.Caption.copy(color = IcodeColors.TextPrimary), maxLines = 1)
        }
    }
}

@Composable
private fun RecentFile(name: String, path: String, age: String, onClick: () -> Unit) {
    IcodeListRow(
        card = true,
        title = name,
        subtitle = path,
        titleStyle = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
        onClick = onClick,
        leading = {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconXl))
        },
        trailing = { Text(age, style = IcodeType.Caption) },
    )
}
