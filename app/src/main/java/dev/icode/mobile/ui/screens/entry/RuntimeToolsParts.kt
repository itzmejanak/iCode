package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeCheckbox
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeOptionRow
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IndeterminateProgressTrack
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.PageHeading
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.RuntimeTool
import dev.icode.mobile.domain.RuntimeToolAction
import dev.icode.mobile.domain.RuntimeToolPlan
import dev.icode.mobile.domain.RuntimeToolRules
import dev.icode.mobile.domain.RuntimeToolStatus
import dev.icode.mobile.domain.RuntimeToolWork
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.RuntimeToolsState

/** Install Type: Standard, what iCode's own features use, with its download; or Custom, the list. */
@Composable
internal fun InstallTypeChoice(custom: Boolean, tools: RuntimeToolsState, standard: Set<String>, onCustom: (Boolean) -> Unit) {
    val plan = tools.installed?.let { RuntimeToolRules.plan(standard, it) }
    val cost = when {
        plan == null -> null
        plan.install.isEmpty() -> "Installed"
        else -> RuntimeToolRules.downloadBytes(plan.install, tools.downloads)?.let(ByteSize::format)
    }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        IcodeOptionRow(
            title = "Standard",
            description = "$STANDARD_NAMES: the tools iCode's own features use. Recommended.",
            selected = !custom,
            onSelect = { onCustom(false) },
            trailing = cost,
        )
        IcodeOptionRow(
            title = "Custom",
            description = "Choose each tool the Linux runtime installs: Python, the build tools and the Android SDK among them.",
            selected = custom,
            onSelect = { onCustom(true) },
        )
    }
}

/** Select Tools: iCode's own first, then the rest; each row its tick, what it is, and its status. */
@Composable
internal fun ToolList(tools: RuntimeToolsState, choice: Set<String>, onToggle: (String) -> Unit) {
    listOf("Used by iCode" to true, "More tools" to false).forEach { (label, own) ->
        IcodeSection(label) {
            Column {
                RuntimeTools.all.filter { (it.usedBy != null) == own }.forEachIndexed { index, tool ->
                    if (index > 0) Divider()
                    val ticked = tool.id in choice
                    ToolRow(tool, ticked, RuntimeToolRules.status(tool, ticked, tools)) { onToggle(tool.id) }
                }
            }
        }
    }
}

/** One tool, flat as a table row: the tick (the whole row takes the tap), its name and use, and its status. */
@Composable
private fun ToolRow(tool: RuntimeTool, ticked: Boolean, status: RuntimeToolStatus, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IcodeShapes.Small)
            .toggleable(value = ticked, role = Role.Checkbox) { onToggle() }
            .padding(vertical = IcodeSpacing.Space2),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        IcodeCheckbox(checked = ticked, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(top = IcodeSpacing.Space2)) {
            Text(tool.name, style = IcodeType.Body)
            Text(tool.summary, style = IcodeType.Caption)
            tool.usedBy?.let { Text("Used by $it", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted)) }
        }
        Text(
            label(status),
            modifier = Modifier.padding(top = IcodeSpacing.Space2),
            style = IcodeType.Caption.copy(color = color(status)),
        )
    }
}

/** Verify Settings, or Confirm Changes: each tool to install with its download, each to remove, and the total. */
@Composable
internal fun VerifyChanges(plan: RuntimeToolPlan?, tools: RuntimeToolsState) {
    when {
        plan == null -> Text("Reading what is installed…", style = IcodeType.Body)
        plan.isEmpty -> Text("Nothing will be installed or removed.", style = IcodeType.Body)
        else -> {
            if (plan.install.isNotEmpty()) {
                IcodeSection("Install") {
                    plan.install.forEach { tool -> DetailRow(tool.name, label(RuntimeToolRules.status(tool, true, tools))) }
                }
            }
            if (plan.remove.isNotEmpty()) {
                IcodeSection("Remove") {
                    plan.remove.forEach { tool -> DetailRow(tool.name, "Removed", valueColor = IcodeColors.Amber) }
                }
            }
        }
    }
}

/**
 * The run, as Android Studio's Downloading Components: the bar (it sweeps while apt reads and
 * downloads, fills by the packages dpkg sets up, then by a setup's own share), the step and what is
 * in hand, and Show Details — the last lines apt or the setup printed. A failure says their words,
 * its details open.
 */
