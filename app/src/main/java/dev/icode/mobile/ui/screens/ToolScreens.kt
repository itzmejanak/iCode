package dev.icode.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.KeyChip
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.Severity
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.DemoActions
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import kotlinx.coroutines.delay

@Composable
fun ToolScreenHost(screen: DemoScreen, actions: DemoActions) {
    when (screen) {
        DemoScreen.SourceControl -> SourceControlScreen(actions)
        DemoScreen.DiffView -> DiffViewScreen(actions)
        DemoScreen.QuickOpen -> QuickOpenScreen(actions)
        DemoScreen.BranchSelector -> BranchSelectorScreen(actions)
        DemoScreen.EditorFind -> EditorFindScreen(actions)
        DemoScreen.TerminalExpanded -> TerminalScreen(actions)
        DemoScreen.ProblemsPanel -> ProblemsScreen(actions)
        DemoScreen.ConflictResolution -> ConflictResolutionScreen(actions)
        DemoScreen.CommandPalette -> CommandPaletteScreen(actions)
        DemoScreen.OutputPanel -> OutputPanelScreen(actions)
        DemoScreen.CommitPush -> CommitPushScreen(actions)
        DemoScreen.PullFetchProgress -> PullProgressScreen(actions)
        DemoScreen.GitAuth -> GitAuthScreen(actions)
        else -> Unit
    }
}

