package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.PageHeading
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

private val permissionReasons = listOf(
    "Allows you to read, edit, and compile repositories and source files in their original locations.",
    "Permits opening any accessible storage directory as a workspace without file-picker restrictions.",
    "Guarantees security. Private app spaces of other native tools remain entirely inaccessible.",
    "You remain in control. This system-level developer permission is revocable at any time in Android settings.",
)

/** All-files-access explanation shown once during setup. */
@Composable
fun PermissionScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize().padding(IcodeSpacing.Space6)) {
        PageHeading(
            title = "All Files Access",
            description = "iCode is an on-device local development tool. To function correctly, Android requires the MANAGE_EXTERNAL_STORAGE permission.",
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            permissionReasons.forEachIndexed { index, text ->
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
        PrimaryButton("Open System Settings", { actions.navigate(DemoScreen.LauncherHome) }, Modifier.fillMaxWidth())
        SecondaryButton("Not Now", { actions.navigate(DemoScreen.LauncherEmpty) }, Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space3))
    }
}
