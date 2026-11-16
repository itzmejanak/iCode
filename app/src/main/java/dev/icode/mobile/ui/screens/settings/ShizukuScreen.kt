package dev.icode.mobile.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.domain.PhoneIntent
import dev.icode.mobile.domain.phone.ShizukuState
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant › Phone Control › Shizuku (docs/voice-and-phone-control.md): its state, read
 * live, the one step that state needs, and what it adds. Optional: phone control works without it.
 */
@Composable
fun ShizukuScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val state = LocalICodeSnapshot.current.phone.shizuku
    val context = LocalContext.current
    RefreshOnResume { gateway.dispatch(PhoneIntent.Refresh) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Shizuku", subtitle = state.label, onBack = actions.back)
        IcodePageColumn(Modifier.weight(1f)) {
            IcodeSection("Status") {
                SettingRow("Shizuku", Modifier.fillMaxWidth(), subtitle = state.step, leadingIcon = Icons.Outlined.Terminal) {
                    AccessState(state.label.uppercase(), allowed = state == ShizukuState.Ready)
                }
                when (state) {
                    ShizukuState.NotInstalled, ShizukuState.TooOld ->
                        PrimaryButton("Get Shizuku", { context.openFirst(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD))) }, Modifier.fillMaxWidth())
                    ShizukuState.NotRunning ->
                        PrimaryButton("Open Shizuku", { context.packageManager.getLaunchIntentForPackage(PACKAGE)?.let { context.openFirst(it) } }, Modifier.fillMaxWidth())
                    ShizukuState.NotAllowed -> PrimaryButton("Allow iCode", { gateway.dispatch(PhoneIntent.RequestShizuku) }, Modifier.fillMaxWidth())
                    ShizukuState.Ready -> Unit
                }
            }
            IcodeSection("What It Adds") {
                SettingRow("One-Tap Setup", Modifier.fillMaxWidth(), subtitle = "Turns phone control on for you", leadingIcon = Icons.Outlined.Bolt)
                SettingRow("Real Touches", Modifier.fillMaxWidth(), subtitle = "Where Android refuses accessibility", leadingIcon = Icons.Outlined.TouchApp)
                SettingRow("Screenshots", Modifier.fillMaxWidth(), subtitle = "When the accessibility one is refused", leadingIcon = Icons.Outlined.Screenshot)
            }
        }
    }
}

private const val PACKAGE = "moe.shizuku.privileged.api"
private const val DOWNLOAD = "https://shizuku.rikka.app/download/"