@Composable
internal fun ToolProgress(work: RuntimeToolWork) {
    val failure = work.failure
    var details by rememberSaveable(failure) { mutableStateOf(failure != null) }
    PageHeading(
        if (failure == null) work.title else if (work.action == RuntimeToolAction.Install) "Installation Failed" else "Removal Failed",
        description = if (failure == null) "The Linux runtime is changing. You can leave this page; the notification follows it." else null,
    )
    IcodeSection {
        if (failure != null) {
            NoticeCard(tone = IcodeTone.Error, modifier = Modifier.fillMaxWidth()) {
                Text(failure, style = IcodeType.Caption)
            }
        } else {
            work.fraction?.let { ProgressTrack(it, Modifier.fillMaxWidth()) }
                ?: IndeterminateProgressTrack(Modifier.fillMaxWidth())
            Text(work.step, style = IcodeType.Caption.copy(color = IcodeColors.Accent))
            work.item?.let { MonoLine(it, style = IcodeType.MonoSmall, maxLines = 1) }
        }
    }
    IcodeSection {
        IcodeTextButton(if (details) "Hide Details" else "Show Details", { details = !details })
        if (details) {
            CodeSurface(Modifier.fillMaxWidth()) {
                work.log.ifEmpty { listOf("Nothing has been printed yet.") }.forEach { MonoLine(it, style = IcodeType.MonoSmall) }
            }
        }
    }
}

/**
 * License Agreement, as Android Studio's: for each licence what is installed needs, which tools need
 * it, its text, and Decline or Accept. Nothing with a licence installs until it is accepted.
 */
@Composable
internal fun LicenceAgreement(plan: RuntimeToolPlan, tools: RuntimeToolsState, accepted: Set<String>, onAnswer: (String, Boolean) -> Unit) {
    plan.licences.forEach { id ->
        IcodeSection(id) {
            Text("For ${spoken(plan.install.filter { it.licence == id }.map { it.name })}.", style = IcodeType.Caption)
            CodeSurface(Modifier.fillMaxWidth().height(IcodeSizes.LicenceText)) {
                Text(tools.licences[id] ?: "Reading the licence…", style = IcodeType.MonoSmall, modifier = Modifier.verticalScroll(rememberScrollState()))
            }
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                IcodeOptionRow("Decline", "Nothing that needs it is installed.", selected = id !in accepted, onSelect = { onAnswer(id, false) })
                IcodeOptionRow("Accept", "I have read the licence and agree to its terms.", selected = id in accepted, onSelect = { onAnswer(id, true) })
            }
        }
    }
}

/** Asked: the run is starting, and apt has not said anything yet. */
@Composable
internal fun ToolsStarting() {
    PageHeading("Starting…", description = "Preparing the Linux runtime's package manager.")
    IndeterminateProgressTrack(Modifier.fillMaxWidth())
}

/** Finish: what changed, by name. */
@Composable
internal fun ToolsApplied(applied: Applied) {
    IcodeSection {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = IcodeColors.Green, modifier = Modifier.size(IcodeSizes.IconXl))
        PageHeading(
            if (applied.removed.isEmpty()) "Tools Installed" else "Changes Applied",
            description = listOfNotNull(
                applied.installed.takeIf { it.isNotEmpty() }?.let { "Installed ${spoken(it)}." },
                applied.removed.takeIf { it.isNotEmpty() }?.let { "Removed ${spoken(it)}." },
            ).joinToString(" "),
        )
    }
}

/** What a row's status says. */
private fun label(status: RuntimeToolStatus): String = when (status) {
    RuntimeToolStatus.Installed -> "Installed"
    RuntimeToolStatus.WillBeRemoved -> "Will be removed"
    is RuntimeToolStatus.Download -> ByteSize.format(status.bytes)
    RuntimeToolStatus.Measuring -> "Measuring…"
    RuntimeToolStatus.SizeUnknown -> "Size unknown"
}

@Composable
private fun color(status: RuntimeToolStatus) = when (status) {
    RuntimeToolStatus.Installed -> IcodeColors.Green
    RuntimeToolStatus.WillBeRemoved -> IcodeColors.Amber
    is RuntimeToolStatus.Download -> IcodeColors.TextSecondary
    RuntimeToolStatus.Measuring, RuntimeToolStatus.SizeUnknown -> IcodeColors.TextMuted
}
