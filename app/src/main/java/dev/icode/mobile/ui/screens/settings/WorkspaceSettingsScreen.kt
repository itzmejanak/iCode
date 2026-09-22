package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.navigation.DemoActions

private val tabTypes = listOf("Spaces", "Tabs")

/** Workspace-scoped overrides for editor and source control behavior. */
@Composable
fun WorkspaceSettingsScreen(actions: DemoActions) {
    var tabType by rememberSaveable { mutableStateOf(tabTypes.first()) }
    var autoFetch by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Workspace Settings",
            subtitle = "icode-subsystem",
            onBack = actions.back,
            actions = { Text("WORKSPACE", style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent)) },
        )
        Column(
            modifier = Modifier.padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            SectionLabel("Editor Overrides")
            IcodeCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Indent Size", style = IcodeType.Body)
                    MonoLine("2", color = IcodeColors.Accent)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Global inherited value is 4", style = IcodeType.Caption)
                    Text("Reset to Inherited", style = IcodeType.Micro)
                }
            }
            SettingRow("Tab Type", Modifier.fillMaxWidth()) {
                SegmentedControl(tabTypes, tabType) { tabType = it }
            }
            SectionLabel("Source Control", Modifier.padding(top = IcodeSpacing.Space2))
            SettingRow("Git Auto-Fetch", Modifier.fillMaxWidth()) {
                IcodeToggle(autoFetch) { autoFetch = it }
            }
            SectionLabel("File Watcher Excludes", Modifier.padding(top = IcodeSpacing.Space2))
            IcodeCard(Modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.None)) {
                CodeSurface {
                    MonoLine(
                        "**/node_modules/**\n**/build/**\n**/.git/**",
                        color = IcodeColors.TextSecondary,
                        maxLines = Int.MAX_VALUE,
                    )
                }
                Divider()
                Text(
                    "+ Add Exclude Pattern",
                    modifier = Modifier.padding(top = IcodeSpacing.Space3, start = IcodeSpacing.Space3, end = IcodeSpacing.Space3, bottom = IcodeSpacing.Space3),
                    style = IcodeType.Body.copy(color = IcodeColors.Accent, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
