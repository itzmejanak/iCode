package dev.icode.mobile.ui.screens.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
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
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.domain.ImportBrowserState
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.FolderBreadcrumb
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Import's browser of phone storage (docs/workspace-storage-plan.md §4.5; the Open Folder frame,
 * 2003:117): the breadcrumb from sdcard (the way up), the folders there with their counts, and
 * **Import <folder>**.
 * Without All Files Access the page is that permission's explanation, in the words onboarding would
 * use; granted, the listing follows.
 */
@Composable
fun ImportBrowserScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val browser = LocalICodeSnapshot.current.importBrowser
    // Where it was last, or where the earlier workspaces' notice asked, read again.
    LaunchedEffect(Unit) { gateway.dispatch(ICodeIntent.OpenImportBrowser()) }
    // Back goes up a folder, as in the Workspaces page's file explorer; at sdcard it leaves.
    val up = browser?.takeIf { it.access }?.parent
    val back = { if (up != null) gateway.dispatch(ICodeIntent.BrowseImportFolder(up)) else actions.back() }
    BackHandler(enabled = up != null) { back() }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Import", onBack = back)
        when {
            // The first read is on its way.
            browser == null -> Unit
            !browser.access -> PermissionExplanation(
                permission = SystemPermission.Storage,
                onAllow = { gateway.dispatch(ICodeIntent.RequestSystemPermission(SystemPermission.Storage)) },
                onNotNow = actions.back,
                modifier = Modifier.weight(1f).padding(IcodeSpacing.Space6),
            )
            else -> FolderListing(browser, gateway, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FolderListing(browser: ImportBrowserState, gateway: ICodeGateway, modifier: Modifier) {
    Column(modifier) {
        FolderBreadcrumb(browser.crumbs) { path -> gateway.dispatch(ICodeIntent.BrowseImportFolder(path)) }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = IcodeSpacing.Space5),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro),
        ) {
            if (browser.folders.isEmpty()) {
                item { EmptyState(title = "No folders here", icon = Icons.Outlined.Folder, modifier = Modifier.padding(top = IcodeSpacing.Space6)) }
            }
            items(browser.folders, key = { it.path }) { folder ->
                IcodeListRow(
                    card = true,
                    title = folder.name,
                    subtitle = "${countOf(folder.directoryCount, "directory", "directories")}, ${countOf(folder.fileCount, "file")}",
                    titleStyle = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                    onClick = { gateway.dispatch(ICodeIntent.BrowseImportFolder(folder.path)) },
                    leading = { Icon(Icons.Outlined.Folder, contentDescription = null, tint = IcodeColors.Accent) },
                    trailing = {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
                    },
                )
            }
        }
        // sdcard itself is where folders are, not one to import.
        PrimaryButton(
            if (browser.atRoot) "Choose a Folder to Import" else "Import ${browser.name}",
            { gateway.dispatch(ICodeIntent.ChooseImportFolder(browser.currentPath)) },
            Modifier.fillMaxWidth().padding(IcodeSpacing.Space5),
            enabled = !browser.atRoot,
        )
    }
}
