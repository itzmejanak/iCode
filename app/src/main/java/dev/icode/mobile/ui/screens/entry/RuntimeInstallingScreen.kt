package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeActionBar
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSetupPage
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IcodeWatermark
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PageHeading
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.ProgressRing
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RuntimeInstallReason
import dev.icode.mobile.domain.RuntimeInstallStep
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.SystemAccess
import dev.icode.mobile.domain.percentOf
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.OnboardingRoute
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Runtime installation, including its failure (docs/onboarding-polish-plan.md §3.1): the ring of
 * the step in hand over the logo, the installer's own words under it, and the free space at the foot.
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
    val failed = runtime as? RuntimeState.Failed
    val rebuilding = reason != RuntimeInstallReason.Initial
    val storage = state.storage
    IcodeSetupPage(
        stage = "Linux Runtime",
        bar = {
            IcodeActionBar(
                title = storage?.let { "${ByteSize.format(it.availableBytes)} available" } ?: "Checking available storage…",
                caption = storage?.let { "of ${ByteSize.format(it.totalBytes)} on this phone" },
                leading = { Icon(Icons.Outlined.Storage, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconMd)) },
            ) {
                // File browsing and search never needed the runtime: a failure offers to carry on
                // without it, or back to Runtime settings, instead of holding the whole app hostage.
                if (failed != null) {
                    SecondaryButton(if (rebuilding) "Back" else "Skip", { leave(actions, state.systemAccess, toolsPending = false) })
                    if (failed.recoverable) PrimaryButton("Retry", { gateway.dispatch(installIntent(reason)) })
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(IcodeSpacing.Gutter)) {
            PageHeading(
                title = when (reason) {
                    RuntimeInstallReason.Initial -> "Installing Runtime"
                    RuntimeInstallReason.Repair -> "Repairing Runtime"
                    RuntimeInstallReason.Reset -> "Resetting Runtime"
                },
                description = if (rebuilding) "Rebuilding the Linux runtime. Progress and failures come from the real installer."
                else "Preparing the isolated Linux container subsystem for building and running code.",
            )
            // The ring and the installer's words together in the page's free space, the logo behind
            // the ring as the launcher has it; clipped, so on a short screen it never shades the heading.
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
                if (failed != null) {
                    IcodeWatermark()
                    RuntimeFailure(failed)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space6)) {
                        Box(contentAlignment = Alignment.Center) {
                            // The ring sets the size; the logo spreads past it.
                            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) { IcodeWatermark() }
                            // Each step's ring starts afresh: the arc never runs back from a full download.
                            key(stepOf(runtime)) {
                                ProgressRing(fractionOf(runtime)) { RingCentre(runtime) }
                            }
                        }
                        StepCard(runtime)
                    }
                }
            }
        }
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

/** The ring's centre: how far the step has got where it counts, a tick once ready, and the step. */
@Composable
private fun RingCentre(runtime: RuntimeState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val fraction = fractionOf(runtime)
        when {
            runtime is RuntimeState.Ready ->
                Icon(Icons.Outlined.Check, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.MarkSm))
            fraction != null -> Text(percentOf(fraction), style = IcodeType.Display)
        }
        Text(stepOf(runtime), style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent))
    }
}

/** The step in the installer's own words, and how much of it is done where it counts. */
@Composable
private fun StepCard(runtime: RuntimeState) {
    IcodeCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Terminal, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
            Column {
                MonoLine(labelOf(runtime))
                detailOf(runtime)?.let { MonoLine(it, color = IcodeColors.TextSecondary, style = IcodeType.MonoSmall) }
            }
        }
    }
}

/** The failure, in the installer's own words rather than an invented one. */
@Composable
private fun RuntimeFailure(runtime: RuntimeState.Failed) {
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
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = IcodeColors.Error, modifier = Modifier.size(IcodeSizes.IconMd))
                Text("Installation failed", style = IcodeType.Title.copy(color = IcodeColors.Error))
            }
            Divider()
            Column(Modifier.padding(IcodeSpacing.Space3), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                MonoLine(runtime.reason, color = IcodeColors.TextSecondary, style = IcodeType.MonoSmall, maxLines = 6)
                if (!runtime.recoverable) MonoLine("This cannot be retried on this device.", color = IcodeColors.Amber, style = IcodeType.MonoSmall)
            }
        }
    }
}

private fun installIntent(reason: RuntimeInstallReason): ICodeIntent = when (reason) {
    RuntimeInstallReason.Initial -> ICodeIntent.InstallRuntime
    RuntimeInstallReason.Repair -> ICodeIntent.RepairRuntime
    RuntimeInstallReason.Reset -> ICodeIntent.ResetRuntime
}

/** The ring's share: the download's bytes, extraction's entries, full once ready; null where the step does not count. */
private fun fractionOf(runtime: RuntimeState): Float? = when (runtime) {
    is RuntimeState.Downloading -> runtime.fraction
    is RuntimeState.Installing -> runtime.fraction
    is RuntimeState.Ready -> 1f
    else -> null
}

/** The step, as the ring's centre says it. */
private fun stepOf(runtime: RuntimeState): String = when (runtime) {
    is RuntimeState.Downloading -> "DOWNLOADING"
    is RuntimeState.Installing -> when (runtime.step) {
        RuntimeInstallStep.Preparing -> "PREPARING"
        RuntimeInstallStep.Extracting -> "EXTRACTING"
        RuntimeInstallStep.Configuring -> "CONFIGURING"
        RuntimeInstallStep.Verifying -> "VERIFYING"
    }
    is RuntimeState.Ready -> "READY"
    is RuntimeState.Failed -> "FAILED"
    is RuntimeState.NotInstalled, RuntimeState.Unknown -> "STARTING"
}

private fun labelOf(runtime: RuntimeState): String = when (runtime) {
    is RuntimeState.Downloading -> runtime.label
    is RuntimeState.Installing -> runtime.label
    is RuntimeState.Ready -> "Ready: ${runtime.release}"
    is RuntimeState.Failed -> runtime.reason
    is RuntimeState.NotInstalled -> runtime.reason
    RuntimeState.Unknown -> "Checking the runtime…"
}

/** "54.1 MB / 120 MB", or "2048 / 3413 files": how much of the step is done, where it counts. */
private fun detailOf(runtime: RuntimeState): String? = when (runtime) {
    is RuntimeState.Downloading -> runtime.transferred
    is RuntimeState.Installing -> runtime.counts?.let { "$it files" }
    else -> null
}