@Composable
private fun SourceControlScreen(actions: DemoActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val staged = state.gitChanges.filter { it.staged }
    val changes = state.gitChanges.filterNot { it.staged }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            state.activeBranch,
            actions = {
                Text("↑2 ↓1", style = IcodeType.MonoSmall, modifier = Modifier.clickable { actions.navigate(DemoScreen.BranchSelector) })
                IconButton(onClick = { actions.navigate(DemoScreen.BranchSelector) }) {
                    Icon(Icons.Outlined.MoreVert, "Branches", tint = IcodeColors.TextPrimary)
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                IcodeCard(Modifier.fillMaxWidth()) {
                    MonoLine("feat: implement on-device secure auth workflow", color = IcodeColors.TextMuted, style = IcodeType.MonoSmall)
                    PrimaryButton("Commit & Push", { actions.navigate(DemoScreen.CommitPush) }, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            item { GitSectionHeader("STAGED CHANGES (${staged.size})", "UNSTAGE ALL") }
            items(staged) { change ->
                GitChangeRow(change) { gateway.dispatch(WorkbenchIntent.UnstageFile(change.path)) }
            }
            item { GitSectionHeader("CHANGES (${changes.size})", "STAGE ALL") }
            items(changes) { change ->
                GitChangeRow(change) {
                    if (change.status == "M") actions.navigate(DemoScreen.DiffView)
                    else if (change.status == "D") actions.navigate(DemoScreen.DeleteConfirmation)
                    else gateway.dispatch(WorkbenchIntent.StageFile(change.path))
                }
            }
            item {
                SecondaryButton("Resolve Sample Conflict", { actions.navigate(DemoScreen.ConflictResolution) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GitSectionHeader(title: String, action: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SectionLabel(title)
        Text(action, style = IcodeType.Label.copy(color = IcodeColors.Accent))
    }
}

@Composable
private fun GitChangeRow(change: GitChange, onClick: () -> Unit) {
    val color = when (change.status) {
        "A", "?" -> IcodeColors.Green
        "M" -> IcodeColors.Amber
        else -> IcodeColors.Error
    }
    IcodeCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MonoLine(change.status, color = color, style = IcodeType.MonoSmall)
            Column(Modifier.weight(1f)) {
                Text(
                    change.name,
                    style = IcodeType.Body.copy(
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (change.status == "D") TextDecoration.LineThrough else TextDecoration.None,
                    ),
                )
                MonoLine(change.path, style = IcodeType.MonoSmall)
            }
            Text(if (change.staged) "⊟" else "⊞", style = IcodeType.Body.copy(color = IcodeColors.TextSecondary))
        }
    }
}

@Composable
private fun DiffViewScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("App.tsx", onBack = actions.back, actions = { StatusLabel("MODIFIED", IcodeColors.Amber) })
        MonoLine("@@ -15,8 +15,12 @@", modifier = Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(14.dp), color = IcodeColors.TextSecondary)
        MonoLine("... 24 lines hidden ...", modifier = Modifier.fillMaxWidth().padding(10.dp), color = IcodeColors.TextMuted, style = IcodeType.MonoSmall)
        DiffLine("15 15", "const config = {", IcodeColors.Background)
        DiffLine("16  -", "secure: false,", Color(0xFF3F1E1B), IcodeColors.Error)
        DiffLine("16  +", "secure: true,", Color(0xFF182E22), IcodeColors.Green)
        DiffLine("17  +", "algorithm: 'RS256',", Color(0xFF182E22), IcodeColors.Green)
        DiffLine("17 18", "subscribe();", IcodeColors.Background)
        Spacer(Modifier.weight(1f))
        TwoColumnActions(
            left = { SecondaryButton("⌃ Prev Change", {}, Modifier.fillMaxWidth()) },
            right = { SecondaryButton("Next Change ⌄", {}, Modifier.fillMaxWidth()) },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun DiffLine(numbers: String, content: String, background: Color, color: Color = IcodeColors.TextPrimary) {
    Row(Modifier.fillMaxWidth().background(background).padding(vertical = 3.dp)) {
        MonoLine(numbers, Modifier.width(56.dp).padding(horizontal = 6.dp), IcodeColors.TextSecondary, IcodeType.MonoSmall)
        MonoLine(content, color = color)
    }
}

@Composable
private fun QuickOpenScreen(actions: DemoActions) {
    val files = listOf(
        Triple("App.tsx", "src/components/App.tsx", IcodeColors.Accent),
        Triple("useAuth.ts", "src/hooks/useAuth.ts", IcodeColors.Blue),
        Triple("package.json", "root/package.json", IcodeColors.Amber),
    )
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        IcodeCard(Modifier.fillMaxWidth(), borderColor = IcodeColors.Outline) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Search, null, tint = IcodeColors.Accent)
                Text("Go to File...", style = IcodeType.Body)
            }
        }
        IcodeCard(Modifier.fillMaxWidth().padding(top = 6.dp), padding = androidx.compose.foundation.layout.PaddingValues(5.dp)) {
            Column {
                files.forEachIndexed { index, (name, path, color) ->
                    Row(
                        Modifier.fillMaxWidth().background(if (index == 0) IcodeColors.SurfaceElevated else Color.Transparent).clickable { actions.navigate(DemoScreen.WorkspaceEditor) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (name.endsWith("json")) Icons.Outlined.Description else Icons.Outlined.Code, null, tint = color)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(name, style = IcodeType.Body.copy(color = if (index == 0) IcodeColors.Accent else IcodeColors.TextPrimary))
                            MonoLine(path, style = IcodeType.MonoSmall)
                        }
                        Text("◔", color = IcodeColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchSelectorScreen(actions: DemoActions) {
    val branches = listOf("main", "develop", "feature/auth", "feature/dashboard", "bugfix/login-error")
    Box(Modifier.fillMaxSize().background(IcodeColors.BackgroundDeep)) {
        Column(Modifier.padding(16.dp)) {
            Text("Source Control", style = IcodeType.Title.copy(color = IcodeColors.TextMuted))
            MonoLine("feature/auth", modifier = Modifier.padding(top = 12.dp), color = IcodeColors.TextMuted)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(IcodeColors.Surface, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).padding(16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).background(IcodeColors.Outline, RoundedCornerShape(2.dp)))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Switch Branch", modifier = Modifier.weight(1f), style = IcodeType.Title)
                Icon(Icons.Outlined.Close, "Close", tint = IcodeColors.TextSecondary, modifier = Modifier.clickable(onClick = actions.back))
            }
            IcodeCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                Row { Icon(Icons.Outlined.Search, null, tint = IcodeColors.TextSecondary); Text("Search branches...", Modifier.padding(start = 8.dp), style = IcodeType.Caption) }
            }
            branches.forEach { branch ->
                Row(
                    Modifier.fillMaxWidth().background(if (branch == "feature/auth") IcodeColors.SurfaceElevated else Color.Transparent).clickable(onClick = actions.back).padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonoLine(branch, modifier = Modifier.weight(1f), color = if (branch == "feature/auth") IcodeColors.Accent else IcodeColors.TextPrimary)
                    if (branch == "feature/auth") Icon(Icons.Outlined.CheckCircle, null, tint = IcodeColors.Accent, modifier = Modifier.size(18.dp))
                }
            }
            Divider()
            Text("⊕  Create New Branch", modifier = Modifier.padding(vertical = 12.dp), style = IcodeType.Body.copy(color = IcodeColors.Accent, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun EditorFindScreen(actions: DemoActions) {
    var query by remember { mutableStateOf("component") }
    var replacement by remember { mutableStateOf("element") }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("App.tsx", onBack = actions.back)
        Column(Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FindInput(query, { query = it }, Modifier.weight(1f), "3 of 7")
                KeyChip("⌃")
                KeyChip("⌄")
                Icon(Icons.Outlined.Close, null, tint = IcodeColors.TextSecondary, modifier = Modifier.clickable(onClick = actions.back))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FindInput(replacement, { replacement = it }, Modifier.weight(1f))
                KeyChip("Replace")
                KeyChip("All")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { KeyChip("Aa"); KeyChip("Ab*"); KeyChip(".*") }
        }
        Column(Modifier.weight(1f).background(IcodeColors.BackgroundDeep).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MonoLine("1   import React from 'components';")
            MonoLine("2   const ActiveComponent = () => {")
            MonoLine("3     return <div>Active Workspace</div>;")
            MonoLine("4   };")
        }
    }
}

@Composable
private fun FindInput(value: String, onChange: (String) -> Unit, modifier: Modifier, trailing: String? = null) {
    IcodeCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value, onChange, Modifier.weight(1f), textStyle = IcodeType.Mono, cursorBrush = androidx.compose.ui.graphics.SolidColor(IcodeColors.Accent))
            if (trailing != null) Text(trailing, style = IcodeType.Caption)
        }
    }
}

@Composable
private fun TerminalScreen(actions: DemoActions) {
    val lines = listOf(
        "~/my-project $ npm run dev" to IcodeColors.TextPrimary,
        "> my-project@0.1.0 dev" to IcodeColors.TextSecondary,
        "> next dev" to IcodeColors.TextSecondary,
        "- ready started server on 0.0.0.0:3000, url: http://localhost:3000" to IcodeColors.Blue,
        "- event compiled client and server successfully in 312ms (18 modules)" to IcodeColors.Green,
        "Attention: Next.js now collects completely anonymous telemetry." to IcodeColors.TextPrimary,
        "~/my-project $ █" to IcodeColors.TextPrimary,
    )
    Column(Modifier.fillMaxSize().background(IcodeColors.BackgroundDeep)) {
        IcodeTopBar("my-project", onBack = actions.back)
        Row(Modifier.fillMaxWidth().background(IcodeColors.Surface).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            TerminalTab("● bash", IcodeColors.Green, true)
            TerminalTab("● build", IcodeColors.Error, false)
            Text("+", modifier = Modifier.clickable { actions.navigate(DemoScreen.OutputPanel) }.padding(16.dp), style = IcodeType.Title)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.MoreVert, null, tint = IcodeColors.TextSecondary, modifier = Modifier.padding(10.dp))
        }
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            lines.forEach { (text, color) -> MonoLine(text, color = color) }
        }
        Row(Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ESC", "TAB", "CTRL", "ALT", "◀", "▲", "▼", "▶").forEach { KeyChip(it) }
        }
    }
}

@Composable
private fun TerminalTab(text: String, color: Color, active: Boolean) {
    Box(Modifier.background(if (active) IcodeColors.Background else IcodeColors.Surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(text, style = IcodeType.MonoSmall.copy(color = color))
    }
}

@Composable
private fun ProblemsScreen(actions: DemoActions) {
    val diagnostics = LocalWorkbenchSnapshot.current.diagnostics
    var filter by remember { mutableStateOf(Severity.Error) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Problems", onBack = actions.back, actions = { Icon(Icons.Outlined.FilterAlt, "Filter", tint = IcodeColors.TextPrimary) })
        Row(Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(12.dp), horizontalArrangement = Arrangement.SpaceAround) {
            StatusLabel("3 Errors", IcodeColors.Error)
            StatusLabel("5 Warnings", IcodeColors.Amber)
            StatusLabel("2 Info", IcodeColors.Blue)
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Severity.entries.forEach { severity ->
                KeyChip(severity.name + if (severity == Severity.Error) "s" else "s", active = filter == severity, modifier = Modifier.clickable { filter = severity })
            }
        }
        LazyColumn(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            val grouped = diagnostics.groupBy { it.file }
            grouped.forEach { (file, items) ->
                item { Text("⌄  $file", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp)) }
                items(items) { diagnostic -> DiagnosticRow(diagnostic) }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(item: Diagnostic) {
    val color = when (item.severity) { Severity.Error -> IcodeColors.Error; Severity.Warning -> IcodeColors.Amber; Severity.Info -> IcodeColors.Blue }
    IcodeCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.WarningAmber, null, tint = color, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                MonoLine(item.message)
                Row { MonoLine(item.location, style = IcodeType.MonoSmall); MonoLine("  ${item.source}", color = color, style = IcodeType.MonoSmall) }
            }
        }
    }
}

@Composable
private fun ConflictResolutionScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("auth.ts", onBack = actions.back, actions = { StatusLabel("Conflict", IcodeColors.Error) })
        Row(Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyChip("Accept Current", Modifier.weight(1f), active = true, compact = true)
            KeyChip("Accept Incoming", Modifier.weight(1f), compact = true)
            KeyChip("Accept Both", Modifier.weight(1f), compact = true)
            KeyChip("Compare", Modifier.weight(1f), compact = true)
        }
        Column(Modifier.weight(1f).background(IcodeColors.BackgroundDeep).padding(top = 12.dp)) {
            MonoLine("export const validateToken = (token: string) => {", modifier = Modifier.padding(horizontal = 12.dp), color = IcodeColors.TextMuted)
            ConflictBand("<<<<<<< HEAD (Current Change)", Color(0xFF3F1E1B), IcodeColors.Error)
            MonoLine("  const decoded = jwt.verify(token, SECRET);\n  return decoded.sub !== undefined;", modifier = Modifier.padding(12.dp))
            ConflictBand("=======", IcodeColors.Outline, IcodeColors.TextMuted)
            MonoLine("  const verified = oauth.decode(token);\n  return verified.isValid && verified.scope === 'write';", modifier = Modifier.padding(12.dp))
            ConflictBand(">>>>>>> feature/oauth (Incoming Change)", Color(0xFF172738), IcodeColors.Blue)
            MonoLine("};", modifier = Modifier.padding(horizontal = 12.dp), color = IcodeColors.TextMuted)
        }
        Text("Conflicted — resolve all conflicts and mark as resolved", modifier = Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(14.dp), style = IcodeType.Caption.copy(color = IcodeColors.Amber), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        PrimaryButton("Mark as Resolved", { actions.navigate(DemoScreen.SourceControl) }, Modifier.fillMaxWidth().padding(16.dp))
    }
}

@Composable
private fun ConflictBand(text: String, background: Color, color: Color) {
    MonoLine(text, Modifier.fillMaxWidth().background(background).padding(horizontal = 12.dp, vertical = 4.dp), color)
}

@Composable
private fun CommandPaletteScreen(actions: DemoActions) {
    val commands = listOf(
        Triple("Git commands", "Git: Push Branch", "Ctrl+Shift+P"),
        Triple("", "Git: Pull (from origin)", "Ctrl+Shift+L"),
        Triple("", "Git: Commit Staged", "Ctrl+Alt+C"),
        Triple("File commands", "File: Save Current", "Ctrl+S"),
    )
    Column(Modifier.fillMaxSize().background(IcodeColors.BackgroundDeep).padding(top = 12.dp)) {
        IcodeCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp), borderColor = IcodeColors.Accent) {
            Row { Icon(Icons.Outlined.Search, null, tint = IcodeColors.Accent); Text("Git: push_", Modifier.padding(start = 8.dp), style = IcodeType.Body) }
        }
        commands.forEachIndexed { index, (group, command, shortcut) ->
            if (group.isNotEmpty()) SectionLabel(group, Modifier.padding(start = 12.dp, top = 12.dp, bottom = 5.dp))
            Row(
                Modifier.fillMaxWidth().background(if (index == 0) IcodeColors.SurfaceElevated else Color.Transparent).clickable {
                    actions.navigate(if (command.contains("Push")) DemoScreen.CommitPush else DemoScreen.WorkspaceEditor)
                }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (command.startsWith("Git")) Icons.Outlined.AccountTree else Icons.Outlined.Description, null, tint = if (index == 0) IcodeColors.Accent else IcodeColors.TextSecondary, modifier = Modifier.size(18.dp))
                Text(command, Modifier.weight(1f).padding(start = 9.dp), style = IcodeType.Body.copy(color = if (index == 0) IcodeColors.Accent else IcodeColors.TextPrimary))
                MonoLine(shortcut, style = IcodeType.MonoSmall)
            }
        }
    }
}

@Composable
private fun OutputPanelScreen(actions: DemoActions) {
    val logs = listOf(
        "[14:30:11] [Git] Executing: git status --porcelain" to IcodeColors.TextSecondary,
        "[14:30:12] [Git] success: M src/App.tsx, A src/components/auth-guard.tsx" to IcodeColors.Green,
        "[14:30:15] [Git] Executing: git push origin feature/auth" to IcodeColors.TextSecondary,
        "[14:30:16] [Git] error: rejected - non-fast-forward" to IcodeColors.Error,
        "[14:30:16] [Git] hint: pull before pushing again to merge remote changes" to IcodeColors.Amber,
    )
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("App.tsx", onBack = actions.back, actions = { Icon(Icons.Outlined.MoreVert, null, tint = IcodeColors.TextPrimary) })
        MonoLine("import React, { useState } from 'react'; export const App...", Modifier.fillMaxWidth().background(IcodeColors.BackgroundDeep).padding(16.dp), IcodeColors.TextMuted)
        Row(Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Terminal", style = IcodeType.Body.copy(color = IcodeColors.TextSecondary))
            Text("Output", modifier = Modifier.padding(14.dp), style = IcodeType.Body.copy(color = IcodeColors.Accent, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.weight(1f))
            KeyChip("Channel: Git ⌄")
        }
        Column(Modifier.weight(1f).background(IcodeColors.Surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            logs.forEach { (line, color) -> MonoLine(line, color = color, style = IcodeType.MonoSmall) }
        }
    }
}

@Composable
private fun CommitPushScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Source Control", onBack = actions.back, actions = { Icon(Icons.Outlined.MoreVert, null, tint = IcodeColors.TextPrimary) })
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IcodeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccountTree, null, tint = IcodeColors.Green)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("feature/auth", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                        MonoLine("1 commit ahead, 0 behind", color = IcodeColors.Green, style = IcodeType.MonoSmall)
                    }
                    PrimaryButton("Push (1)", { actions.navigate(DemoScreen.PullFetchProgress) })
                }
            }
            IcodeCard(Modifier.fillMaxWidth()) { StatusLabel("Working tree clean. No unstaged changes.", IcodeColors.Green) }
            IcodeCard(Modifier.fillMaxWidth(), elevated = true) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Last Commit")
                    MonoLine("commit a9d2b1f8c7e923", color = IcodeColors.Accent)
                    Text("feat: implement secure auth flow on Android", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                    MonoLine("Author: Dev Android <dev@icode.com>", style = IcodeType.MonoSmall)
                    MonoLine("Time: 5 mins ago", style = IcodeType.MonoSmall)
                }
            }
            TwoColumnActions(
                left = { SecondaryButton("FETCH", { actions.navigate(DemoScreen.PullFetchProgress) }, Modifier.fillMaxWidth()) },
                right = { SecondaryButton("PULL", { actions.navigate(DemoScreen.PullFetchProgress) }, Modifier.fillMaxWidth()) },
            )
        }
    }
}

