package dev.icode.mobile.ui.screens.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.demo.DemoContent
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route

/** Recovery center listing auto-saved documents after an unexpected shutdown. */
@Composable
fun DocumentRecoveryScreen(actions: NavigationActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Recover Unsaved Work", subtitle = "Unexpected shutdown recovery center", onBack = actions.back)
        LazyColumn(
            modifier = Modifier.weight(1f).padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            item {
                NoticeCard(
                    tone = IcodeTone.Warning,
                    title = "Unsaved changes detected",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "iCode was closed or interrupted unexpectedly. The following documents have local auto-recovery caches.",
                        style = IcodeType.Body,
                    )
                }
            }
            item {
                SectionLabel(
                    "Recoverable Documents",
                    Modifier.padding(top = IcodeSpacing.Space3, bottom = IcodeSpacing.Space1),
                )
            }
            items(DemoContent.recoveryDocuments) { document ->
                IcodeListRow(
                    card = true,
                    content = {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(
                                    if (document.name == DemoContent.packageJson) IcodeColors.TextSecondary else IcodeColors.Amber,
                                )
                                Text(
                                    document.name,
                                    modifier = Modifier.weight(1f).padding(start = IcodeSpacing.Space2),
                                    style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                                )
                                MonoLine(document.size, color = IcodeColors.Accent, style = IcodeType.MonoSmall)
                            }
                            MonoLine(
                                text = document.path,
                                modifier = Modifier.padding(top = IcodeSpacing.Space2),
                                style = IcodeType.MonoSmall,
                            )
                            Text("Auto-saved: ${document.time}", style = IcodeType.Caption)
                            TwoColumnActions(
                                left = { SecondaryButton("Restore Cache", {}, Modifier.fillMaxWidth(), color = IcodeColors.Accent) },
                                right = { SecondaryButton("Discard", {}, Modifier.fillMaxWidth(), color = IcodeColors.Error) },
                                modifier = Modifier.padding(top = IcodeSpacing.Space2),
                            )
                        }
                    },
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            PrimaryButton("Restore All Workspace Docs", { actions.navigate(Route.WorkspaceEditor) }, Modifier.fillMaxWidth())
            SecondaryButton("Discard All Recovery Files", { actions.navigate(Route.LauncherHome) }, Modifier.fillMaxWidth(), color = IcodeColors.TextMuted)
        }
    }
}
