package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.ShareTrack
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.domain.files.ExplorerView
import dev.icode.mobile.domain.files.ScanScope
import dev.icode.mobile.domain.files.StorageInsights
import dev.icode.mobile.domain.files.TrashRules
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.screens.entry.icon
import dev.icode.mobile.ui.screens.workspace.categoryTint
import dev.icode.mobile.ui.screens.workspace.icon

/**
 * Manage storage (docs/explorer-gaps-plan.md §3.10): where it looks — All, Private or Shared — and
 * one scan of it, made when the page first opens and again on ↻. The bar stacks each kind's share in
 * its colour, Git's history last; under it, how much in how many files, and when it scanned or how far
 * it has got, with Stop. Each category, then Largest files, Duplicates and Trash, opens the explorer's
 * view of the same name; one with nothing in it is greyed. Git's history is counted but opens nothing.
 * Without All Files Access, Shared's part is a line asking for it.
 */
@Composable
fun ManageStorageScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val overview = state.explorer.storage
    val scope = overview?.scope ?: ScanScope.All
    LaunchedEffect(Unit) {
        gateway.dispatch(FilesIntent.ReadTrash)
        if (overview == null) gateway.dispatch(FilesIntent.Scan(ScanScope.All))
    }
    val open = { view: ExplorerView ->
        gateway.dispatch(ICodeIntent.BrowseView(view))
        actions.navigate(Route.Workspaces)
    }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Manage storage", onBack = actions.back) {
            IconButton(onClick = { gateway.dispatch(FilesIntent.Scan(scope)) }, enabled = overview?.scanning == null) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Scan again", tint = IcodeColors.TextPrimary)
            }
        }
        IcodePageColumn {
            SegmentedControl(ScanScope.entries.map { it.label }, scope.label, Modifier.fillMaxWidth()) { label ->
                ScanScope.entries.first { it.label == label }.takeIf { it != scope }?.let { gateway.dispatch(FilesIntent.Scan(it)) }
            }
            overview?.let { found ->
                IcodeCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                        ShareTrack(StorageInsights.shares(found).map { (category, share) -> share to (category?.let { categoryTint(it) } ?: IcodeColors.TextMuted) }, Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                            Text(StorageInsights.total(found), style = IcodeType.Body, modifier = Modifier.weight(1f))
                            Text(StorageInsights.status(found, System.currentTimeMillis()), style = IcodeType.Caption)
                        }
                        if (found.scanning != null) SecondaryButton("Stop", { gateway.dispatch(FilesIntent.StopScan) }, Modifier.fillMaxWidth())
                        if (found.sharedUnread) {
                            Text("Shared is not counted: the file explorer needs All Files Access to read phone storage.", style = IcodeType.Caption, color = IcodeColors.Amber)
                            SecondaryButton("Allow", { gateway.dispatch(ICodeIntent.RequestSystemPermission(SystemPermission.Storage)) }, Modifier.fillMaxWidth())
                        }
                    }
                }
                IcodeSection("Categories") {
                    found.categories.forEach { share ->
                        val view = ExplorerView.Category(share.category, found.scope)
                        ViewRow(share.category.label, countOf(share.files, "file"), ByteSize.format(share.bytes), share.category.icon, categoryTint(share.category), { open(view) }.takeIf { share.files > 0 })
                    }
                    SettingRow("Git history (.git)", Modifier.fillMaxWidth(), subtitle = "Git's own, in no category", leadingIcon = Icons.Outlined.AccountTree, leadingTint = IcodeColors.TextMuted) {
                        Text(ByteSize.format(found.gitBytes), style = IcodeType.Mono)
                    }
                }
                IcodeSection("Clean up") {
                    val largest = ExplorerView.Largest(found.scope)
                    ViewRow(largest.title, "The ${StorageInsights.LARGEST} largest", ByteSize.format(found.largestBytes), largest.icon, IcodeColors.Accent, { open(largest) }.takeIf { found.files > 0 })
                    val duplicates = ExplorerView.Duplicates(found.scope)
                    ViewRow(
                        duplicates.title,
                        countOf(found.duplicateGroups, "group"),
                        "${ByteSize.format(found.duplicateBytes)} extra",
                        duplicates.icon,
                        IcodeColors.Accent,
                        { open(duplicates) }.takeIf { found.duplicateGroups > 0 },
                    )
                    val trash = TrashRules.summary(state.explorer.trash)
                    ViewRow(ExplorerView.Trash.title, countOf(trash.items, "item"), ByteSize.format(trash.bytes), ExplorerView.Trash.icon, IcodeColors.Accent, { open(ExplorerView.Trash) }.takeIf { trash.items > 0 })
                }
            }
        }
    }
}

/**
 * One of the explorer's views as Manage storage lists it: its [icon] in its [tint], what it [holds],
 * and how much ([size]); [onOpen] opens the view. One that holds nothing has none, and is greyed.
 */
@Composable
private fun ViewRow(title: String, holds: String, size: String, icon: ImageVector, tint: Color, onOpen: (() -> Unit)?) {
    SettingRow(title, Modifier.fillMaxWidth(), subtitle = holds, leadingIcon = icon, leadingTint = if (onOpen == null) IcodeColors.TextMuted else tint, onClick = onOpen) {
        Text(size, style = IcodeType.Mono, color = if (onOpen == null) IcodeColors.TextMuted else IcodeColors.TextPrimary)
    }
}
