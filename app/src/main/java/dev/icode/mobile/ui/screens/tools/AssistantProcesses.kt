package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.ManagedProcess
import dev.icode.mobile.domain.ManagedProcessState
import dev.icode.mobile.domain.ProcessRules
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.ui.LocalWorkbenchGateway
import kotlinx.coroutines.delay

// The processes the assistant started (docs/runtime-foundation-plan.md §3.7): a row above the
// composer while any is listed, and the sheet it opens.

/** "2 running · 1 ended", and a caret: the Processes sheet is a tap away. */
@Composable
internal fun AssistantProcessesRow(processes: List<ManagedProcess>, onOpen: () -> Unit) {
    val running = processes.any { it.running }
    Row(
        Modifier.fillMaxWidth().minimumInteractiveComponentSize().clickable(onClickLabel = "Show processes", onClick = onOpen)
            .padding(horizontal = IcodeSpacing.Space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        StatusDot(if (running) IcodeColors.Green else IcodeColors.TextMuted)
        Text(ProcessRules.summary(processes), style = IcodeType.Caption)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
    }
}

/**
 * Every process, this workspace's first: what it runs and where, its state, its ports — each opens
 * the phone's browser — and the end of its output; Stop while it runs, Remove once it has ended.
 */
@Composable
internal fun AssistantProcessesSheet(processes: List<ManagedProcess>, workspace: String?, modifier: Modifier, onClose: () -> Unit) {
    // A running process's age moves on while the sheet is open, output or not.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    IcodeBottomSheet(title = "Processes", onClose = onClose, modifier = modifier) {
        Hint("They keep running while iCode runs, in the background too. Android may end them when many processes run on the phone.")
        processes.sortedBy { it.workspace != workspace }.forEach { process ->
            ProcessCard(process, elsewhere = process.workspace != workspace, now = now)
        }
    }
}

@Composable
private fun ProcessCard(process: ManagedProcess, elsewhere: Boolean, now: Long) {
    val gateway = LocalWorkbenchGateway.current
    IcodeCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            StatusDot(stateColor(process.state))
            Text(process.name, style = IcodeType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(ProcessRules.stateLabel(process.state, now, process.startedAt), style = IcodeType.Caption)
        }
        Text(process.command, style = IcodeType.MonoSmall.copy(color = IcodeColors.TextPrimary), modifier = Modifier.padding(top = IcodeSpacing.Space1))
        val place = if (elsewhere) "${AssistantRules.workspaceName(process.workspace)} · ${process.directory}" else process.directory
        Text("in $place", style = IcodeType.MonoSmall.copy(color = IcodeColors.TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (process.ports.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                process.ports.forEach { port -> PortLink(port) { gateway.dispatch(WorkbenchIntent.OpenExternalUrl(ProcessRules.address(port))) } }
            }
        }
        val lines = process.tail.trimEnd('\n').lines().takeLast(TAIL_LINES).joinToString("\n")
        if (lines.isNotBlank()) {
            // Lines as printed, never wrapped: a long one scrolls sideways, as in a terminal.
            Box(
                Modifier.fillMaxWidth().padding(vertical = IcodeSpacing.Space2).background(IcodeColors.Background, IcodeShapes.Small)
                    .horizontalScroll(rememberScrollState()).padding(IcodeSpacing.Space2),
            ) {
                Text(lines, style = IcodeType.MonoSmall, softWrap = false)
            }
        }
        if (process.running) {
            DangerButton("Stop", { gateway.dispatch(WorkbenchIntent.StopProcess(process.id)) }, Modifier.fillMaxWidth())
        } else {
            SecondaryButton("Remove", { gateway.dispatch(WorkbenchIntent.ForgetProcess(process.id)) }, Modifier.fillMaxWidth())
        }
    }
}

/** `127.0.0.1:8000`, opening in the phone's browser. */
@Composable
private fun PortLink(port: Int, onOpen: () -> Unit) {
    Row(
        Modifier.minimumInteractiveComponentSize().clip(IcodeShapes.Small).clickable(onClickLabel = "Open in the browser", onClick = onOpen)
            .padding(horizontal = IcodeSpacing.Space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm))
        Text("127.0.0.1:$port", style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent))
    }
}

/** Green while it runs; red when it failed on its own; muted when it succeeded or was stopped. */
@Composable
private fun stateColor(state: ManagedProcessState): Color = when {
    state == ManagedProcessState.Running -> IcodeColors.Green
    state is ManagedProcessState.Ended && !state.stopped && state.code != 0 -> IcodeColors.Error
    else -> IcodeColors.TextMuted
}

private const val CLOCK_MILLIS = 1_000L
private const val TAIL_LINES = 12
