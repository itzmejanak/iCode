package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.Stepper
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.IndentType
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.LocalWorkspacePreferencesRepository
import kotlinx.coroutines.launch

private val tabTypes = listOf("Spaces", "Tabs")

/** Workspace-scoped overrides; every enabled control has a persisted consumer. */
@Composable
fun WorkspaceSettingsScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val values = LocalWorkspacePreferences.current
    val repository = LocalWorkspacePreferencesRepository.current
    val root = state.activeWorkspaceLocation
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    fun save(change: suspend () -> Unit) {
        error = null
        scope.launch { runCatching { change() }.onFailure { error = it.message ?: "Could not save setting" } }
    }

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
            error?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error)) }
            SectionLabel("Editor Overrides")
            IcodeCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Indent Size", style = IcodeType.Body)
                    Stepper(
                        value = values.effectiveIndentSize,
                        onDecrement = {
                            if (values.effectiveIndentSize > 1) save {
                                repository.setIndentSizeOverride(root, values.effectiveIndentSize - 1)
                            }
                        },
                        onIncrement = {
                            if (values.effectiveIndentSize < 8) save {
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
                            save { repository.setIndentSizeOverride(root, null) }
                        },
                        style = IcodeType.Micro.copy(
                            color = if (values.indentSizeOverride == null) IcodeColors.TextMuted else IcodeColors.Accent,
                        ),
                    )
                }
            }
            SettingRow("Tab Type", Modifier.fillMaxWidth()) {
                SegmentedControl(tabTypes, values.effectiveIndentType.name) { selected ->
                    save { repository.setIndentTypeOverride(root, IndentType.valueOf(selected)) }
                }
            }
            Text(
                if (values.indentTypeOverride == null) "Inherited: Spaces" else "Reset Tab Type to Inherited",
                modifier = Modifier.clickable(enabled = values.indentTypeOverride != null) {
                    save { repository.setIndentTypeOverride(root, null) }
                },
                style = IcodeType.Caption.copy(
                    color = if (values.indentTypeOverride == null) IcodeColors.TextMuted else IcodeColors.Accent,
                ),
            )
            SectionLabel("Source Control", Modifier.padding(top = IcodeSpacing.Space2))
            SettingRow("Git Auto-Fetch", Modifier.fillMaxWidth(), subtitle = "On workspace open or app resume; never pulls") {
                IcodeToggle(values.autoFetch) { enabled -> save { repository.setAutoFetch(root, enabled) } }
            }
            SettingRow(
                "Git Identity",
                Modifier.fillMaxWidth(),
                subtitle = "Application default or this repository's override",
                onClick = { actions.navigate(Route.GitAuth) },
            )
            val fetchFailure = state.gitOperation as? GitOperationState.Failed
            if (fetchFailure?.label == "Fetching") {
                Text("Last fetch failed: ${fetchFailure.reason}", style = IcodeType.Caption.copy(color = IcodeColors.Error))
            }
            SectionLabel("File Watcher Excludes", Modifier.padding(top = IcodeSpacing.Space2))
            IcodeCard(Modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.None)) {
                CodeSurface {
                    MonoLine(
                        values.watcherExcludes.joinToString("\n").ifEmpty { "No exclusions configured" },
                        color = IcodeColors.TextSecondary,
                        maxLines = Int.MAX_VALUE,
                    )
                }
                Divider()
                Text(
                    "+ Add Exclude Pattern",
                    modifier = Modifier.padding(top = IcodeSpacing.Space3, start = IcodeSpacing.Space3, end = IcodeSpacing.Space3),
                    style = IcodeType.Body.copy(color = IcodeColors.TextMuted, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    "Available when File Watcher is connected",
                    modifier = Modifier.padding(start = IcodeSpacing.Space3, end = IcodeSpacing.Space3, bottom = IcodeSpacing.Space3),
                    style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
                )
            }
        }
        SettingsScopeBar(workspace = true, actions)
    }
}
