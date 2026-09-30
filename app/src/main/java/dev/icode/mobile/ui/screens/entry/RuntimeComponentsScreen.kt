package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PageHeading
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.ComponentPlan
import dev.icode.mobile.domain.ComponentRules
import dev.icode.mobile.domain.RuntimeComponents
import dev.icode.mobile.domain.RuntimeComponentsState
import dev.icode.mobile.domain.StorageSummary
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.OnboardingRoute
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/**
 * The runtime's components (docs/runtime-components-plan.md §3.5): Android Studio's setup and SDK
 * Manager on one page. Offered once after a runtime is built — Standard or Custom, then Install or
 * Skip. From Runtime settings: what is installed is ticked; tick to add, untick to remove, Apply.
 * While apt runs the page is its progress, and Hide leaves it to its notification.
 */
@Composable
fun RuntimeComponentsScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val components = state.components
    val installed = components.installedPackages
    // Decided on arrival: answering the offer ends it, and the page must not change what it is meanwhile.
    val offered by rememberSaveable { mutableStateOf(components.offerPending) }
    var custom by rememberSaveable { mutableStateOf(!offered) }
    var chosen by rememberSaveable(stateSaver = ChoiceSaver) { mutableStateOf<Set<String>?>(null) }
    // Asked here, and seen running: when it ends well, an offer moves on by itself.
    var applying by rememberSaveable { mutableStateOf(false) }
    var watching by remember { mutableStateOf(false) }

    val standard = installed?.let(ComponentRules::initialChoice).orEmpty()
    val choice = if (custom) chosen.orEmpty().toSet() else standard
    val plan = installed?.let { ComponentRules.plan(choice, it) }
    val work = components.work

    fun leave() {
        if (actions.canGoBack) actions.back() else OnboardingRoute.afterComponents(state.systemAccess)?.let(actions.replace)
    }
    fun apply() {
        applying = true
        gateway.dispatch(WorkbenchIntent.ApplyComponents(choice))
    }

    LaunchedEffect(installed) {
        if (installed == null) return@LaunchedEffect
        if (chosen == null) chosen = standard
        gateway.dispatch(WorkbenchIntent.MeasureComponents)
        gateway.dispatch(WorkbenchIntent.RefreshStorage)
    }
    LaunchedEffect(work) {
        when {
            work != null -> if (applying) watching = true
            watching -> {
                watching = false
                applying = false
                if (offered) leave()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (offered) {
            PageHeading(
                "Set Up Tools",
                modifier = Modifier.padding(start = IcodeSpacing.Space5, end = IcodeSpacing.Space5, top = IcodeSpacing.Space6),
                description = "Choose what the Linux runtime installs now. Change it any time in Runtime Environment settings.",
            )
        } else {
            IcodeTopBar("Runtime Components", onBack = actions.back)
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(IcodeSpacing.Space5),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            if (work != null) {
                ComponentProgress(work)
                return@Column
            }
            if (offered) {
                SegmentedControl(listOf(STANDARD, CUSTOM), if (custom) CUSTOM else STANDARD) { custom = it == CUSTOM }
                Text(
                    if (custom) "Tick what to install." else "What iCode itself uses: Git, the SSH client and ripgrep.",
                    style = IcodeType.Caption,
                )
            } else {
                Text("Tick to install, untick to remove.", style = IcodeType.Caption)
            }
            ComponentList(components, choice, editable = custom) { id ->
                val before = chosen.orEmpty()
                chosen = if (id in before) before - id else before + id
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space5),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            when {
                work?.failure != null -> TwoColumnActions(
                    left = { SecondaryButton("Close", { gateway.dispatch(WorkbenchIntent.DismissComponents) }, Modifier.fillMaxWidth()) },
                    right = { PrimaryButton("Retry", ::apply, Modifier.fillMaxWidth()) },
                )
                work != null -> TwoColumnActions(
                    left = {
                        SecondaryButton(
                            "Cancel",
                            { gateway.dispatch(WorkbenchIntent.CancelComponents) },
                            Modifier.fillMaxWidth(),
                            enabled = work.cancellable,
                        )
                    },
                    right = { PrimaryButton("Hide", ::leave, Modifier.fillMaxWidth()) },
                )
                else -> {
                    MonoLine(summary(components, plan, state.storage), style = IcodeType.MonoSmall)
                    components.measureFailure?.let { MonoLine(it, color = IcodeColors.Error, style = IcodeType.MonoSmall, maxLines = 3) }
                    if (offered) {
                        TwoColumnActions(
                            left = {
                                SecondaryButton("Skip", {
                                    gateway.dispatch(WorkbenchIntent.SkipComponents)
                                    leave()
                                }, Modifier.fillMaxWidth())
                            },
                            right = {
                                val nothing = plan?.isEmpty != false
                                PrimaryButton(if (nothing) "Continue" else "Install", {
                                    if (nothing) {
                                        gateway.dispatch(WorkbenchIntent.SkipComponents)
                                        leave()
                                    } else {
                                        apply()
                                    }
                                }, Modifier.fillMaxWidth(), enabled = plan != null)
                            },
                        )
                    } else {
                        PrimaryButton("Apply", ::apply, Modifier.fillMaxWidth(), enabled = plan?.isEmpty == false)
                    }
                }
            }
        }
    }
}

/** iCode's own tools first, then the rest, each with its tick and what it costs. */
@Composable
private fun ComponentList(components: RuntimeComponentsState, choice: Set<String>, editable: Boolean, onToggle: (String) -> Unit) {
    listOf("Used by iCode" to true, "More tools" to false).forEach { (label, own) ->
        SectionLabel(label)
        RuntimeComponents.all.filter { (it.usedBy != null) == own }.forEach { component ->
            val installed = components.isInstalled(component)
            val ticked = component.id in choice
            ComponentRow(
                component = component,
                ticked = ticked,
                enabled = editable,
                cost = when {
                    installed -> if (ticked) INSTALLED else REMOVES
                    else -> components.downloads[component.id]?.let { ByteSize.format(it.archives.values.sum()) }
                        ?: if (components.measuring) "…" else "—"
                },
                onToggle = { onToggle(component.id) },
            )
        }
    }
}

/** "Download 7.1 MB · 1 to remove · 42.3 GB free": what Install or Apply costs, as apt measured it. */
private fun summary(components: RuntimeComponentsState, plan: ComponentPlan?, storage: StorageSummary?): String {
    val change = when {
        plan == null -> "Reading what is installed…"
        plan.isEmpty -> "Nothing to change"
        else -> listOfNotNull(
            plan.install.takeIf { it.isNotEmpty() }?.let {
                ComponentRules.downloadBytes(it, components.downloads)?.let { bytes -> "Download ${ByteSize.format(bytes)}" }
                    ?: if (components.measuring) "Reading package lists…" else "Download size unknown"
            },
            plan.remove.takeIf { it.isNotEmpty() }?.let { "${it.size} to remove" },
        ).joinToString(" · ")
    }
    return listOfNotNull(change, storage?.let { "${ByteSize.format(it.availableBytes)} free" }).joinToString(" · ")
}

/** A Custom choice survives the page being recreated. */
private val ChoiceSaver = Saver<Set<String>?, ArrayList<String>>(
    save = { choice -> choice?.let(::ArrayList) },
    restore = { saved -> saved.toSet() },
)

private const val STANDARD = "Standard"
private const val CUSTOM = "Custom"
