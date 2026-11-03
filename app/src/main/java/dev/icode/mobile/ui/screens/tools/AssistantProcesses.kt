package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.ManagedProcess
import dev.icode.mobile.domain.ManagedProcessState
import dev.icode.mobile.domain.ProcessRules
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.ui.LocalICodeGateway
import kotlinx.coroutines.delay

// The processes the assistant started (docs/runtime-foundation-plan.md §3.7): a row above the
// composer while any is listed, and the sheet it opens.

/** "2 running · 1 ended", and a caret: the Processes sheet is a tap away. */
@Composable
internal fun AssistantProcessesRow(processes: List<ManagedProcess>, onOpen: () -> Unit) {
    val running = processes.any { it.running }
    Row(
        Modifier.fillMaxWidth().minimumInteractiveComponentSize().clickable(onClickLabel = "Show processes", onClick = onOpen)
            .padding(horizontal = IcodeSpacing.Gutter),
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
 * Every process, this workspace's first (docs/runtime-foundation-plan.md §6 P4), each one row: its
 * state, name and command, its ports — each opens the phone's browser — and Stop while it runs, or
 * Remove once it has ended. A tap on a row is its output, in the terminal: the sheet lists, the
 * terminal shows.
 */
@Composable
internal fun AssistantProcessesSheet(processes: List<ManagedProcess>, workspace: String?, modifier: Modifier, onClose: () -> Unit) {
    val gateway = LocalICodeGateway.current
    // A running process's age moves on while the sheet is open.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    IcodeBottomSheet(title = "Processes", onClose = onClose, modifier = modifier) {
        // One group: the rows in a raised box of their own, divided within, as a settings group.
        IcodeCard(Modifier.fillMaxWidth(), padding = PaddingValues()) {
            processes.sortedBy { it.workspace != workspace }.forEachIndexed { index, process ->
                if (index > 0) Divider()
                ProcessRow(process, elsewhere = process.workspace != workspace, now = now) {
                    gateway.dispatch(ICodeIntent.ShowProcessOutput(process.id))
                    onClose()
                }
            }
        }
        Text(
            "They run while iCode runs, in the background too; Android may end them when many processes run.",
            style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
        )
    }
}

/**
 * One process: its dot on its name's line, its state at the end; its command, then its ports, from
 * where its name starts; Stop, or Remove once it has ended, at the row's end. A tap shows its output.
 */
@Composable
private fun ProcessRow(process: ManagedProcess, elsewhere: Boolean, now: Long, onShowOutput: () -> Unit) {
    val gateway = LocalICodeGateway.current
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Show its output", onClick = onShowOutput)
            .padding(start = IcodeSpacing.Space3, top = IcodeSpacing.Space2, bottom = IcodeSpacing.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(IcodeSizes.IconSm), contentAlignment = Alignment.CenterStart) { StatusDot(stateColor(process.state)) }
                Text(
                    process.name,
                    style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(ProcessRules.stateLabel(process.state, now, process.startedAt), style = IcodeType.Caption.copy(color = IcodeColors.TextMuted), maxLines = 1)
            }
            Column(Modifier.padding(start = IcodeSizes.IconSm), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro)) {
                // Another workspace's says which.
                val command = if (elsewhere) "${AssistantPlace.name(process.workspace)} · ${process.command}" else process.command
                Text(command, style = IcodeType.MonoSmall.copy(color = IcodeColors.TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (process.ports.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                        process.ports.forEach { port -> PortLink(port) { gateway.dispatch(ICodeIntent.OpenExternalUrl(ProcessRules.address(port))) } }
                    }
                }
            }
        }
        if (process.running) {
            IconButton(onClick = { gateway.dispatch(ICodeIntent.StopProcess(process.id)) }) {
                Icon(Icons.Outlined.StopCircle, contentDescription = "Stop ${process.name}", tint = IcodeColors.Error, modifier = Modifier.size(IcodeSizes.IconMd))
            }
        } else {
            IconButton(onClick = { gateway.dispatch(ICodeIntent.ForgetProcess(process.id)) }) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove ${process.name}", tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconMd))
            }
        }
    }
}

/** `127.0.0.1:8000`, opening in the phone's browser: starting where the text above starts, as tall as a touch target. */
@Composable
private fun PortLink(port: Int, onOpen: () -> Unit) {
    Row(
        Modifier.heightIn(min = IcodeSizes.BarChoice).clip(IcodeShapes.Small).clickable(onClickLabel = "Open in the browser", onClick = onOpen),
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
