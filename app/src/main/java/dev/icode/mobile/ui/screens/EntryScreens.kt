package dev.icode.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledField
import dev.icode.mobile.designsystem.LocalOpenDemoCatalog
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PageHeading
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.DemoActions
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import kotlinx.coroutines.delay

@Composable
fun EntryScreenHost(screen: DemoScreen, actions: DemoActions) {
    when (screen) {
        DemoScreen.Splash -> SplashScreen(actions)
        DemoScreen.RuntimeInstalling -> RuntimeInstallingScreen(actions)
        DemoScreen.RuntimeError -> RuntimeErrorScreen(actions)
        DemoScreen.PermissionExplanation -> PermissionScreen(actions)
        DemoScreen.LauncherHome -> LauncherScreen(actions, empty = false)
        DemoScreen.LauncherEmpty -> LauncherScreen(actions, empty = true)
        DemoScreen.CloneRepository -> CloneRepositoryScreen(actions)
        DemoScreen.CloneProgress -> CloneProgressScreen(actions)
        DemoScreen.CreateFolder -> CreateFolderScreen(actions)
        DemoScreen.OpenFolderBrowser -> OpenFolderScreen(actions)
        else -> Unit
    }
}

@Composable
private fun SplashScreen(actions: DemoActions) {
    val openCatalog = LocalOpenDemoCatalog.current
    LaunchedEffect(actions.staticDemo) {
        if (!actions.staticDemo) {
            delay(1_100)
            actions.navigate(DemoScreen.RuntimeInstalling)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(24.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                modifier = Modifier
                    .clickable(onClick = openCatalog)
                    .background(Color.Transparent, RoundedCornerShape(4.dp))
                    .padding(2.dp),
            ) {
                IcodeCard(borderColor = IcodeColors.Accent, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    Text("[ i ]", style = IcodeType.Mono.copy(color = IcodeColors.Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("iCode", style = IcodeType.PageTitle.copy(fontSize = 32.sp))
                Text("MOBILE IDE", style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent))
            }
        }
        Text("Code anywhere.", style = IcodeType.Body.copy(color = IcodeColors.TextMuted))
    }
}

@Composable
private fun RuntimeInstallingScreen(actions: DemoActions) {
    LaunchedEffect(actions.staticDemo) {
        if (!actions.staticDemo) {
            delay(1_800)
            actions.navigate(DemoScreen.PermissionExplanation)
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        PageHeading(
            title = "Installing Runtime",
            description = "Preparing the isolated Linux container subsystem for building and running code.",
        )
        Spacer(Modifier.weight(1f))
        ProgressTrack(0.705f, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLine("70.5% COMPLETE", color = IcodeColors.Accent, style = IcodeType.MonoSmall)
            MonoLine("847 MB / 1.2 GB", style = IcodeType.MonoSmall)
        }
        IcodeCard(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            onClick = { actions.navigate(DemoScreen.RuntimeError) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Terminal, null, tint = IcodeColors.Accent, modifier = Modifier.size(18.dp))
                MonoLine("Extracting Ubuntu ARM64 rootfs...")
            }
        }
        Spacer(Modifier.weight(1.35f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Storage, null, tint = IcodeColors.TextMuted, modifier = Modifier.size(18.dp))
            Text("Allocated: 2.4 GB | Available storage: 45.2 GB", style = IcodeType.Caption)
        }
    }
}

@Composable
private fun RuntimeErrorScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IcodeCard(borderColor = IcodeColors.Error, padding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                Icon(Icons.Outlined.WarningAmber, null, tint = IcodeColors.Error, modifier = Modifier.size(18.dp))
            }
            Text("Installation Failed", style = IcodeType.PageTitle)
        }
        Text(
            "Checksum verification failed. The download may be corrupted or interrupted.",
            modifier = Modifier.padding(top = 10.dp),
            style = IcodeType.Body,
            color = IcodeColors.TextSecondary,
        )
        IcodeCard(Modifier.fillMaxWidth().padding(top = 24.dp), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Column {
                SectionLabel("Technical Details", Modifier.padding(12.dp))
                Divider()
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    MonoLine("ERR_HASH_MISMATCH: rootfs.tar.gz", color = IcodeColors.Error, style = IcodeType.MonoSmall)
                    MonoLine("Expected: 8e5fd22b7a...", color = IcodeColors.TextMuted, style = IcodeType.MonoSmall)
                    MonoLine("Received: 3fa9120de4...", color = IcodeColors.TextMuted, style = IcodeType.MonoSmall)
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Info, null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(17.dp))
            Text("Storage stable: 45.2 GB free space.", style = IcodeType.Caption)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Retry Installation", { actions.navigate(DemoScreen.RuntimeInstalling) }, Modifier.fillMaxWidth())
        SecondaryButton("View Diagnostics", { actions.navigate(DemoScreen.About) }, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun PermissionScreen(actions: DemoActions) {
    val bullets = listOf(
        "Allows you to read, edit, and compile repositories and source files in their original locations.",
        "Permits opening any accessible storage directory as a workspace without file-picker restrictions.",
        "Guarantees security. Private app spaces of other native tools remain entirely inaccessible.",
        "You remain in control. This system-level developer permission is revocable at any time in Android settings.",
    )
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        PageHeading(
            title = "All Files Access",
            description = "iCode is an on-device local development tool. To function correctly, Android requires the MANAGE_EXTERNAL_STORAGE permission.",
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            bullets.forEachIndexed { index, text ->
                Row(Modifier.padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.size(24.dp).background(IcodeColors.SurfaceElevated, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index + 1}", style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent))
                    }
                    Text(text, modifier = Modifier.weight(1f), style = IcodeType.Body)
                }
            }
        }
        PrimaryButton("Open System Settings", { actions.navigate(DemoScreen.LauncherHome) }, Modifier.fillMaxWidth())
        SecondaryButton("Not Now", { actions.navigate(DemoScreen.LauncherEmpty) }, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun LauncherScreen(actions: DemoActions, empty: Boolean) {
    val workspaces = LocalWorkbenchSnapshot.current.workspaces
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = "iCode",
            actions = {
                IconButton(onClick = { actions.navigate(DemoScreen.SettingsRoot) }) {
                    Icon(Icons.Outlined.Settings, "Settings", tint = IcodeColors.TextPrimary)
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LauncherAction(Icons.Outlined.FolderOpen, "Open Folder", Modifier.weight(1f)) { actions.navigate(DemoScreen.OpenFolderBrowser) }
            LauncherAction(Icons.Outlined.AccountTree, "Clone Repo", Modifier.weight(1f)) { actions.navigate(DemoScreen.CloneRepository) }
            LauncherAction(Icons.Outlined.CreateNewFolder, "New Folder", Modifier.weight(1f)) { actions.navigate(DemoScreen.CreateFolder) }
        }
        if (empty) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
            ) {
                Spacer(Modifier.height(32.dp))
                Box(Modifier.size(64.dp).background(IcodeColors.Surface, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Folder, null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(30.dp))
                }
                Text("No recent workspaces", style = IcodeType.Title)
                Text(
                    "Open a folder, clone a repository, or create a new project\nto get started.",
                    style = IcodeType.Body,
                    color = IcodeColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(Modifier.padding(horizontal = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Recent Workspaces", Modifier.padding(bottom = 4.dp))
                workspaces.forEach { workspace ->
                    WorkspaceRow(workspace) { actions.navigate(DemoScreen.EditorEmpty) }
                }
            }
        }
        StatusLabel("RUNTIME HEALTHY", IcodeColors.Green, Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
    }
}

@Composable
private fun LauncherAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    IcodeCard(modifier = modifier.height(74.dp), onClick = onClick) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = IcodeColors.Accent, modifier = Modifier.size(24.dp))
            Text(label, style = IcodeType.Label.copy(color = IcodeColors.TextPrimary))
        }
    }
}

@Composable
private fun WorkspaceRow(item: dev.icode.mobile.domain.WorkspaceSummary, onClick: () -> Unit) {
    IcodeCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(IcodeColors.SurfaceElevated, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                Icon(
                    if (!item.available) Icons.Outlined.WarningAmber else Icons.Outlined.Folder,
                    null,
                    tint = if (!item.available) IcodeColors.Amber else IcodeColors.Accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                    if (!item.available) Text("• UNAVAILABLE", style = IcodeType.Label.copy(color = IcodeColors.Amber, fontSize = 9.sp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonoLine(item.path, modifier = Modifier.weight(1f), style = IcodeType.MonoSmall)
                    if (item.branch != null) {
                        Text(item.branch, style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent, fontSize = 9.sp))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Icon(Icons.Outlined.MoreVert, null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(18.dp))
                Text(item.lastOpened, style = IcodeType.Caption)
            }
        }
    }
}

@Composable
private fun CloneRepositoryScreen(actions: DemoActions) {
    var url by remember { mutableStateOf("https://github.com/icode-dev/compiler-core.git") }
    var path by remember { mutableStateOf("~/projects/compiler-core") }
    var branch by remember { mutableStateOf("main") }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Clone Repository", onBack = actions.back)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            EditableField("Repository URL", url) { url = it }
            EditableField("Destination Directory", path) { path = it }
            EditableField("Checkout Branch (Optional)", branch) { branch = it }
            IcodeCard(Modifier.fillMaxWidth(), onClick = { actions.navigate(DemoScreen.GitAuth) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Lock, null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Text("AUTHENTICATION", modifier = Modifier.weight(1f), style = IcodeType.Label.copy(color = IcodeColors.TextPrimary))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = IcodeColors.TextSecondary)
                }
            }
        }
        PrimaryButton("Clone Repository", { actions.navigate(DemoScreen.CloneProgress) }, Modifier.fillMaxWidth().padding(20.dp))
    }
}

