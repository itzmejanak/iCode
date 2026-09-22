package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.BottomPanelBar
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** Editor placeholder with quick actions and recent files. */
@Composable
fun EditorEmptyScreen(actions: DemoActions) {
    val state = LocalWorkbenchSnapshot.current
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = state.activeWorkspace,
            subtitle = state.activePath,
            onBack = { actions.navigate(DemoScreen.LauncherHome) },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space4),
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            EmptyActionTile(Icons.Outlined.Search, "Quick Open", Modifier.weight(1f)) { actions.navigate(DemoScreen.QuickOpen) }
            EmptyActionTile(Icons.Outlined.Add, "New File", Modifier.weight(1f)) { actions.navigate(DemoScreen.WorkspaceEditor) }
            EmptyActionTile(Icons.Outlined.Terminal, "Open Terminal", Modifier.weight(1f)) { actions.navigate(DemoScreen.TerminalExpanded) }
        }
        EmptyState(
            title = "Select a file to begin coding",
            mark = {
                IcodeCard(borderColor = IcodeColors.Outline, padding = PaddingValues(IcodeSpacing.Space3)) {
                    MonoLine("[ i ]", style = IcodeType.Mark)
                }
            },
            modifier = Modifier.padding(top = IcodeSpacing.Space6 + IcodeSpacing.Space4),
        )
        Column(
            modifier = Modifier.padding(IcodeSpacing.Space4).weight(1f),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            SectionLabel(
                "Recent Files",
                Modifier.padding(top = IcodeSpacing.Space6 + IcodeSpacing.Micro, bottom = IcodeSpacing.Space1),
            )
            RecentFile("auth-guard.tsx", "src/components/auth-guard.tsx", "10m ago") { actions.navigate(DemoScreen.WorkspaceEditor) }
            RecentFile("token-utils.ts", "src/utils/token-utils.ts", "2h ago") { actions.navigate(DemoScreen.WorkspaceEditor) }
        }
        BottomPanelBar(
            problemCount = state.diagnostics.size,
            onTerminal = { actions.navigate(DemoScreen.TerminalExpanded) },
            onProblems = { actions.navigate(DemoScreen.ProblemsPanel) },
        )
    }
}

@Composable
private fun EmptyActionTile(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    IcodeCard(modifier = modifier, padding = PaddingValues(IcodeSpacing.Space2 + IcodeSpacing.Micro), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro)) {
            Icon(icon, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm))
            Text(text, style = IcodeType.Caption.copy(color = IcodeColors.TextPrimary), maxLines = 1)
        }
    }
}

@Composable
private fun RecentFile(name: String, path: String, age: String, onClick: () -> Unit) {
    IcodeListRow(
        card = true,
        title = name,
        subtitle = path,
        titleStyle = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
        onClick = onClick,
        leading = {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconXl))
        },
        trailing = { Text(age, style = IcodeType.Caption) },
    )
}
