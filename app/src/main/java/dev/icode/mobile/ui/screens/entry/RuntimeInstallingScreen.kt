package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IndeterminateProgressTrack
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PageHeading
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.RuntimeInstallReason
import dev.icode.mobile.domain.StorageSummary
import dev.icode.mobile.domain.SystemAccess
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.OnboardingRoute
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Runtime installation, including its failure.
 *
 * Failure is a state of this installation, not a place of its own: the user
 * never asks to see it, they arrive because the install failed, and retrying
 * belongs on the surface that was doing the work. A separate route also made
 * retry impossible, because returning to this screen in a failed state only
 * sent the user back to the failure again.
 */
@Composable
fun RuntimeInstallingScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val runtime = state.runtime
    val reason = state.runtimeInstallReason
    LaunchedEffect(actions.captureMode, runtime, reason, state.systemAccess) {
        if (actions.captureMode) return@LaunchedEffect
        when (runtime) {
            is RuntimeState.NotInstalled -> gateway.dispatch(installIntent(reason))
            is RuntimeState.Ready -> leave(actions, state.systemAccess, state.runtimeTools.offerPending)
            else -> Unit
        }
    }
    Column(Modifier.fillMaxSize().padding(IcodeSpacing.Space6)) {
        PageHeading(
            title = when (reason) {
                RuntimeInstallReason.Initial -> "Installing Runtime"
                RuntimeInstallReason.Repair -> "Repairing Runtime"
                RuntimeInstallReason.Reset -> "Resetting Runtime"
            },
            description = if (reason == RuntimeInstallReason.Initial)
                "Preparing the isolated Linux container subsystem for building and running code."
            else "Rebuilding the Linux runtime. Progress and failures come from the real installer.",
        )
        Spacer(Modifier.weight(1f))
        if (runtime is RuntimeState.Failed) {
            RuntimeFailure(
                runtime = runtime,
                onRetry = { gateway.dispatch(installIntent(reason)) },
                onSkip = { leave(actions, state.systemAccess, toolsPending = false) },
                rebuilding = reason != RuntimeInstallReason.Initial,
            )
            Spacer(Modifier.weight(1.35f))
            StorageLine(state.storage)
            return@Column
        }
        // Extraction has no measurable fraction. A bar parked at a fixed point
        // for the minutes it takes reads as hung, so the phase sweeps instead
        // and the step line below carries tar's own running count.
        if (runtime is RuntimeState.Installing) {
            IndeterminateProgressTrack(Modifier.fillMaxWidth())
        } else {
            ProgressTrack(installProgress(runtime), Modifier.fillMaxWidth())
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoLine(percentLabel(runtime), color = IcodeColors.Accent, style = IcodeType.MonoSmall)
            MonoLine(transferLabel(runtime), style = IcodeType.MonoSmall)
        }
        IcodeCard(
            modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space4 + IcodeSpacing.Micro),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Terminal, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
                MonoLine(stepLabel(runtime))
            }
        }
        Spacer(Modifier.weight(1.35f))
        StorageLine(state.storage)
    }
}

/**
 * Where onboarding goes once the runtime is settled.
 *
 * A runtime just built offers Set Up Tools first, in this screen's place, whether
 * onboarding or a rebuild brought the user here. Otherwise, reached from the Terminal or
 * Runtime settings there is something to return to; reached as the first step of onboarding
 * there is not, and this screen must route onward.
 */
private fun leave(actions: NavigationActions, access: SystemAccess?, toolsPending: Boolean) {
    if (toolsPending) {
        actions.replace(Route.RuntimeTools)
        return
    }
    if (actions.canGoBack) {
        actions.back()
        return
    }
    // What Android allows has not been read yet; the next state change routes.
    OnboardingRoute.afterRuntime(access, toolsPending = false)?.let { actions.replace(it) }
}

/**
 * The failure, in the installer's own words rather than an invented one.
 *
 * File browsing and search never needed the runtime, so a failure offers to
 * carry on without it instead of holding the whole app hostage.
 */
@Composable
private fun RuntimeFailure(
    runtime: RuntimeState.Failed,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    rebuilding: Boolean,
) {
    IcodeCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = IcodeColors.Error,
        padding = PaddingValues(IcodeSpacing.None),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(IcodeSpacing.Space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
            ) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = IcodeColors.Error,
                    modifier = Modifier.size(IcodeSizes.IconMd),
                )
                Text("Installation failed", style = IcodeType.Title.copy(color = IcodeColors.Error))
            }
            Divider()
            MonoLine(
                runtime.reason,
                modifier = Modifier.padding(IcodeSpacing.Space3),
                color = IcodeColors.TextSecondary,
                style = IcodeType.MonoSmall,
                maxLines = 6,
            )
        }
    }
    if (runtime.recoverable) {
        PrimaryButton(
            if (rebuilding) "Retry Rebuild" else "Retry Installation",
            onRetry,
            Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space4),
        )
    } else {
        MonoLine(
            "This cannot be retried on this device.",
            modifier = Modifier.padding(top = IcodeSpacing.Space4),
            color = IcodeColors.Amber,
            style = IcodeType.MonoSmall,
        )
    }
    SecondaryButton(
        if (rebuilding) "Return to Runtime Settings" else "Continue without the runtime",
        onSkip,
        Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space3),
    )
}

private fun installIntent(reason: RuntimeInstallReason): ICodeIntent = when (reason) {
    RuntimeInstallReason.Initial -> ICodeIntent.InstallRuntime
    RuntimeInstallReason.Repair -> ICodeIntent.RepairRuntime
    RuntimeInstallReason.Reset -> ICodeIntent.ResetRuntime
}

@Composable
private fun StorageLine(storage: StorageSummary?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
        Icon(Icons.Outlined.Storage, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconMd))
        Text(storageLabel(storage), style = IcodeType.Caption)
    }
}

/**
 * Real progress.
 *
 * Extraction has no measurable fraction, so it holds at a fixed point rather
 * than inventing movement: a bar that pretends to advance is a lie about work.
 */
private fun installProgress(runtime: RuntimeState): Float = when (runtime) {
    is RuntimeState.Downloading -> runtime.fraction ?: 0f
    is RuntimeState.Ready -> 1f
    else -> 0f
}

private fun percentLabel(runtime: RuntimeState): String = when (runtime) {
    is RuntimeState.Downloading ->
        runtime.fraction?.let { "%.1f%% DOWNLOADED".format(it * 100) } ?: "DOWNLOADING"
    is RuntimeState.Installing -> "PREPARING"
    is RuntimeState.Ready -> "100% COMPLETE"
    is RuntimeState.Failed -> "FAILED"
    else -> "STARTING"
}

private fun transferLabel(runtime: RuntimeState): String = when (runtime) {
    is RuntimeState.Downloading -> runtime.transferred
    else -> ""
}

private fun stepLabel(runtime: RuntimeState): String = when (runtime) {
    is RuntimeState.Downloading -> runtime.label
    is RuntimeState.Installing -> runtime.label
    is RuntimeState.Ready -> "Ready: ${runtime.release}"
    is RuntimeState.Failed -> runtime.reason
    is RuntimeState.NotInstalled -> runtime.reason
    else -> "Checking the runtime..."
}

private fun storageLabel(storage: StorageSummary?): String = storage
    ?.let { "Available storage: ${ByteSize.format(it.availableBytes)} of ${ByteSize.format(it.totalBytes)}" }
    ?: "Checking available storage..."
