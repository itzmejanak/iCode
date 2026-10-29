package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeActionBar
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSetupPage
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.PageHeading
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.WebIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway

/** What sharing means, in the user's words (docs/device-telemetry-plan.md §2 — keep the two in step). */
private val reasons = listOf(
    "Shared: app version, Android version, phone model, language, and whether the Linux runtime is installed.",
    "Never shared: your workspaces, files, Git remotes, keys, terminal or assistant conversations.",
    "A random ID made for this install — not your account, phone number or Android ID.",
    "Change it any time in Settings › Application. Turning it off deletes what was sent.",
)

/**
 * Onboarding's last step: whether iCode may send anonymous device info to the iCode website.
 * Not Now is the first answer offered, and either answer is final until Settings changes it.
 */
@Composable
fun DeviceInfoConsentScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    fun answer(shared: Boolean) {
        gateway.dispatch(WebIntent.SetDeviceInfoConsent(shared))
        actions.replace(Route.LauncherHome)
    }
    IcodeSetupPage(
        stage = "Device info · Optional",
        bar = {
            IcodeActionBar(title = null) {
                SecondaryButton("Not Now", { answer(false) })
                PrimaryButton("Share Device Info", { answer(true) })
            }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(IcodeSpacing.Gutter)) {
            PageHeading(
                title = "Help improve iCode",
                description = "Share anonymous device info so we know which phones and Android versions iCode runs on, and how many people use it. iCode works the same either way.",
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                reasons.forEachIndexed { index, text ->
                    Row(
                        Modifier.padding(vertical = IcodeSpacing.Space2),
                        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4),
                    ) {
                        IconBadge(
                            text = "${index + 1}",
                            tint = IcodeColors.Accent,
                            size = IcodeSizes.IconXl,
                            background = IcodeColors.SurfaceElevated,
                            style = IcodeType.MonoSmall,
                        )
                        Text(text, modifier = Modifier.weight(1f), style = IcodeType.Body)
                    }
                }
            }
        }
    }
}
