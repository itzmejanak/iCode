package dev.icode.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.KeyChip
import dev.icode.mobile.designsystem.LabeledField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.LanguageService
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.DemoActions
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

@Composable
fun SettingsScreenHost(screen: DemoScreen, actions: DemoActions) {
    when (screen) {
        DemoScreen.SettingsRoot -> SettingsRootScreen(actions)
        DemoScreen.SettingsEditor -> EditorSettingsScreen(actions)
        DemoScreen.SettingsLanguages -> LanguageServicesScreen(actions)
        DemoScreen.SettingsLanguageDetail -> LanguageDetailScreen(actions)
        DemoScreen.SettingsRuntime -> RuntimeSettingsScreen(actions)
        DemoScreen.AppearanceSettings -> AppearanceScreen(actions)
        DemoScreen.TerminalSettings -> TerminalSettingsScreen(actions)
        DemoScreen.StorageSettings -> StorageSettingsScreen(actions)
        DemoScreen.About -> AboutScreen(actions)
        DemoScreen.WorkspaceSettings -> WorkspaceSettingsScreen(actions)
        else -> Unit
    }
}

private data class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: DemoScreen,
)

@Composable
private fun SettingsRootScreen(actions: DemoActions) {
    val categories = listOf(
        SettingsCategory("Appearance", "Theme, fonts, and display", Icons.Outlined.Palette, DemoScreen.AppearanceSettings),
        SettingsCategory("Editor", "Editing behavior, formatting, autosave", Icons.Outlined.Code, DemoScreen.SettingsEditor),
        SettingsCategory("Terminal", "Shell, extra keys, scrollback", Icons.Outlined.Terminal, DemoScreen.TerminalSettings),
        SettingsCategory("Source Control", "Git preferences and diff display", Icons.Outlined.AccountTree, DemoScreen.GitAuth),
        SettingsCategory("Languages", "Syntax, highlighting, language servers", Icons.Outlined.Public, DemoScreen.SettingsLanguages),
        SettingsCategory("Runtime", "Ubuntu environment, processes, health", Icons.Outlined.Storage, DemoScreen.SettingsRuntime),
        SettingsCategory("Storage", "Workspace paths and cleanup", Icons.Outlined.Folder, DemoScreen.StorageSettings),
        SettingsCategory("Application", "Updates, about, diagnostics", Icons.Outlined.Info, DemoScreen.About),
    )
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Settings",
            onBack = actions.back,
            actions = { IconButton(onClick = {}) { Icon(Icons.Outlined.Search, "Search settings", tint = IcodeColors.TextPrimary) } },
        )
        Row(Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Application Settings", style = IcodeType.Label.copy(color = IcodeColors.Accent))
            Text(
                "Switch to Workspace  ↻",
                style = IcodeType.Caption,
                modifier = Modifier.clickable { actions.navigate(DemoScreen.WorkspaceSettings) },
            )
        }
        LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                SettingRow(
                    title = category.title,
                    subtitle = category.subtitle,
                    leadingIcon = category.icon,
                    onClick = { actions.navigate(category.destination) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = IcodeColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun EditorSettingsScreen(actions: DemoActions) {
    var fontSize by remember { mutableIntStateOf(14) }
    var tabSize by remember { mutableIntStateOf(2) }
    var wordWrap by remember { mutableStateOf(true) }
    var formatOnSave by remember { mutableStateOf(true) }
    var lineNumbers by remember { mutableStateOf(true) }
    var minimap by remember { mutableStateOf(false) }
    var brackets by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Editor Settings", onBack = actions.back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("General")
            SettingRow("Font Size", Modifier.fillMaxWidth()) { Stepper(fontSize, { if (fontSize > 9) fontSize-- }, { if (fontSize < 30) fontSize++ }) }
            SettingRow("Font Family", Modifier.fillMaxWidth()) { MonoLine("IBM Plex Mono", style = IcodeType.MonoSmall) }
            SettingRow("Tab Size", Modifier.fillMaxWidth()) { Segment(listOf("2", "4"), tabSize.toString()) { tabSize = it.toInt() } }
            SettingRow("Word Wrap", Modifier.fillMaxWidth()) { IcodeToggle(wordWrap) { wordWrap = it } }
            SectionLabel("Save", Modifier.padding(top = 8.dp))
            SettingRow("Auto Save", Modifier.fillMaxWidth()) { Text("After 1 second⌄", style = IcodeType.Caption.copy(color = IcodeColors.Accent)) }
            SettingRow("Format On Save", Modifier.fillMaxWidth()) { IcodeToggle(formatOnSave) { formatOnSave = it } }
            SectionLabel("Display", Modifier.padding(top = 8.dp))
            SettingRow("Line Numbers", Modifier.fillMaxWidth()) { IcodeToggle(lineNumbers) { lineNumbers = it } }
            SettingRow("Minimap", Modifier.fillMaxWidth()) { IcodeToggle(minimap) { minimap = it } }
            SettingRow("Bracket Matching", Modifier.fillMaxWidth()) { IcodeToggle(brackets) { brackets = it } }
        }
    }
}