@Composable
private fun PullProgressScreen(actions: DemoActions) {
    LaunchedEffect(actions.staticDemo) {
        if (!actions.staticDemo) {
            delay(2_200)
            actions.navigate(DemoScreen.CommitPush)
        }
    }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Syncing Repository")
        Column(Modifier.padding(20.dp)) {
            Text("Pulling from origin main...", modifier = Modifier.align(Alignment.CenterHorizontally), style = IcodeType.Title)
            ProgressTrack(0.52f, Modifier.fillMaxWidth().padding(vertical = 16.dp))
            IcodeCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(
                        "$ git pull origin main",
                        "From github.com:icode/icode-subsystem",
                        "* branch          main      -> FETCH_HEAD",
                        "Updating 2fd9b31..a8d29b0",
                        "Fast-forward",
                    ).forEach { MonoLine(it, style = IcodeType.MonoSmall) }
                    MonoLine("src/utils/token-utils.ts | 12 ++++++++----", color = IcodeColors.Green, style = IcodeType.MonoSmall)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        SecondaryButton("Cancel Operation", actions.back, Modifier.fillMaxWidth().padding(20.dp))
    }
}

@Composable
private fun GitAuthScreen(actions: DemoActions) {
    var selected by remember { mutableStateOf("SSH Public/Private Key") }
    val methods = listOf("SSH Public/Private Key", "Personal Access Token (PAT)", "Username & Password")
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Git Credentials", onBack = actions.back)
        Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Authentication Method", Modifier.padding(bottom = 2.dp))
            methods.forEach { method ->
                IcodeCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (selected == method) IcodeColors.Accent else IcodeColors.Outline,
                    onClick = { selected = method },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (selected == method) IcodeColors.Accent else IcodeColors.Outline, 12.dp)
                        Text(method, modifier = Modifier.weight(1f).padding(start = 10.dp), style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                        if (selected == method) MonoLine("ACTIVE", color = IcodeColors.Accent, style = IcodeType.MonoSmall)
                    }
                    if (selected == method && method.startsWith("SSH")) {
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Key Path", style = IcodeType.Caption)
                            MonoLine("~/.ssh/id_rsa_icode")
                            Text("Key Status", style = IcodeType.Caption)
                            MonoLine("Loaded, configured for github.com", color = IcodeColors.Green, style = IcodeType.MonoSmall)
                            SecondaryButton("Regenerate SSH Key", {}, Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        PrimaryButton("Test Git Connection", actions.back, Modifier.fillMaxWidth().padding(20.dp))
    }
}