@Composable
private fun EditableField(label: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        IcodeCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = IcodeType.Mono,
                singleLine = false,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(IcodeColors.Accent),
            )
        }
    }
}

@Composable
private fun CloneProgressScreen(actions: DemoActions) {
    LaunchedEffect(actions.staticDemo) {
        if (!actions.staticDemo) {
            delay(2_000)
            actions.navigate(DemoScreen.WorkspaceExplorer)
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("GIT CLONE IN PROGRESS", style = IcodeType.Label.copy(color = IcodeColors.Accent))
        Text("compiler-core", style = IcodeType.PageTitle, modifier = Modifier.padding(top = 10.dp))
        MonoLine("Dest: ~/projects/compiler-core", modifier = Modifier.padding(top = 6.dp), style = IcodeType.MonoSmall)
        Divider()
        ProgressTrack(0.67f, Modifier.fillMaxWidth().padding(top = 20.dp))
        IcodeCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoLine("$ git clone https://github.com/icode...", color = IcodeColors.TextSecondary)
                MonoLine("> Cloning into 'compiler-core'...", color = IcodeColors.Green)
                MonoLine("> Receiving objects: 67%... 234/350", color = IcodeColors.Accent)
            }
        }
        Spacer(Modifier.weight(1f))
        SecondaryButton("Cancel Clone", actions.back, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CreateFolderScreen(actions: DemoActions) {
    var name by remember { mutableStateOf("auth-v2_") }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Create Folder", onBack = actions.back)
        Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            LabeledField("Destination Path", "~/projects/icode-subsystem/src") {
                Text("Change", style = IcodeType.Label.copy(color = IcodeColors.Accent))
            }
            EditableField("Folder Name", name) { name = it }
            StatusLabel("Path is valid and available", IcodeColors.Green)
            IcodeCard(Modifier.fillMaxWidth(), elevated = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Storage, null, tint = IcodeColors.Blue)
                    Column {
                        Text("Device Available Storage", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                        MonoLine("12.4 GB free of 128 GB (Ext4 partition)", style = IcodeType.MonoSmall)
                    }
                }
            }
        }
        PrimaryButton("Create Folder", { actions.navigate(DemoScreen.WorkspaceExplorer) }, Modifier.fillMaxWidth().padding(20.dp))
    }
}

@Composable
private fun OpenFolderScreen(actions: DemoActions) {
    val folders = listOf(
        "src" to "12 directories, 4 files",
        "public" to "2 directories, 15 files",
        "tests" to "1 directory, 6 files",
    )
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Open Folder", onBack = actions.back)
        MonoLine(
            "root / projects / icode-subsystem",
            modifier = Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(12.dp),
            color = IcodeColors.Accent,
        )
        LazyColumn(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            item { Text("↰  ..", style = IcodeType.Mono.copy(color = IcodeColors.Accent), modifier = Modifier.padding(10.dp)) }
            items(folders) { (name, meta) ->
                IcodeCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Folder, null, tint = IcodeColors.Accent)
                        Column(Modifier.weight(1f)) {
                            Text(name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                            MonoLine(meta, style = IcodeType.MonoSmall)
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = IcodeColors.TextSecondary)
                    }
                }
            }
        }
        PrimaryButton("Open This Folder", { actions.navigate(DemoScreen.EditorEmpty) }, Modifier.fillMaxWidth().padding(20.dp))
    }
}