@Composable
private fun Stepper(value: Int, minus: () -> Unit, plus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KeyChip("−", modifier = Modifier.clickable(onClick = minus))
        MonoLine(value.toString(), color = IcodeColors.Accent)
        KeyChip("+", modifier = Modifier.clickable(onClick = plus))
    }
}

@Composable
private fun Segment(values: List<String>, active: String, onSelect: (String) -> Unit) {
    Row(Modifier.background(IcodeColors.SurfaceElevated, RoundedCornerShape(4.dp))) {
        values.forEach { value ->
            Box(
                Modifier.background(if (value == active) IcodeColors.Accent else Color.Transparent, RoundedCornerShape(3.dp)).clickable { onSelect(value) }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(value, style = IcodeType.Caption.copy(color = if (value == active) Color.Black else IcodeColors.TextMuted)) }
        }
    }
}

@Composable
private fun LanguageServicesScreen(actions: DemoActions) {
    val services = LocalWorkbenchSnapshot.current.languageServices
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Language Services",
            onBack = actions.back,
            actions = { IconButton(onClick = {}) { Icon(Icons.Outlined.Add, "Add language service", tint = IcodeColors.TextPrimary) } },
        )
        Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            services.forEach { service -> LanguageServiceRow(service) { actions.navigate(DemoScreen.SettingsLanguageDetail) } }
            Spacer(Modifier.weight(1f))
            Text(
                "Install a language server in the terminal, then add its configuration here.",
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                style = IcodeType.Caption,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LanguageServiceRow(service: LanguageService, onClick: () -> Unit) {
    IcodeCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp).background(IcodeColors.SurfaceElevated, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                Text(service.initials, style = IcodeType.Label.copy(color = if (service.running) IcodeColors.Blue else IcodeColors.Amber))
            }
            Column(Modifier.weight(1f)) {
                Text(service.name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                MonoLine(service.patterns, style = IcodeType.MonoSmall)
            }
            StatusLabel(if (service.running) "Running" else "Stopped", if (service.running) IcodeColors.Green else IcodeColors.TextSecondary)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = IcodeColors.TextSecondary)
        }
    }
}

@Composable
private fun LanguageDetailScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("TypeScript Service", onBack = actions.back, actions = { StatusLabel("Running", IcodeColors.Green) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Configuration")
            LabeledField("Command", "typescript-language-server --stdio")
            LabeledField("Root Markers", ".tsconfig.json, package.json")
            LabeledField("File Patterns", "*.ts, *.tsx, *.js, *.jsx")
            SectionLabel("Initialization Options", Modifier.padding(top = 4.dp))
            IcodeCard(Modifier.fillMaxWidth()) {
                MonoLine("{\n  \"preferences\": {\n    \"importModuleSpecifier\": \"non-relative\"\n  }\n}", color = IcodeColors.Amber)
            }
            SectionLabel("Status", Modifier.padding(top = 4.dp))
            IcodeCard(Modifier.fillMaxWidth()) {
                DetailValue("Process ID", "24018")
                DetailValue("Uptime", "1h 45m")
                Text("Capabilities", style = IcodeType.Caption, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("completion", "hover", "diagnostics", "formatting").forEach { KeyChip(it) }
                }
            }
            SecondaryButton("Restart Server", {}, Modifier.fillMaxWidth())
            DangerButton("Remove Service", {}, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = IcodeType.Caption)
        MonoLine(value)
    }
}

@Composable
private fun RuntimeSettingsScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Runtime Environment", onBack = actions.back)
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            IcodeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).background(IcodeColors.Green, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, null, tint = Color.Black)
                    }
                    Column {
                        Text("Runtime Healthy", style = IcodeType.Title.copy(color = IcodeColors.Green))
                        Text("Ubuntu 22.04 ARM64 via PRoot", style = IcodeType.Caption)
                    }
                }
            }
            SectionLabel("Technical Details")
            IcodeCard(Modifier.fillMaxWidth()) {
                listOf(
                    "Architecture" to "aarch64",
                    "Runtime Home" to ".../files/usr/runtime",
                    "Installed Packages" to "247 packages",
                    "Disk Usage" to "1.8 GB of 16 GB",
                    "Uptime" to "3h 42m",
                    "PRoot Version" to "5.3.0",
                ).forEach { (label, value) -> DetailValue(label, value) }
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton("Repair Runtime", {}, Modifier.fillMaxWidth())
            Text("Reinstall runtime files. Preserves home directory and installed tools.", style = IcodeType.Caption)
            DangerButton("Reset Runtime", { actions.navigate(DemoScreen.DeleteConfirmation) }, Modifier.fillMaxWidth())
            Text("Remove everything including installed tools and home directory.", style = IcodeType.Caption)
        }
    }
}

