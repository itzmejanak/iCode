package dev.icode.mobile.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.domain.PhoneIntent
import dev.icode.mobile.domain.phone.PhoneSetup
import dev.icode.mobile.domain.phone.PhoneSetupStep
import dev.icode.mobile.domain.phone.ShizukuState
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant › Phone Control (docs/voice-and-phone-control.md): off by default. Off, the steps
 * that turn it on — one tap with Shizuku; on, the bubble and Turn Off. Shizuku has its own page.
 */
@Composable
fun PhoneControlScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val access = LocalICodeSnapshot.current.phone
    val context = LocalContext.current
    RefreshOnResume { gateway.dispatch(PhoneIntent.Refresh) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Phone Control", subtitle = PhoneSetup.status(access), onBack = actions.back)
        IcodePageColumn(Modifier.weight(1f)) {
            if (access.service) {
                IcodeSection("Phone Control") {
                    SettingRow("Bubble", Modifier.fillMaxWidth(), subtitle = "Over other apps, with the edge glow", leadingIcon = Icons.Outlined.BubbleChart) {
                        IcodeToggle(access.bubble) { gateway.dispatch(PhoneIntent.ShowBubble(it)) }
                    }
                    // A second icon from an earlier build: Android's button, whose tap turns phone control off.
                    if (access.systemButton) {
                        SettingRow(
                            "Android's Accessibility Button",
                            Modifier.fillMaxWidth(),
                            subtitle = "Turn off its shortcut: the bubble replaces it",
                            leadingIcon = Icons.Outlined.AccessibilityNew,
                            leadingTint = IcodeColors.Amber,
                            onClick = { gateway.dispatch(PhoneIntent.OpenAccessibility) },
                        ) { AccessState("REMOVE", allowed = false) }
                    }
                    SettingRow(
                        "Turn Off",
                        Modifier.fillMaxWidth(),
                        subtitle = "Nothing on screen is read",
                        leadingIcon = Icons.Outlined.PowerSettingsNew,
                        leadingTint = IcodeColors.Error,
                        onClick = { gateway.dispatch(PhoneIntent.TurnOff) },
                    )
                }
            } else {
                IcodeSection("Turn On") {
                    if (PhoneSetup.oneTap(access)) {
                        SettingRow(
                            "Turn On With Shizuku",
                            Modifier.fillMaxWidth(),
                            subtitle = "One tap, no steps in Android's settings",
                            leadingIcon = Icons.Outlined.Bolt,
                            onClick = { gateway.dispatch(PhoneIntent.TurnOnWithShizuku) },
                        ) { Chevron() }
                    }
                    PhoneSetup.lines(access, Build.VERSION.SDK_INT).forEachIndexed { index, line ->
                        SettingRow(
                            "${index + 1}. ${line.step.title}",
                            Modifier.fillMaxWidth(),
                            subtitle = line.step.detail,
                            leadingIcon = if (line.step == PhoneSetupStep.Restricted) Icons.Outlined.AdminPanelSettings else Icons.Outlined.AccessibilityNew,
                            onClick = { if (line.step == PhoneSetupStep.Restricted) context.openAppInfo() else gateway.dispatch(PhoneIntent.OpenAccessibility) },
                        ) { if (line.current) AccessState("NEXT", allowed = false) else Chevron() }
                    }
                    // Said where it is turned on: what it reads, and where that goes.
                    Text("The screen it reads goes to the chat's model provider", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
                }
            }
            IcodeSection("Shizuku") {
                SettingRow(
                    "Shizuku",
                    Modifier.fillMaxWidth(),
                    subtitle = "Optional: one-tap setup and real touches",
                    leadingIcon = Icons.Outlined.Terminal,
                    onClick = { actions.navigate(Route.ShizukuSetup) },
                ) {
                    AccessState(access.shizuku.label.uppercase(), allowed = access.shizuku == ShizukuState.Ready)
                    Chevron()
                }
            }
        }
    }
}
