package dev.icode.mobile.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.PhoneIntent
import dev.icode.mobile.domain.phone.ShizukuState
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant › Phone control › Shizuku (docs/phone-agent-plan.md §5.9, §5.11): its state,
 * read live, the one step that state needs, and what it adds. Optional: phone control works without it.
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
            IcodeSection("State") {
                IcodeListRow(
                    card = true,
                    title = state.label,
                    subtitle = state.step,
                    subtitleStyle = IcodeType.Caption,
                    trailing = { StatusLabel(if (state == ShizukuState.Ready) "Ready" else "To do", if (state == ShizukuState.Ready) IcodeColors.Green else IcodeColors.Amber) },
                )
                when (state) {
                    ShizukuState.NotInstalled, ShizukuState.TooOld ->
                        PrimaryButton("Get Shizuku", { context.openFirst(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD))) }, Modifier.fillMaxWidth())
                    ShizukuState.NotRunning -> PrimaryButton("Open Shizuku", {
                        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.let { context.openFirst(it) }
                    }, Modifier.fillMaxWidth())
                    ShizukuState.NotAllowed -> PrimaryButton("Allow iCode", { gateway.dispatch(PhoneIntent.RequestShizuku) }, Modifier.fillMaxWidth())
                    ShizukuState.Ready -> Unit
                }
            }
            IcodeSection("What it adds") {
                Text(
                    "• One tap turns phone control on: iCode allows restricted settings and turns its own service on.\n" +
                        "• Taps, swipes, typing and keys as real input where Android refuses accessibility.\n" +
                        "• A screenshot when the accessibility one is refused.",
                    style = IcodeType.Body.copy(color = IcodeColors.TextSecondary),
                )
            }
            Text(
                "Shizuku runs with the shell's rights, started by you over Wireless debugging; Android stops it at each restart. " +
                    "iCode reads the screen through its accessibility service either way.",
                style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
            )
        }
    }
}

private const val PACKAGE = "moe.shizuku.privileged.api"
private const val DOWNLOAD = "https://shizuku.rikka.app/download/"