@Composable
private fun AppearanceScreen(actions: DemoActions) {
    var theme by remember { mutableStateOf("Dark") }
    var scale by remember { mutableFloatStateOf(0.58f) }
    var compact by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Appearance", subtitle = "Theme, fonts, and display", onBack = actions.back)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SectionLabel("Color Theme")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf("Dark", "Light", "AMOLED").forEach { option ->
                    IcodeCard(
                        modifier = Modifier.weight(1f),
                        borderColor = if (theme == option) IcodeColors.Accent else IcodeColors.Outline,
                        onClick = { theme = option },
                    ) {
                        Box(Modifier.fillMaxWidth().height(28.dp).background(if (option == "Light") IcodeColors.TextPrimary else Color.Black))
                        Text(option, modifier = Modifier.padding(top = 8.dp), style = IcodeType.Body, color = if (theme == option) IcodeColors.TextPrimary else IcodeColors.TextSecondary)
                    }
                }
            }
            SectionLabel("Font Size & Scale")
            IcodeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aa", style = IcodeType.Caption)
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = IcodeColors.Accent, activeTrackColor = IcodeColors.Accent, inactiveTrackColor = IcodeColors.SurfaceTrack),
                    )
                    Text("Aa", style = IcodeType.Body)
                }
                Box(Modifier.fillMaxWidth().background(IcodeColors.BackgroundDeep).padding(10.dp)) { MonoLine("const ide = iCode.init();") }
            }
            SectionLabel("UI Density")
            SettingRow("Compact Layout", Modifier.fillMaxWidth()) { IcodeToggle(compact) { compact = it } }
            SectionLabel("Accent Color")
            SettingRow("Ember Accent", Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(16.dp).background(IcodeColors.Accent, RoundedCornerShape(50)))
                    MonoLine("#FF6B2C", color = IcodeColors.Accent, style = IcodeType.MonoSmall)
                }
            }
        }
    }
}

@Composable
private fun TerminalSettingsScreen(actions: DemoActions) {
    var fontSize by remember { mutableIntStateOf(13) }
    var cursor by remember { mutableStateOf("Block") }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Terminal Settings", subtitle = "Shell, extra keys, scrollback", onBack = actions.back)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Shell Path")
            SettingRow("/bin/bash", Modifier.fillMaxWidth()) { Text("EDIT", style = IcodeType.Label.copy(color = IcodeColors.Accent)) }
            SectionLabel("Typography")
            SettingRow("Font Family", Modifier.fillMaxWidth()) { MonoLine("IBM Plex Mono", style = IcodeType.MonoSmall) }
            SettingRow("Font Size", Modifier.fillMaxWidth()) { Stepper(fontSize, { fontSize-- }, { fontSize++ }) }
            SectionLabel("History")
            SettingRow("Scrollback Limit", Modifier.fillMaxWidth()) { MonoLine("10000 lines", color = IcodeColors.Accent) }
            SectionLabel("Extra Keys Row")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Esc", "Tab", "Ctrl", "Alt", "Fn", "◀", "▲", "▼", "▶").forEach { KeyChip(it, active = it == "Ctrl" || it == "Alt") }
            }
            SectionLabel("Cursor Style")
            SettingRow("Cursor Type", Modifier.fillMaxWidth()) { Segment(listOf("Block", "Underline", "Bar"), cursor) { cursor = it } }
        }
    }
}

