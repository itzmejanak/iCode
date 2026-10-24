package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.Stepper
import dev.icode.mobile.domain.EditorFontFamily
import dev.icode.mobile.domain.SettingLimits
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.TerminalCursorStyle
import dev.icode.mobile.domain.TerminalExtraKey
import dev.icode.mobile.domain.TerminalSettingRules
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.LocalWorkspacePreferencesRepository
import dev.icode.mobile.ui.TerminalKeyChip

/**
 * App-wide terminal preferences; the scrollback applies when the next session starts. The shell is
 * root's login shell, as the runtime's `/etc/passwd` records it (`chsh` changes it).
 */
@Composable
fun TerminalSettingsScreen(actions: NavigationActions) {
    val preferences = LocalWorkspacePreferences.current
    val repository = LocalWorkspacePreferencesRepository.current
    val gateway = LocalICodeGateway.current
    val access = LocalICodeSnapshot.current.systemAccess
    val settings = preferences.terminal
    val saver = rememberSettingSaver(preferences, "Could not save terminal setting")

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Terminal Settings", subtitle = settingsSubtitle(Route.TerminalSettings), onBack = actions.back)
        IcodePageColumn {
            if (!preferences.loaded) Text("Loading settings…", style = IcodeType.Caption)
            preferences.loadError?.let {
                Text("Settings unavailable: $it", style = IcodeType.Caption.copy(color = IcodeColors.Error))
            }
            saver.error?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error)) }
            IcodeSection("Display") {
                IcodeSelect(
                    options = EditorFontFamily.entries,
                    selected = settings.fontFamily,
                    optionLabel = { if (it == EditorFontFamily.PlexMono) "IBM Plex Mono" else "System Monospace" },
                    onSelect = { saver.save { repository.setTerminalFontFamily(it) } },
                    label = "Font Family",
                )
                SettingRow("Font Size", Modifier.fillMaxWidth()) {
                    Stepper(
                        settings.fontSizeSp,
                        { if (settings.fontSizeSp > SettingLimits.fontSizesSp.first) saver.save { repository.setTerminalFontSize(settings.fontSizeSp - 1) } },
                        { if (settings.fontSizeSp < SettingLimits.fontSizesSp.last) saver.save { repository.setTerminalFontSize(settings.fontSizeSp + 1) } },
                    )
                }
                IcodeSelect(
                    options = TerminalSettingRules.scrollbackChoices,
                    selected = settings.scrollbackLines,
                    optionLabel = { "$it lines" },
                    onSelect = { saver.save { repository.setTerminalScrollbackLimit(it) } },
                    label = "Scrollback Limit",
                )
            }
            IcodeSection("Extra Keys Row") {
                KeyRowSettings(TerminalExtraKey.entries, settings.extraKeys, { keys -> saver.save { repository.setTerminalExtraKeys(keys) } }) { key, modifier, active, onClick ->
                    TerminalKeyChip(key, modifier, active, compact = true, onClick = onClick)
                }
            }
            IcodeSection("Cursor Style") {
                SettingRow("Cursor Type", Modifier.fillMaxWidth()) {
                    SegmentedControl(TerminalCursorStyle.entries.map { it.label }, settings.cursorStyle.label) { label ->
                        val style = TerminalCursorStyle.entries.first { it.label == label }
                        saver.save { repository.setTerminalCursorStyle(style) }
                    }
                }
            }
            // Android's own settings; iCode shows what it reads and opens the page to change it.
            IcodeSection("Background") {
                val notifications = access?.notifications == true
                SettingRow(
                    "Notifications",
                    Modifier.fillMaxWidth(),
                    subtitle = if (notifications) "Running sessions are listed in the notification drawer"
                    else "Off: running sessions appear only in Android's Task Manager",
                    onClick = { gateway.dispatch(ICodeIntent.OpenSystemSettings(SystemPermission.Notifications)) },
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
                            if (unrestricted) ICodeIntent.OpenSystemSettings(SystemPermission.Battery)
                            else ICodeIntent.RequestSystemPermission(SystemPermission.Battery),
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
    }
}
