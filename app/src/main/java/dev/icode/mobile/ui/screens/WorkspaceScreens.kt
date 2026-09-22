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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.DemoActions
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

@Composable
fun WorkspaceScreenHost(screen: DemoScreen, actions: DemoActions) {
    when (screen) {
        DemoScreen.WorkspaceExplorer -> WorkspaceExplorerScreen(actions)
        DemoScreen.WorkspaceEditor -> WorkspaceEditorScreen(actions)
        DemoScreen.WorkspaceSearch -> WorkspaceSearchScreen(actions)
        DemoScreen.EditorEmpty -> EditorEmptyScreen(actions)
        else -> Unit
    }
}

@Composable
private fun WorkspaceHeader(actions: DemoActions, actionsContent: @Composable () -> Unit = {}) {
    val state = LocalWorkbenchSnapshot.current
    IcodeTopBar(
        title = state.activeWorkspace,
        subtitle = state.activePath,
        actions = {
            actionsContent()
            IconButton(onClick = { actions.navigate(DemoScreen.WorkspaceSettings) }) {
                Icon(Icons.Outlined.MoreVert, "Workspace settings", tint = IcodeColors.TextPrimary)
            }
        },
    )
}

@Composable
private fun WorkspaceExplorerScreen(actions: DemoActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    Column(Modifier.fillMaxSize()) {
        WorkspaceHeader(actions)
        Row(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(IcodeColors.Surface),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ActivityIcon(Icons.Outlined.FolderOpen, active = true) {}
                ActivityIcon(Icons.Outlined.Search) { actions.navigate(DemoScreen.WorkspaceSearch) }
                ActivityIcon(Icons.Outlined.AccountTree) { actions.navigate(DemoScreen.SourceControl) }
            }
            Column(Modifier.weight(1f)) {
                SectionLabel("Workspace Explorer", Modifier.padding(12.dp))
                state.files.forEach { entry ->
                    ExplorerRow(
                        entry = entry,
                        onClick = {
                            if (entry.kind == FileKind.Folder) gateway.dispatch(WorkbenchIntent.ToggleFolder(entry.path))
                            else actions.navigate(DemoScreen.WorkspaceEditor)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (active) Box(Modifier.align(Alignment.BottomCenter).height(2.dp).width(20.dp).background(IcodeColors.Accent))
        Icon(icon, null, tint = if (active) IcodeColors.Accent else IcodeColors.TextSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ExplorerRow(entry: FileEntry, onClick: () -> Unit) {
    val icon = when (entry.kind) {
        FileKind.Folder -> if (entry.expanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder
        FileKind.TypeScript -> Icons.Outlined.Code
        FileKind.Json, FileKind.Markdown -> Icons.Outlined.Description
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (entry.name == "helpers.ts") IcodeColors.Surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = (10 + entry.depth * 18).dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (entry.kind == FileKind.Folder) {
            Icon(
                if (entry.expanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                null,
                tint = IcodeColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        } else Spacer(Modifier.width(16.dp))
        Icon(
            icon,
            null,
            tint = when (entry.kind) {
                FileKind.TypeScript -> IcodeColors.Blue
                FileKind.Json -> IcodeColors.Amber
                FileKind.Markdown -> IcodeColors.TextSecondary
                FileKind.Folder -> if (entry.expanded) IcodeColors.Accent else IcodeColors.TextSecondary
            },
            modifier = Modifier.size(18.dp),
        )
        Text(entry.name, modifier = Modifier.weight(1f), style = IcodeType.Body, color = if (entry.name == "README.md") IcodeColors.TextSecondary else IcodeColors.TextPrimary)
        if (entry.gitStatus != null) {
            MonoLine(
                entry.gitStatus,
                color = if (entry.gitStatus == "M" || entry.gitStatus == "•") IcodeColors.Amber else IcodeColors.Green,
                style = IcodeType.MonoSmall,
            )
        }
    }
}

@Composable
private fun WorkspaceEditorScreen(actions: DemoActions) {
    val codeLines = listOf(
        codeLine("import", " React, { ", "useState", " } from 'react';"),
        codeLine("import", " { Container, Header } from './components';", "", ""),
        AnnotatedString("// Main Workspace Entry"),
        codeLine("export const", " App = () => {", "", ""),
        codeLine("", "  const [active, setActive] = ", "useState", "(true);"),
        codeLine("", "  const [loading, setLoading] = ", "", ""),
        AnnotatedString("  return ("),
        codeLine("", "    <Container>", "", ""),
        codeLine("", "      <Header title=", "", "\"iCode Mobile\" />"),
        AnnotatedString("    </Container>"),
    )
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = "App.tsx  •",
            actions = {
                IconButton(onClick = {}) { Icon(Icons.Outlined.Save, "Save", tint = IcodeColors.TextPrimary) }
                IconButton(onClick = { actions.navigate(DemoScreen.EditorFind) }) { Icon(Icons.Outlined.Search, "Find", tint = IcodeColors.TextPrimary) }
                IconButton(onClick = { actions.navigate(DemoScreen.CommandPalette) }) { Icon(Icons.Outlined.MoreVert, "Commands", tint = IcodeColors.TextPrimary) }
            },
        )
        Row(Modifier.fillMaxWidth().height(38.dp).horizontalScroll(rememberScrollState())) {
            EditorTab("App.tsx", true) {}
            EditorTab("index.ts", false) {}
            EditorTab("utils.ts", false) {}
        }
        LazyColumn(Modifier.weight(1f).background(IcodeColors.BackgroundDeep).padding(top = 8.dp)) {
            items(codeLines.size) { index ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    MonoLine("${index + 1}", Modifier.width(32.dp).padding(end = 8.dp), IcodeColors.TextMuted, IcodeType.MonoSmall)
                    Text(codeLines[index], style = IcodeType.Mono, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).background(IcodeColors.Surface).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Terminal", style = IcodeType.Body.copy(color = IcodeColors.TextSecondary), modifier = Modifier.clickable { actions.navigate(DemoScreen.TerminalExpanded) })
            Row(Modifier.clickable { actions.navigate(DemoScreen.ProblemsPanel) }, verticalAlignment = Alignment.CenterVertically) {
                Text("Problems", style = IcodeType.Body.copy(color = IcodeColors.Error, fontWeight = FontWeight.SemiBold))
                Box(Modifier.padding(start = 6.dp).size(20.dp).background(IcodeColors.Error, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Text("3", style = IcodeType.Label.copy(color = Color.Black))
                }
            }
            Spacer(Modifier.weight(1f))
            Text("⌃", style = IcodeType.Body.copy(color = IcodeColors.TextSecondary))
        }
    }
}

private fun codeLine(keyword: String, before: String, symbol: String, after: String): AnnotatedString = buildAnnotatedString {
    if (keyword.isNotEmpty()) pushStyle(SpanStyle(color = IcodeColors.Accent, fontWeight = FontWeight.SemiBold))
    append(keyword)
    if (keyword.isNotEmpty()) pop()
    append(before)
    if (symbol.isNotEmpty()) pushStyle(SpanStyle(color = IcodeColors.Blue))
    append(symbol)
    if (symbol.isNotEmpty()) pop()
    append(after)
}

@Composable
private fun EditorTab(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(92.dp)
            .fillMaxHeight()
            .background(if (active) IcodeColors.Surface else IcodeColors.Background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = IcodeType.Caption.copy(color = if (active) IcodeColors.Accent else IcodeColors.TextMuted))
    }
}

@Composable
private fun WorkspaceSearchScreen(actions: DemoActions) {
    var matchCase by remember { mutableStateOf(true) }
    val results = listOf(
        Triple("src/App.tsx", "3 matches", listOf("1   import React, { useState } from 'react';", "5   const [active, setActive] = useState(true);", "6   const [loading, setLoading] = useState(false);")),
        Triple("src/hooks/useAuth.ts", "2 matches", listOf("2   import { useState, useEffect } from 'react';", "8   const [user, setUser] = useState(null);")),
    )
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Search Workspace", onBack = actions.back)
        IcodeCard(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Search, null, tint = IcodeColors.TextSecondary)
                MonoLine("useState", modifier = Modifier.weight(1f))
                Text("⊗", color = IcodeColors.TextSecondary)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchChip("Match Case", matchCase) { matchCase = !matchCase }
            SearchChip("Whole Word", false) {}
            SearchChip("Regex", false) {}
        }
        Text("5 results in 2 files", modifier = Modifier.padding(16.dp), style = IcodeType.Caption)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            results.forEach { (file, count, lines) ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⌄  $file", style = IcodeType.Mono.copy(color = IcodeColors.Accent, fontWeight = FontWeight.SemiBold))
                        Text("  ($count)", style = IcodeType.MonoSmall)
                    }
                    lines.forEach { MonoLine(it, modifier = Modifier.padding(start = 18.dp), style = IcodeType.MonoSmall) }
                }
            }
        }
    }
}

@Composable
private fun SearchChip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (active) IcodeColors.Accent else IcodeColors.Background, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, style = IcodeType.Caption.copy(color = if (active) Color.Black else IcodeColors.TextSecondary))
    }
}

@Composable
private fun EditorEmptyScreen(actions: DemoActions) {
    val state = LocalWorkbenchSnapshot.current
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(state.activeWorkspace, subtitle = state.activePath, onBack = { actions.navigate(DemoScreen.LauncherHome) })
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EmptyAction(Icons.Outlined.Search, "Quick Open", Modifier.weight(1f)) { actions.navigate(DemoScreen.QuickOpen) }
            EmptyAction(Icons.Outlined.Add, "New File", Modifier.weight(1f)) { actions.navigate(DemoScreen.WorkspaceEditor) }
            EmptyAction(Icons.Outlined.Terminal, "Open Terminal", Modifier.weight(1f)) { actions.navigate(DemoScreen.TerminalExpanded) }
        }
        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IcodeCard(borderColor = IcodeColors.Outline) {
                MonoLine("[ i ]", color = IcodeColors.TextSecondary, style = IcodeType.Mono.copy(fontSize = 22.sp))
            }
            Text("Select a file to begin coding", modifier = Modifier.padding(top = 12.dp), style = IcodeType.Body, color = IcodeColors.TextSecondary)
        }
        Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Recent Files", Modifier.padding(top = 26.dp, bottom = 4.dp))
            RecentFile("auth-guard.tsx", "src/components/auth-guard.tsx", "10m ago") { actions.navigate(DemoScreen.WorkspaceEditor) }
            RecentFile("token-utils.ts", "src/utils/token-utils.ts", "2h ago") { actions.navigate(DemoScreen.WorkspaceEditor) }
        }
        Row(Modifier.fillMaxWidth().height(48.dp).background(IcodeColors.Surface).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Terminal", style = IcodeType.Body.copy(color = IcodeColors.TextSecondary), modifier = Modifier.clickable { actions.navigate(DemoScreen.TerminalExpanded) })
            Text("Problems  3", modifier = Modifier.padding(start = 20.dp).clickable { actions.navigate(DemoScreen.ProblemsPanel) }, style = IcodeType.Body.copy(color = IcodeColors.Error, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun EmptyAction(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    IcodeCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(9.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = IcodeColors.Accent, modifier = Modifier.size(17.dp))
            Text(text, style = IcodeType.Caption.copy(color = IcodeColors.TextPrimary), maxLines = 1)
        }
    }
}

@Composable
private fun RecentFile(name: String, path: String, age: String, onClick: () -> Unit) {
    IcodeCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Description, null, tint = IcodeColors.Accent)
            Column(Modifier.weight(1f)) {
                Text(name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                MonoLine(path, style = IcodeType.MonoSmall)
            }
            Text(age, style = IcodeType.Caption)
        }
    }
}
