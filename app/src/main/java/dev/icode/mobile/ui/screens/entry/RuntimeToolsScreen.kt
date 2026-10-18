package dev.icode.mobile.ui.screens.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PageHeading
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RuntimeToolPlan
import dev.icode.mobile.domain.RuntimeToolRules
import dev.icode.mobile.domain.RuntimeToolStep
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.RuntimeToolsState
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.OnboardingRoute
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * The Linux runtime's tools (docs/runtime-tools-plan.md §8), as Android Studio sets up its SDK.
 * Offered once after a runtime is built, **Set Up Tools**: Install Type (Standard or Custom), Select
 * Tools (Custom only), Verify Settings, the install, Finish. From Settings, **Runtime Tools**, the SDK
 * Manager: what is installed is ticked; tick to add, untick to remove, Apply, Confirm Changes. A tool
 * with a licence adds the License Agreement last (docs/android-toolchain-plan.md §4.2). While the
 * run goes the page is its progress; Run in Background leaves it to its notification.
 */
@Composable
fun RuntimeToolsScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val tools = state.runtimeTools
    val installed = tools.installed
    // Decided on arrival: answering the offer ends it, and the page must not change what it is meanwhile.
    val setup by rememberSaveable { mutableStateOf(tools.offerPending) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable(stateSaver = ChoiceSaver) { mutableStateOf<Set<String>?>(null) }
    // The licences accepted on the License Agreement, by id.
    var accepted by rememberSaveable(stateSaver = ChoiceSaver) { mutableStateOf<Set<String>?>(null) }
    var stepIndex by rememberSaveable { mutableStateOf(0) }
    // What this page asked apt for, by name: said as done once the plan is empty.
    var applied by rememberSaveable(stateSaver = AppliedSaver) { mutableStateOf<Applied?>(null) }

    val initial = installed?.let(RuntimeToolRules::initialChoice).orEmpty()
    val choice = if (setup && !custom) initial else chosen ?: initial
    val plan = installed?.let { RuntimeToolRules.plan(choice, it) }
    val work = tools.work
    val steps = RuntimeToolRules.steps(setup, custom, licences = plan?.licences?.isNotEmpty() == true)
    val step = steps[stepIndex.coerceIn(steps.indices)]
    val done = applied != null && work == null && plan?.isEmpty == true
    val atStep = work == null && applied == null

    fun leave() {
        if (actions.canGoBack) actions.back() else OnboardingRoute.afterTools(state.systemAccess)?.let(actions.replace)
    }
    fun apply(plan: RuntimeToolPlan) {
        applied = Applied(plan.install.map { it.name }, plan.remove.map { it.name })
        gateway.dispatch(ICodeIntent.ApplyRuntimeTools(choice, accepted.orEmpty()))
    }
    fun finish() {
        gateway.dispatch(ICodeIntent.SkipRuntimeTools)
        leave()
    }
    fun back() = if (stepIndex > 0) stepIndex-- else leave()

    LaunchedEffect(installed) {
        if (installed == null) return@LaunchedEffect
        if (chosen == null) chosen = initial
        gateway.dispatch(ICodeIntent.MeasureRuntimeTools)
        gateway.dispatch(ICodeIntent.RefreshStorage)
    }
    BackHandler(enabled = atStep && stepIndex > 0) { back() }

    Column(Modifier.fillMaxSize()) {
        if (setup) {
            Column(Modifier.padding(start = IcodeSpacing.Space5, end = IcodeSpacing.Space5, top = IcodeSpacing.Space6)) {
                Text("SET UP TOOLS · STEP ${if (atStep) stepIndex + 1 else steps.size} OF ${steps.size}", style = IcodeType.MonoSmall)
            }
        } else {
            IcodeTopBar("Runtime Tools", subtitle = "Linux runtime · Ubuntu 24.04", onBack = { if (atStep) back() else leave() })
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(IcodeSpacing.Space5),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            when {
                work != null -> ToolProgress(work)
                done -> applied?.let { ToolsApplied(it) }
                // Asked, and apt has not published its run yet.
                applied != null -> ToolsStarting()
                else -> {
                    val heading = headingOf(step, setup)
                    PageHeading(heading.first, description = heading.second)
                    when (step) {
                        RuntimeToolStep.InstallType -> InstallTypeChoice(custom, tools, initial) { custom = it }
                        RuntimeToolStep.Select -> ToolList(tools, choice) { id ->
                            val before = chosen.orEmpty()
                            chosen = if (id in before) before - id else before + id
                        }
                        RuntimeToolStep.Verify -> VerifyChanges(plan, tools)
                        RuntimeToolStep.Licence -> plan?.let {
                            LicenceAgreement(it, tools, accepted.orEmpty()) { id, yes ->
                                accepted = if (yes) accepted.orEmpty() + id else accepted.orEmpty() - id
                            }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().background(IcodeColors.Surface),
        ) {
            Divider()
            Column(
                modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space4),
                verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
            ) {
                when {
                    work?.failure != null -> TwoColumnActions(
                        left = {
                            SecondaryButton("Back", {
                                applied = null
                                gateway.dispatch(ICodeIntent.DismissRuntimeTools)
                            }, Modifier.fillMaxWidth())
                        },
                        right = { PrimaryButton("Retry", { plan?.let(::apply) }, Modifier.fillMaxWidth(), enabled = plan?.isEmpty == false) },
                    )
                    work != null || (applied != null && !done) -> {
                        if (work != null && !work.cancellable) {
                            Text(
                                if (work.setup == null) "Packages being set up can't be stopped: dpkg would be left half done."
                                else "This step can't be stopped: it would be left half done.",
                                style = IcodeType.Caption,
                            )
                        }
                        TwoColumnActions(
                            left = {
                                SecondaryButton(
                                    "Cancel",
                                    { gateway.dispatch(ICodeIntent.CancelRuntimeTools) },
                                    Modifier.fillMaxWidth(),
                                    enabled = work?.cancellable == true,
                                )
                            },
                            right = { PrimaryButton("Run in Background", ::leave, Modifier.fillMaxWidth()) },
                        )
                    }
                    done -> PrimaryButton(if (setup) "Finish" else "Done", {
                        applied = null
                        stepIndex = 0
                        if (setup) leave()
                    }, Modifier.fillMaxWidth())
                    else -> StepActions(
                        step,
                        last = stepIndex >= steps.lastIndex,
                        setup = setup,
                        plan = plan,
                        accepted = plan?.licences.orEmpty().all { it in accepted.orEmpty() },
                        tools = tools,
                        availableBytes = state.storage?.availableBytes,
                        onBack = ::back,
                        onSkip = ::finish,
                    ) {
                        when {
                            stepIndex < steps.lastIndex -> stepIndex++
                            plan == null -> Unit
                            plan.isEmpty -> finish()
                            else -> apply(plan)
                        }
                    }
                }
            }
        }
    }
}

/** Each step's title and what it asks, as Android Studio's wizard words them. */
private fun headingOf(step: RuntimeToolStep, setup: Boolean): Pair<String, String> = when (step) {
    RuntimeToolStep.InstallType -> "Install Type" to
        "Choose how the Linux runtime gets its tools. Change them any time in Settings › Runtime Environment › Runtime Tools."
    RuntimeToolStep.Select -> "Select Tools" to
        if (setup) "Tick each tool to install." else "Ticked tools are installed. Tick to add one, untick to remove it."
    RuntimeToolStep.Verify -> if (setup) {
        "Verify Settings" to "This is what will be installed. Go back to change it."
    } else {
        "Confirm Changes" to "This is what will change. Go back to change it."
    }
    RuntimeToolStep.Licence -> "License Agreement" to "Read each licence, then accept it to install."
}

/**
 * The foot of a step: what it costs, then Back (Skip, on the first step of Set Up Tools) and the
 * way on — Next, then on the [last] step Install, Apply, or Finish when there is nothing to do; the
 * License Agreement's only once each licence is [accepted].
 */
@Composable
private fun StepActions(
    step: RuntimeToolStep,
    last: Boolean,
    setup: Boolean,
    plan: RuntimeToolPlan?,
    accepted: Boolean,
    tools: RuntimeToolsState,
    availableBytes: Long?,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    Text(RuntimeToolRules.summary(plan, tools, availableBytes), style = IcodeType.Caption)
    tools.measureFailure?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error), maxLines = 3) }
    val first = step == RuntimeToolStep.InstallType || (!setup && step == RuntimeToolStep.Select)
    val forward = when {
        // Runtime Tools' list moves on as Android Studio's SDK Manager does: Apply, then a confirmation.
        !last -> if (setup || step != RuntimeToolStep.Select) "Next" else "Apply"
        plan == null || plan.isEmpty -> "Finish"
        plan.remove.isEmpty() -> "Install"
        plan.install.isEmpty() -> "Remove"
        else -> "Apply"
    }
    // Runtime Tools moves on only with something to change; Set Up Tools may finish with nothing.
    val enabled = plan != null && (setup || step != RuntimeToolStep.Select || !plan.isEmpty) &&
        (step != RuntimeToolStep.Licence || accepted)
    when {
        setup && first -> TwoColumnActions(
            left = { SecondaryButton("Skip", onSkip, Modifier.fillMaxWidth()) },
            right = { PrimaryButton(forward, onNext, Modifier.fillMaxWidth(), enabled = enabled) },
        )
        first -> PrimaryButton(forward, onNext, Modifier.fillMaxWidth(), enabled = enabled)
        else -> TwoColumnActions(
            left = { SecondaryButton("Back", onBack, Modifier.fillMaxWidth()) },
            right = { PrimaryButton(forward, onNext, Modifier.fillMaxWidth(), enabled = enabled) },
        )
    }
}

/** What Apply asked apt for, by name, for its Done page. */
internal data class Applied(val installed: List<String>, val removed: List<String>)

private val AppliedSaver = Saver<Applied?, ArrayList<ArrayList<String>>>(
    save = { applied -> applied?.let { arrayListOf(ArrayList(it.installed), ArrayList(it.removed)) } },
    restore = { saved -> Applied(saved[0], saved[1]) },
)

/** A Custom choice survives the page being recreated. */
private val ChoiceSaver = Saver<Set<String>?, ArrayList<String>>(
    save = { choice -> choice?.let(::ArrayList) },
    restore = { saved -> saved.toSet() },
)

/** "Git, SSH client and ripgrep". */
internal fun spoken(names: List<String>): String =
    if (names.size < 2) names.joinToString() else names.dropLast(1).joinToString() + " and " + names.last()

/** Standard's tools by name, as its option says them. */
internal val STANDARD_NAMES: String = spoken(RuntimeTools.all.filter { it.id in RuntimeTools.standard }.map { it.name })
