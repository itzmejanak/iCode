package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.domain.ImportBrowserState
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/**
 * Import's browser of phone storage (docs/workspace-storage-plan.md §4.5; the Open Folder frame,
 * 2003:117): the folder's path, `..`, the folders there with their counts, and **Import <folder>**.
 * Without All Files Access the page is that permission's explanation, in the words onboarding would
 * use; granted, the listing follows.
 */
@Composable
fun ImportBrowserScreen(actions: NavigationActions) {
    val gateway = LocalWorkbenchGateway.current
    val browser = LocalWorkbenchSnapshot.current.importBrowser
    // Where it was last, or where the earlier workspaces' notice asked, read again.
    LaunchedEffect(Unit) { gateway.dispatch(WorkbenchIntent.OpenImportBrowser()) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Import", onBack = actions.back)
        when {
            // The first read is on its way.
            browser == null -> Unit
            !browser.access -> PermissionExplanation(
                permission = SystemPermission.Storage,
                onAllow = { gateway.dispatch(WorkbenchIntent.RequestSystemPermission(SystemPermission.Storage)) },
                onNotNow = actions.back,
                modifier = Modifier.weight(1f).padding(IcodeSpacing.Space6),
            )
            else -> FolderListing(browser, gateway, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FolderListing(browser: ImportBrowserState, gateway: WorkbenchGateway, modifier: Modifier) {
    Column(modifier) {
        MonoLine(
            text = browser.displayPath,
            modifier = Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(IcodeSpacing.Space3),
            color = IcodeColors.Accent,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).padding(IcodeSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro),
        ) {
            if (!browser.atRoot) {
                item {
                    Text(
                        "↰  ..",
                        style = IcodeType.Mono.copy(color = IcodeColors.Accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { gateway.dispatch(WorkbenchIntent.BrowseImportParent) }
                            .padding(IcodeSpacing.Space2),
                    )
                }
            }
            if (browser.folders.isEmpty()) {
                item { EmptyState(title = "No folders here", icon = Icons.Outlined.Folder, modifier = Modifier.padding(top = IcodeSpacing.Space6)) }
            }
            items(browser.folders, key = { it.path }) { folder ->
                IcodeListRow(
                    card = true,
                    title = folder.name,
                    subtitle = "${countOf(folder.directoryCount, "directory", "directories")}, ${countOf(folder.fileCount, "file")}",
                    titleStyle = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                    onClick = { gateway.dispatch(WorkbenchIntent.BrowseImportFolder(folder.path)) },
                    leading = { Icon(Icons.Outlined.Folder, contentDescription = null, tint = IcodeColors.Accent) },
                    trailing = {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
                    },
                )
            }
        }
        // Internal storage itself is where folders are, not one to import.
        PrimaryButton(
            if (browser.atRoot) "Choose a Folder to Import" else "Import ${browser.name}",
            { gateway.dispatch(WorkbenchIntent.ChooseImportFolder(browser.currentPath)) },
            Modifier.fillMaxWidth().padding(IcodeSpacing.Space5),
            enabled = !browser.atRoot,
        )
    }
}
