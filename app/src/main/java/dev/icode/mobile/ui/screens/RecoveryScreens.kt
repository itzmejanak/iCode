package dev.icode.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.DemoActions

@Composable
fun RecoveryScreenHost(screen: DemoScreen, actions: DemoActions) {
    when (screen) {
        DemoScreen.DeleteConfirmation -> DeleteConfirmationScreen(actions)
        DemoScreen.ExternalFileChange -> ExternalChangeScreen(actions)
        DemoScreen.DocumentRecovery -> DocumentRecoveryScreen(actions)
        else -> Unit
    }
}

@Composable
private fun DeleteConfirmationScreen(actions: DemoActions) {
    DialogBackdrop("auth.ts", "Conflict workspace", actions) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(42.dp).background(Color.Transparent, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.WarningAmber, null, tint = IcodeColors.Error, modifier = Modifier.size(36.dp))
            }
            Text("Delete Folder", style = IcodeType.PageTitle.copy(color = IcodeColors.Error))
            Text("Are you sure you want to delete the directory below?", style = IcodeType.Body, textAlign = TextAlign.Center)
            Box(Modifier.fillMaxWidth().background(IcodeColors.BackgroundDeep, RoundedCornerShape(4.dp)).padding(12.dp)) {
                MonoLine("src/components/auth", color = IcodeColors.Accent)
            }
            Text(
                "This folder contains 8 files. This action is destructive and cannot be undone.",
                style = IcodeType.Body,
                color = IcodeColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            DangerButton("DELETE DESTRUCTIVELY", { actions.navigate(DemoScreen.WorkspaceExplorer) }, Modifier.fillMaxWidth(), filled = true)
            SecondaryButton("Cancel", actions.back, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ExternalChangeScreen(actions: DemoActions) {
    DialogBackdrop("token-utils.ts", "Modified state", actions) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.WarningAmber, null, tint = IcodeColors.Amber)
                Text("File Changed Externally", style = IcodeType.Title.copy(color = IcodeColors.Amber))
            }
            Text(
                "token-utils.ts was modified outside the editor while you have unsaved changes inside the IDE.",
                style = IcodeType.Body,
            )
            IcodeCard(Modifier.fillMaxWidth(), elevated = true) {
                DetailCompare("Disk Version", "14:31:05 (Newer)", IcodeColors.Amber)
                DetailCompare("IDE Cache", "14:28:10 (Modified)", IcodeColors.TextSecondary)
            }
            PrimaryButton("Reload From Disk", { actions.navigate(DemoScreen.WorkspaceEditor) }, Modifier.fillMaxWidth())
            SecondaryButton("Compare Versions Side-by-Side", { actions.navigate(DemoScreen.DiffView) }, Modifier.fillMaxWidth())
            SecondaryButton("Overwrites Disk with My Changes", { actions.navigate(DemoScreen.WorkspaceEditor) }, Modifier.fillMaxWidth())
            Text("Ignore & Keep Editing", modifier = Modifier.fillMaxWidth().padding(8.dp), style = IcodeType.Body.copy(color = IcodeColors.TextSecondary), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DetailCompare(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = IcodeType.Caption)
        MonoLine(value, color = color, style = IcodeType.MonoSmall)
    }
}

@Composable
private fun DialogBackdrop(title: String, subtitle: String, actions: DemoActions, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(IcodeColors.BackgroundDeep.copy(alpha = 0.95f))) {
            IcodeTopBar(title, subtitle = subtitle, onBack = actions.back)
        }
        IcodeCard(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp),
            elevated = true,
            padding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) { content() }
    }
}

private data class RecoveryDocument(val name: String, val path: String, val size: String, val time: String)

@Composable
private fun DocumentRecoveryScreen(actions: DemoActions) {
    val documents = listOf(
        RecoveryDocument("auth.ts", "src/components/auth.ts", "4.2 KB", "14:28:10"),
        RecoveryDocument("token-utils.ts", "src/utils/token-utils.ts", "1.8 KB", "14:25:32"),
        RecoveryDocument("package.json", "package.json", "12 KB", "14:15:00"),
    )
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Recover Unsaved Work", subtitle = "Unexpected shutdown recovery center", onBack = actions.back)
        LazyColumn(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                IcodeCard(Modifier.fillMaxWidth(), borderColor = IcodeColors.Amber) {
                    Text("Unsaved changes detected", style = IcodeType.Title.copy(color = IcodeColors.Amber))
                    Text(
                        "iCode was closed or interrupted unexpectedly. The following documents have local auto-recovery caches.",
                        modifier = Modifier.padding(top = 8.dp),
                        style = IcodeType.Body,
                    )
                }
            }
            item { SectionLabel("Recoverable Documents", Modifier.padding(top = 10.dp, bottom = 2.dp)) }
            items(documents) { document ->
                IcodeCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (document.name == "package.json") IcodeColors.TextSecondary else IcodeColors.Amber)
                        Text(document.name, modifier = Modifier.weight(1f).padding(start = 10.dp), style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                        MonoLine(document.size, color = IcodeColors.Accent, style = IcodeType.MonoSmall)
                    }
                    MonoLine(document.path, modifier = Modifier.padding(top = 10.dp), style = IcodeType.MonoSmall)
                    Text("Auto-saved: ${document.time}", style = IcodeType.Caption)
                    TwoColumnActions(
                        left = { SecondaryButton("Restore Cache", {}, Modifier.fillMaxWidth(), color = IcodeColors.Accent) },
                        right = { SecondaryButton("Discard", {}, Modifier.fillMaxWidth(), color = IcodeColors.Error) },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Restore All Workspace Docs", { actions.navigate(DemoScreen.WorkspaceEditor) }, Modifier.fillMaxWidth())
            SecondaryButton("Discard All Recovery Files", { actions.navigate(DemoScreen.LauncherHome) }, Modifier.fillMaxWidth(), color = IcodeColors.TextMuted)
        }
    }
}
