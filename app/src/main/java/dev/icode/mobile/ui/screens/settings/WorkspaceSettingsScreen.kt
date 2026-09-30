package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.Stepper
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.IndentType
import dev.icode.mobile.domain.SettingLimits
import dev.icode.mobile.domain.GitTransferLabel
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.LocalWorkspacePreferencesRepository

private val tabTypes = listOf("Spaces", "Tabs")

/** Workspace-scoped overrides; every enabled control has a persisted consumer. */
@Composable
fun WorkspaceSettingsScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val values = LocalWorkspacePreferences.current
    val repository = LocalWorkspacePreferencesRepository.current
    val root = state.activeWorkspaceLocation
    val saver = rememberSettingSaver(values, "Could not save setting")

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Workspace Settings",
            subtitle = if (root == null) "No workspace" else state.activeWorkspace,
            onBack = actions.back,
        )
        if (root == null || values.workspaceLocation != root || !values.loaded || values.loadError != null) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(IcodeSpacing.Space4), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                Text(
                    when {
                        root == null -> "Open a workspace to edit its settings."
                        values.loadError != null -> "Settings could not be loaded: ${values.loadError}"
                        else -> "Loading workspace settings…"
                    },
                    style = IcodeType.Body,
                )
                if (root == null) SecondaryButton("Open workspace", { actions.navigate(Route.LauncherHome) })
            }
            SettingsScopeBar(workspace = true, actions)
            return@Column
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            saver.error?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error)) }
            SectionLabel("Editor Overrides")
            IcodeCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Indent Size", style = IcodeType.Body)
                    Stepper(
                        value = values.effectiveIndentSize,
                        onDecrement = {
                            if (values.effectiveIndentSize > SettingLimits.indentSizes.first) saver.save {
                                repository.setIndentSizeOverride(root, values.effectiveIndentSize - 1)
                            }
                        },
                        onIncrement = {
                            if (values.effectiveIndentSize < SettingLimits.indentSizes.last) saver.save {
                                repository.setIndentSizeOverride(root, values.effectiveIndentSize + 1)
                            }
                        },
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Global inherited value is ${values.globalIndentSize}", style = IcodeType.Caption)
                    Text(
                        "Reset to Inherited",
                        modifier = Modifier.clickable(enabled = values.indentSizeOverride != null) {
                            saver.save { repository.setIndentSizeOverride(root, null) }
                        },
                        style = IcodeType.Micro.copy(
                            color = if (values.indentSizeOverride == null) IcodeColors.TextMuted else IcodeColors.Accent,
                        ),
                    )
                }
            }
            SettingRow("Tab Type", Modifier.fillMaxWidth()) {
                SegmentedControl(tabTypes, values.effectiveIndentType.name) { selected ->
                    saver.save { repository.setIndentTypeOverride(root, IndentType.valueOf(selected)) }
                }
            }
            Text(
                if (values.indentTypeOverride == null) "Inherited: ${values.inheritedIndentType.name}" else "Reset Tab Type to Inherited",
                modifier = Modifier.clickable(enabled = values.indentTypeOverride != null) {
                    saver.save { repository.setIndentTypeOverride(root, null) }
                },
                style = IcodeType.Caption.copy(
                    color = if (values.indentTypeOverride == null) IcodeColors.TextMuted else IcodeColors.Accent,
                ),
            )
            SectionLabel("Source Control", Modifier.padding(top = IcodeSpacing.Space2))
            SettingRow("Git Auto-Fetch", Modifier.fillMaxWidth(), subtitle = "On workspace open or app resume; never pulls") {
                IcodeToggle(values.autoFetch) { enabled -> saver.save { repository.setAutoFetch(root, enabled) } }
            }
            SettingRow(
                "Git Identity",
                Modifier.fillMaxWidth(),
                subtitle = "Application default or this repository's override",
                onClick = { actions.navigate(Route.GitAuth) },
            )
            val fetchFailure = state.gitOperation as? GitOperationState.Failed
            if (fetchFailure?.label == GitTransferLabel.FETCHING) {
                Text("Last fetch failed: ${fetchFailure.reason}", style = IcodeType.Caption.copy(color = IcodeColors.Error))
            }
            SectionLabel("File Watcher Excludes", Modifier.padding(top = IcodeSpacing.Space2))
            IcodeCard(Modifier.fillMaxWidth()) {
                Text(
                    "+ Add Exclude Pattern",
                    style = IcodeType.Body.copy(color = IcodeColors.TextMuted, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    "Not available yet. The watcher follows only the folders open in the Explorer " +
                        "and the folders of open files.",
                    style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
                )
            }
        }
        SettingsScopeBar(workspace = true, actions)
    }
}
