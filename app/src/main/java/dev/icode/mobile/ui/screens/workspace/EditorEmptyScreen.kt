package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.BottomPanelBar
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeFileRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.files.FileFolders
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.ui.FileTypeIcon
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.parentName

/**
 * The editor with no file open (docs/design-and-navigation.md): one list that scrolls above the
 * Terminal and Problems bar — four ways in as equal tiles, then every file of this workspace opened
 * lately, as the explorer lists files. The mark and its words only while there are none yet.
 */
@Composable
fun EditorEmptyScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    var showCreateFile by remember { mutableStateOf(false) }
    val root = state.activeWorkspaceLocation
    val recent = state.explorer.recent.filter { root != null && FileFolders.relative(it.location, root) != null }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = state.activeWorkspace,
            subtitle = state.activePath,
            // Back as anywhere (docs/design-and-navigation.md): the tool used before, then the Launcher.
            onBack = actions.back,
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = IcodeSpacing.SectionGap)) {
            item(key = "ways in") {
                Column(Modifier.padding(IcodeSpacing.Gutter), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap)) {
                        WayIn(Icons.Outlined.Search, "Quick Open", Modifier.weight(1f)) { actions.navigate(Route.QuickOpen) }
                        WayIn(Icons.AutoMirrored.Outlined.ManageSearch, "Find in Files", Modifier.weight(1f)) { actions.navigate(Route.WorkspaceSearch) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap)) {
                        WayIn(Icons.Outlined.Add, "New File", Modifier.weight(1f)) { showCreateFile = true }
                        WayIn(Icons.Outlined.Terminal, "Terminal", Modifier.weight(1f)) { actions.openPanel(ToolPanelTab.Terminal) }
                    }
                }
            }
            if (recent.isEmpty()) {
                item(key = "nothing yet") {
                    EmptyState(
                        title = "No file open",
                        description = "Files you open appear here",
                        mark = { IcodeBrandMark(Modifier.size(IcodeSizes.MarkLg)) },
                        modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space6),
                    )
                }
            } else {
                item(key = "recent") {
                    SectionLabel(
                        "Recent Files",
                        Modifier.padding(start = IcodeSpacing.Gutter, top = IcodeSpacing.SectionBreak, end = IcodeSpacing.Gutter, bottom = IcodeSpacing.Space1),
                    )
                }
                items(recent, key = { it.location }) { file ->
                    IcodeFileRow(
                        name = file.name,
                        leading = { FileTypeIcon(file.name, folder = false, size = IcodeSizes.IconXl, parent = parentName(file.location)) },
                        onClick = { gateway.dispatch(ICodeIntent.OpenFile(file.location)) },
                        detail = FileFolders.of(file.location, root, state.activeWorkspace),
                        detailIsPath = true,
                        trailing = { Text(RelativeTime.since(file.changedAt), style = IcodeType.Caption) },
                    )
                }
            }
        }
        BottomPanelBar(
            problemCount = state.problems.size,
            onTerminal = { actions.openPanel(ToolPanelTab.Terminal) },
            onProblems = { actions.openPanel(ToolPanelTab.Problems) },
        )
    }
    if (showCreateFile) CreateFileSheet(parentLocation = null, onDismiss = { showCreateFile = false })
}

/** One way in: its icon and its name, a tile of the grid, all the same height. */
@Composable
private fun WayIn(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    IcodeCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            Icon(icon, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
            Text(text, style = IcodeType.Body, color = IcodeColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
