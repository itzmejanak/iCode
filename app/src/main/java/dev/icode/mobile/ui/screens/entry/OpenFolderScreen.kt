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
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.FolderBrowserPurpose
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** Directory browser used when opening an existing workspace folder. */
@Composable
fun OpenFolderScreen(actions: NavigationActions) {
    val gateway = LocalWorkbenchGateway.current
    val browser = LocalWorkbenchSnapshot.current.folderBrowser
    LaunchedEffect(browser) {
        if (browser == null) {
            gateway.dispatch(WorkbenchIntent.StartFolderBrowser(FolderBrowserPurpose.OpenWorkspace))
        }
    }
    val folders = browser?.folders.orEmpty()
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Open Folder", onBack = actions.back)
        MonoLine(
            text = browser?.displayPath.orEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .background(IcodeColors.Surface)
                .padding(IcodeSpacing.Space3),
            color = IcodeColors.Accent,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).padding(IcodeSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro),
        ) {
            item {
                Text(
                    "↰  ..",
                    style = IcodeType.Mono.copy(color = IcodeColors.Accent),
                    modifier = Modifier
                        .clickable(enabled = browser != null && browser.currentPath != browser.rootPath) {
                            gateway.dispatch(WorkbenchIntent.BrowseParentFolder)
                        }
                        .padding(IcodeSpacing.Space2),
                )
            }
            items(folders, key = { it.path }) { folder ->
                IcodeListRow(
                    card = true,
                    title = folder.name,
                    subtitle = "${countOf(folder.directoryCount, "directory", "directories")}, ${countOf(folder.fileCount, "file")}",
                    titleStyle = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                    onClick = { gateway.dispatch(WorkbenchIntent.BrowseFolder(folder.path)) },
                    leading = { Icon(Icons.Outlined.Folder, contentDescription = null, tint = IcodeColors.Accent) },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = IcodeColors.TextSecondary,
                        )
                    },
                )
            }
        }
        PrimaryButton(
            if (browser?.purpose == FolderBrowserPurpose.CreateFolderDestination) "Use This Folder" else "Open This Folder",
            { gateway.dispatch(WorkbenchIntent.ConfirmFolderSelection) },
            Modifier.fillMaxWidth().padding(IcodeSpacing.Space5),
            enabled = browser != null,
        )
    }
}
