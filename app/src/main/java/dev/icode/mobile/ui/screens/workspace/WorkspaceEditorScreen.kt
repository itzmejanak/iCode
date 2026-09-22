package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.BottomPanelBar
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.EditorTab
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

private val demoCodeLines = listOf(
    highlightLine("import", " React, { ", "useState", " } from 'react';"),
    highlightLine("import", " { Container, Header } from './components';", "", ""),
    AnnotatedString("// Main Workspace Entry"),
    highlightLine("export const", " App = () => {", "", ""),
    highlightLine("", "  const [active, setActive] = ", "useState", "(true);"),
    highlightLine("", "  const [loading, setLoading] = ", "", ""),
    AnnotatedString("  return ("),
    highlightLine("", "    <Container>", "", ""),
    highlightLine("", "      <Header title=", "", "\"iCode Mobile\" />"),
    AnnotatedString("    </Container>"),
)

/** Editor with tab strip, syntax-highlighted document and panel bar. */
@Composable
fun WorkspaceEditorScreen(actions: DemoActions) {
    val problemCount = LocalWorkbenchSnapshot.current.diagnostics.size
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = "App.tsx  •",
            actions = {
                IconButton(onClick = {}) { Icon(Icons.Outlined.Save, contentDescription = "Save", tint = IcodeColors.TextPrimary) }
                IconButton(onClick = { actions.navigate(DemoScreen.EditorFind) }) {
                    Icon(Icons.Outlined.Search, contentDescription = "Find", tint = IcodeColors.TextPrimary)
                }
                IconButton(onClick = { actions.navigate(DemoScreen.CommandPalette) }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Commands", tint = IcodeColors.TextPrimary)
                }
            },
        )
        Row(Modifier.fillMaxWidth().height(IcodeSizes.TabStrip).horizontalScroll(rememberScrollState())) {
            EditorTab("App.tsx", active = true, onClick = {})
            EditorTab("index.ts", active = false, onClick = {})
            EditorTab("utils.ts", active = false, onClick = {})
        }
        CodeSurface(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            padding = PaddingValues(top = IcodeSpacing.Space2),
        ) {
            demoCodeLines.forEachIndexed { index, line ->
                Row(Modifier.fillMaxWidth().padding(vertical = IcodeSpacing.Micro)) {
                    MonoLine(
                        text = "${index + 1}",
                        modifier = Modifier.width(IcodeSizes.LineNumberGutter).padding(end = IcodeSpacing.Space2),
                        color = IcodeColors.TextMuted,
                        style = IcodeType.MonoSmall,
                    )
                    Text(line, style = IcodeType.Mono, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
        BottomPanelBar(
            problemCount = problemCount,
            onTerminal = { actions.navigate(DemoScreen.TerminalExpanded) },
            onProblems = { actions.navigate(DemoScreen.ProblemsPanel) },
            trailing = { Text("⌃", style = IcodeType.Body.copy(color = IcodeColors.TextSecondary)) },
        )
    }
}

/** Minimal token highlighter for the demo document. */
private fun highlightLine(keyword: String, before: String, symbol: String, after: String): AnnotatedString =
    buildAnnotatedString {
        if (keyword.isNotEmpty()) pushStyle(SpanStyle(color = IcodeColors.Accent, fontWeight = FontWeight.SemiBold))
        append(keyword)
        if (keyword.isNotEmpty()) pop()
        append(before)
        if (symbol.isNotEmpty()) pushStyle(SpanStyle(color = IcodeColors.Blue))
        append(symbol)
        if (symbol.isNotEmpty()) pop()
        append(after)
    }
