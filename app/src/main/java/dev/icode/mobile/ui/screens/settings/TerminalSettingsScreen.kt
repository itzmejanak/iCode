package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeDropdown
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.KeyChipRow
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.Stepper
import dev.icode.mobile.domain.EditorFontFamily
import dev.icode.mobile.domain.SettingLimits
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.TerminalCursorStyle
import dev.icode.mobile.domain.TerminalExtraKey
import dev.icode.mobile.domain.TerminalSettingRules
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.LocalWorkspacePreferencesRepository

/** App-wide terminal preferences; shell changes apply when the next session starts. */
@Composable
fun TerminalSettingsScreen(actions: NavigationActions) {
    val preferences = LocalWorkspacePreferences.current
    val repository = LocalWorkspacePreferencesRepository.current
    val gateway = LocalWorkbenchGateway.current
    val access = LocalWorkbenchSnapshot.current.systemAccess
    val settings = preferences.terminal
    val saver = rememberSettingSaver(preferences, "Could not save terminal setting")
    var editingShell by remember { mutableStateOf(false) }
    var shellDraft by rememberSaveable { mutableStateOf(settings.shellPath) }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Terminal Settings", subtitle = settingsSubtitle(Route.TerminalSettings), onBack = actions.back)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4 + IcodeSpacing.Micro),
        ) {
            if (!preferences.loaded) Text("Loading settings…", style = IcodeType.Caption)
            preferences.loadError?.let {
                Text("Settings unavailable: $it", style = IcodeType.Caption.copy(color = IcodeColors.Error))
            }
            saver.error?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error)) }
            SectionLabel("Shell Path")
            SettingRow(
                settings.shellPath,
                Modifier.fillMaxWidth(),
                subtitle = "Used by new sessions; the executable must be installed in the runtime",
                onClick = {
                    if (saver.ready) {
                        shellDraft = settings.shellPath
                        editingShell = true
                    }
                },
            ) {
                Text("EDIT", style = IcodeType.Label.copy(color = IcodeColors.Accent))
            }
            IcodeDropdown(
                label = "Font Family",
                options = EditorFontFamily.entries,
                selected = settings.fontFamily,
                optionLabel = { if (it == EditorFontFamily.PlexMono) "IBM Plex Mono" else "System Monospace" },
                onSelect = { saver.save { repository.setTerminalFontFamily(it) } },
            )
            SettingRow("Font Size", Modifier.fillMaxWidth()) {
                Stepper(
                    settings.fontSizeSp,
                    { if (settings.fontSizeSp > SettingLimits.fontSizesSp.first) saver.save { repository.setTerminalFontSize(settings.fontSizeSp - 1) } },
                    { if (settings.fontSizeSp < SettingLimits.fontSizesSp.last) saver.save { repository.setTerminalFontSize(settings.fontSizeSp + 1) } },
                )
            }
            IcodeDropdown(
                label = "Scrollback Limit",
                options = TerminalSettingRules.scrollbackChoices,
                selected = settings.scrollbackLines,
                optionLabel = { "$it lines" },
                onSelect = { saver.save { repository.setTerminalScrollbackLimit(it) } },
            )
            SectionLabel("Extra Keys Row")
            Text("Tap a key to show or hide it in the terminal.", style = IcodeType.Caption)
            KeyChipRow(
                keys = TerminalExtraKey.entries.map { it.label },
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                chipModifier = Modifier.heightIn(min = IcodeSizes.TouchTarget),
                activeKeys = settings.extraKeys.map { it.label }.toSet(),
                onKey = { label ->
                    val selected = TerminalExtraKey.entries.filter { key ->
                        if (key.label == label) key !in settings.extraKeys else key in settings.extraKeys
                    }
                    saver.save { repository.setTerminalExtraKeys(selected) }
                },
            )
            SectionLabel("Cursor Style")
            SettingRow("Cursor Type", Modifier.fillMaxWidth()) {
                SegmentedControl(TerminalCursorStyle.entries.map { it.label }, settings.cursorStyle.label) { label ->
                    val style = TerminalCursorStyle.entries.first { it.label == label }
                    saver.save { repository.setTerminalCursorStyle(style) }
                }
            }
            // Android's own settings; iCode shows what it reads and opens the page to change it.
            SectionLabel("Background")
            val notifications = access?.notifications == true
            SettingRow(
                "Notifications",
                Modifier.fillMaxWidth(),
                subtitle = if (notifications) "Running sessions are listed in the notification drawer"
                else "Off: running sessions appear only in Android's Task Manager",
                onClick = { gateway.dispatch(WorkbenchIntent.OpenSystemSettings(SystemPermission.Notifications)) },
            ) {
                AccessState(if (notifications) "ALLOWED" else "OFF", allowed = notifications)
            }
            val unrestricted = access?.unrestrictedBattery == true
            SettingRow(
                "Battery Optimization",
                Modifier.fillMaxWidth(),
                subtitle = if (unrestricted) "iCode is exempt: running sessions keep going while the phone is idle"
                else "On: Android may cut running sessions off while the phone is idle",
                // While optimized, Android's own prompt exempts iCode in one
                // tap; once exempt, its list page is where to change it back.
                onClick = {
                    gateway.dispatch(
                        if (unrestricted) WorkbenchIntent.OpenSystemSettings(SystemPermission.Battery)
                        else WorkbenchIntent.RequestSystemPermission(SystemPermission.Battery),
                    )
                },
            ) {
                AccessState(if (unrestricted) "UNRESTRICTED" else "OPTIMIZED", allowed = unrestricted)
            }
            // Alerts come only for a session out of sight: iCode closed, or another tab or screen shown.
            SettingRow(
                "Alert When a Program Asks",
                Modifier.fillMaxWidth(),
                subtitle = "It rang the terminal bell, as Claude Code does when set to terminal_bell",
            ) {
                IcodeToggle(settings.bellAlerts) { enabled ->
                    saver.save { repository.setTerminalBellAlerts(enabled) }
                }
            }
            SettingRow(
                "Alert When a Long Command Ends",
                Modifier.fillMaxWidth(),
                subtitle = "It ran ${TerminalSettingRules.FINISH_ALERT_MILLIS / 1_000} seconds or more",
            ) {
                IcodeToggle(settings.finishAlerts) { enabled ->
                    saver.save { repository.setTerminalFinishAlerts(enabled) }
                }
            }
        }
    }

    if (editingShell) {
        IcodeConfirmDialog(
            title = "Shell for new sessions",
            confirmLabel = "Save path",
            confirmEnabled = TerminalSettingRules.validShellPath(shellDraft),
            onConfirm = {
                saver.save { repository.setTerminalShellPath(shellDraft) }
                editingShell = false
            },
            onDismiss = { editingShell = false },
        ) {
            IcodeTextField(
                value = shellDraft,
                onValueChange = { shellDraft = it },
                placeholder = "/bin/bash",
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Enter an absolute path inside the Linux runtime. The next session reports a real failure if that shell is missing.", style = IcodeType.Caption)
        }
    }
}

@Composable
private fun AccessState(label: String, allowed: Boolean) {
    Text(label, style = IcodeType.Label.copy(color = if (allowed) IcodeColors.Green else IcodeColors.Amber))
}
