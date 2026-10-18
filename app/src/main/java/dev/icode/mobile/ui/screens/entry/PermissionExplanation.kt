package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.PageHeading
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.SystemPermission

/** What iCode says when it asks for a permission: why, what the user keeps control of. */
private class PermissionStep(
    val title: String,
    val description: String,
    val reasons: List<String>,
    val allowLabel: String,
)

private fun stepOf(permission: SystemPermission): PermissionStep = when (permission) {
    SystemPermission.Notifications -> PermissionStep(
        title = "Notifications",
        description = "Terminal sessions keep running after you leave iCode. Android asks whether iCode may show them in the notification drawer.",
        reasons = listOf(
            "Each running session has its own notification: its name, its workspace and what it is running.",
            "Exit a session, or keep the phone awake for it, right from its notification.",
            "Tapping a notification opens that exact terminal.",
            "Sessions run either way. Without this, Android lists them only in its Task Manager.",
        ),
        allowLabel = "Allow Notifications",
    )
    SystemPermission.Battery -> PermissionStep(
        title = "Battery Optimization",
        description = "While the phone is idle, battery optimization cuts apps off from the network and lets the processor sleep. Exempting iCode lets long commands such as installs, builds and servers keep going.",
        reasons = listOf(
            "Running sessions keep their network access while the phone is idle.",
            "A session you keep awake from its notification can hold the processor awake.",
            "Some phone makers pause background apps on their own; this setting does not change that.",
            "You remain in control. Change it at any time in Terminal Settings.",
        ),
        allowLabel = "Allow Background Use",
    )
    SystemPermission.Storage -> PermissionStep(
        title = "All Files Access",
        description = "Shared is your phone's storage, the files other apps see. Android asks whether iCode may read and change them.",
        reasons = listOf(
            "Browse phone storage in iCode's file explorer, beside your workspaces.",
            "Copy or move files and folders between phone storage and iCode's own storage.",
            "iCode changes phone storage only when you paste, rename, delete or create something there.",
            "Your workspaces never need this: they live in iCode's own storage.",
            "You remain in control. Change it at any time in Storage Settings.",
        ),
        allowLabel = "Allow Access",
    )
    SystemPermission.InstallApps -> PermissionStep(
        title = "Install Unknown Apps",
        description = "Build & Run installs the app it built on this phone, as Android Studio does on a device. Android asks whether iCode may install apps.",
        reasons = listOf(
            "Android shows its own confirmation before each app is installed or updated.",
            "iCode installs only an app you build with Build & Run, or the assistant does when you approve it.",
            "The app is installed as any other: uninstall it from Android's settings.",
            "You remain in control. Change it at any time in Android's settings for iCode.",
        ),
        allowLabel = "Allow Installs",
    )
}

/**
 * [permission]'s explanation and its two answers, as onboarding asks and as a feature asks where it
 * needs one (the file explorer's Shared asks for All Files Access): one text for each permission,
 * wherever it is asked.
 * [header] goes above the title — onboarding's step count.
 */
@Composable
internal fun PermissionExplanation(
    permission: SystemPermission,
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val step = stepOf(permission)
    Column(modifier) {
        header()
        PageHeading(title = step.title, description = step.description)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            step.reasons.forEachIndexed { index, text ->
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
        PrimaryButton(step.allowLabel, onAllow, Modifier.fillMaxWidth())
        SecondaryButton("Not Now", onNotNow, Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space3))
    }
}