@Composable
private fun StorageSettingsScreen(actions: DemoActions) {
    val state = LocalWorkbenchSnapshot.current
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Storage Settings", subtitle = "Disk usage and workspace folders", onBack = actions.back)
        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionLabel("Storage Usage") }
            item {
                IcodeCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1.8 GB Used", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                        Text("14.2 GB Free of 16 GB", style = IcodeType.Caption)
                    }
                    Row(Modifier.fillMaxWidth().height(8.dp).padding(top = 6.dp)) {
                        Box(Modifier.weight(1.1f).height(8.dp).background(IcodeColors.Accent))
                        Box(Modifier.weight(0.42f).height(8.dp).background(IcodeColors.Blue))
                        Box(Modifier.weight(0.2f).height(8.dp).background(IcodeColors.Amber))
                        Box(Modifier.weight(0.08f).height(8.dp).background(IcodeColors.SurfaceTrack))
                    }
                    Text("● Runtime (1.1GB)    ● Workspaces (410MB)    ● Cache (220MB)", modifier = Modifier.padding(top = 12.dp), style = IcodeType.Caption)
                }
            }
            item { SectionLabel("Workspace Paths", Modifier.padding(top = 8.dp)) }
            items(state.workspaces) { workspace ->
                IcodeCard(Modifier.fillMaxWidth()) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(workspace.name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                            MonoLine(workspace.path, style = IcodeType.MonoSmall)
                            MonoLine(if (workspace.name.startsWith("external")) "1.2 GB  · ${workspace.lastOpened}" else "244 MB  · ${workspace.lastOpened}", color = IcodeColors.Accent, style = IcodeType.MonoSmall)
                        }
                        Icon(Icons.Outlined.MoreVert, null, tint = IcodeColors.TextSecondary)
                    }
                }
            }
            item { SectionLabel("Cleanup Operations", Modifier.padding(top = 8.dp)) }
            item {
                TwoColumnActions(
                    left = { SecondaryButton("Clear Cache", {}, Modifier.fillMaxWidth()) },
                    right = { DangerButton("Remove Stale", {}, Modifier.fillMaxWidth()) },
                )
            }
        }
    }
}

@Composable
private fun AboutScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("About iCode", subtitle = "Application specs & diagnostic details", onBack = actions.back)
        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IcodeCard(borderColor = IcodeColors.Accent) { MonoLine("[ i ]", color = IcodeColors.Accent, style = IcodeType.Mono.copy(fontSize = 22.sp)) }
                    Text("iCode Mobile IDE", modifier = Modifier.padding(top = 10.dp), style = IcodeType.PageTitle.copy(fontSize = 23.sp))
                    Text("NATIVE ANDROID WORKBENCH", style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent))
                }
            }
            item { SectionLabel("Build Specifications") }
            item {
                IcodeCard(Modifier.fillMaxWidth()) {
                    listOf(
                        "App Version" to "0.1.0-demo",
                        "Runtime Version" to "Ubuntu 24.04 ARM64",
                        "Editor Engine" to "Sora (integration planned)",
                        "Min Android API" to "30 (Android 11)",
                    ).forEach { (label, value) -> DetailValue(label, value) }
                }
            }
            item { SectionLabel("Diagnostics", Modifier.padding(top = 6.dp)) }
            items(listOf("Check for Updates", "Export Diagnostics", "View Licenses", "View Source")) { action ->
                SettingRow(action, Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = IcodeColors.TextSecondary) }
            }
            item { SectionLabel("Resources", Modifier.padding(top = 6.dp)) }
            item { ResourceCard("Documentation", "Guides, tutorials and CLI references") }
            item { ResourceCard("Release Notes", "What's new in this demo") }
            item { ResourceCard("Report Issue", "Open a ticket on the project tracker") }
        }
    }
}

@Composable
private fun ResourceCard(title: String, subtitle: String) {
    IcodeCard(Modifier.fillMaxWidth()) {
        Text(title, style = IcodeType.Body.copy(color = IcodeColors.Accent, fontWeight = FontWeight.SemiBold))
        Text(subtitle, style = IcodeType.Caption)
    }
}

@Composable
private fun WorkspaceSettingsScreen(actions: DemoActions) {
    var tabType by remember { mutableStateOf("Spaces") }
    var autoFetch by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Workspace Settings",
            subtitle = "icode-subsystem",
            onBack = actions.back,
            actions = { Text("WORKSPACE", style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent)) },
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Editor Overrides")
            IcodeCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Indent Size", style = IcodeType.Body)
                    MonoLine("2", color = IcodeColors.Accent)
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Global inherited value is 4", style = IcodeType.Caption)
                    Text("Reset to Inherited", style = IcodeType.Label.copy(color = IcodeColors.TextSecondary, fontSize = 10.sp))
                }
            }
            SettingRow("Tab Type", Modifier.fillMaxWidth()) { Segment(listOf("Spaces", "Tabs"), tabType) { tabType = it } }
            SectionLabel("Source Control", Modifier.padding(top = 8.dp))
            SettingRow("Git Auto-Fetch", Modifier.fillMaxWidth()) { IcodeToggle(autoFetch) { autoFetch = it } }
            SectionLabel("File Watcher Excludes", Modifier.padding(top = 8.dp))
            IcodeCard(Modifier.fillMaxWidth()) {
                MonoLine("**/node_modules/**\n**/build/**\n**/.git/**", color = IcodeColors.TextSecondary)
                Divider()
                Text("+ Add Exclude Pattern", modifier = Modifier.padding(top = 10.dp), style = IcodeType.Body.copy(color = IcodeColors.Accent, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
