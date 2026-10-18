package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.domain.AndroidAppRun
import dev.icode.mobile.domain.AndroidAppStep
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

// Build & Run in the Explorer (docs/android-toolchain-plan.md §4.4): the header's ▶ when the open
// workspace holds an Android app, and a notice while it builds and installs, or once it failed.

/** What ▶ needs before it builds, asked for in turn. */
internal enum class BuildAndRunNeed { AndroidSdk, InstallApps }

/** The open workspace's Build & Run, while it goes or once it failed; null when there is none to show. */
@Composable
internal fun visibleAndroidRun(): AndroidAppRun? {
    val state = LocalICodeSnapshot.current
    return state.androidApps.run?.takeIf { it.workspace == state.activeWorkspaceLocation && (it.running || it.failure != null) }
}

/** ▶: what is missing is asked for first — the Android SDK, then installing apps; while it builds, its output. */
@Composable
internal fun BuildAndRunButton(onNeed: (BuildAndRunNeed) -> Unit) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val running = visibleAndroidRun()?.takeIf { it.running }
    IconButton(
        onClick = {
            when {
                running != null -> running.process?.let { gateway.dispatch(ICodeIntent.ShowProcessOutput(it)) }
                !state.runtimeTools.isInstalled(RuntimeTools.AndroidSdk) -> onNeed(BuildAndRunNeed.AndroidSdk)
                state.systemAccess?.installApps != true -> onNeed(BuildAndRunNeed.InstallApps)
                else -> gateway.dispatch(ICodeIntent.BuildAndRunAndroidApp)
            }
        },
    ) {
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = if (running != null) "Show build output" else "Build & Run",
            tint = if (running != null) IcodeColors.Accent else IcodeColors.TextPrimary,
        )
    }
}

/** Building, installing, or why it stopped; Show Output opens the build's tab in the Terminal panel. */
@Composable
internal fun AndroidRunNotice(run: AndroidAppRun, modifier: Modifier = Modifier) {
    val gateway = LocalICodeGateway.current
    val failure = run.failure
    NoticeCard(
        tone = if (failure != null) IcodeTone.Error else IcodeTone.Info,
        modifier = modifier.fillMaxWidth(),
        title = when {
            failure != null -> "Build & Run Failed"
            run.step == AndroidAppStep.Building -> "Building…"
            else -> "Installing…"
        },
    ) {
        val installing = failure == null && run.step == AndroidAppStep.Installing
        Text(
            failure ?: if (installing) "Android asks you to confirm the install." else "Gradle builds the app; its output is in the Terminal.",
            style = IcodeType.Caption,
        )
        Row {
            // Android's confirmation does not open over another app: Install opens it again here.
            if (installing) IcodeTextButton("Install", { gateway.dispatch(ICodeIntent.ConfirmAndroidAppInstall) })
            run.process?.let { process -> IcodeTextButton("Show Output", { gateway.dispatch(ICodeIntent.ShowProcessOutput(process)) }) }
            if (failure != null || installing) {
                IcodeTextButton(if (installing) "Cancel" else "Dismiss", { gateway.dispatch(ICodeIntent.DismissAndroidAppRun) })
            }
        }
    }
}

/** Build & Run without the Android SDK: Runtime Tools installs it. */
@Composable
internal fun AndroidSdkNeededDialog(onOpenRuntimeTools: () -> Unit, onDismiss: () -> Unit) {
    IcodeConfirmDialog(title = "Android SDK Needed", confirmLabel = "Open Runtime Tools", onConfirm = onOpenRuntimeTools, onDismiss = onDismiss) {
        Text(
            "Build & Run builds with the Android SDK: JDK 17, SDK Platform 36 and Build-Tools 36. Install it in Runtime Tools.",
            style = IcodeType.Body,
        )
    }
}
