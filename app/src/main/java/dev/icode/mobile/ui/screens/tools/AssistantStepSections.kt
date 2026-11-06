package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.SettingLimits
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.copyText
import dev.icode.mobile.ui.terminal.TerminalHost
import dev.icode.mobile.ui.terminal.TerminalKeys
import dev.icode.mobile.ui.terminal.TerminalReplay

// A step's page, part by part (docs/assistant-sheets-plan.md §2.2): its texts under their names, and a
// command's terminal (docs/agent-control-plan.md §6) — the tab it runs in, live, then what it printed.

/** A step's [text] under its [label], all of it, selectable, with Copy at its corner. */
@Composable
internal fun StepText(label: String, text: String) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    StepSection(label, trailing = { CopyChip { copyText(context, label, text, "$label copied", gateway::dispatch) } }) {
        CodeSurface(Modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.Space2)) {
            SelectionContainer { Text(text, style = IcodeType.MonoSmall.copy(color = IcodeColors.TextPrimary)) }
        }
    }
}

/** A part of a step's page: its name in small capitals, [trailing] at its end, and what it holds. */
@Composable
internal fun StepSection(label: String, trailing: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = IcodeSpacing.Space2), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            SectionLabel(label, Modifier.weight(1f))
            trailing()
        }
        content()
    }
}

/**
 * The terminal tab [session] a running command runs in, live: the Terminal panel's own view, so what
 * is typed there goes to the command, with the panel's extra keys under it; Open in Terminal shows the
 * tab there, the sheet closed.
 */
@Composable
internal fun StepTerminal(session: String, onClose: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val settings = LocalWorkspacePreferences.current.terminal
    val tab = LocalICodeSnapshot.current.terminal.sessions.firstOrNull { it.id == session }
    val keys = remember { TerminalKeys() }
    LaunchedEffect(settings.extraKeys) { keys.keepOnly(settings.extraKeys) }
    var fontScale by rememberSaveable { mutableFloatStateOf(1f) }
    StepSection(listOfNotNull("Terminal", tab?.title).joinToString(" · "), trailing = {
        CornerChip("Open in Terminal", Icons.Outlined.Terminal) {
            onClose()
            gateway.dispatch(ICodeIntent.ShowTerminalSession(session))
        }
    }) {
        TerminalFrame(IcodeSizes.StepTerminal) {
            TerminalHost(
                sessionId = session,
                settings = settings,
                fontScale = SettingLimits.zoomScale(fontScale, settings.fontSizeSp),
                keys = keys,
                onScale = { fontScale = SettingLimits.zoomScale(fontScale * it, settings.fontSizeSp) },
                onUrl = { gateway.dispatch(ICodeIntent.OpenExternalUrl(it)) },
                // The tab is the chat's; it closes from the Terminal panel.
                onEndedEnter = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (settings.extraKeys.isNotEmpty()) {
            ExtraKeyRow(settings.extraKeys, enabled = tab?.isRunning == true, ctrlHeld = keys.ctrl, altHeld = keys.alt, onKey = keys::press)
        }
    }
}

/**
 * What a command printed, drawn as its terminal drew it, in a frame that fits it up to the live
 * terminal's height, then scrolls; how it ended — [output]'s first line — under it. Copy takes
 * [output], the text the model read.
 */
@Composable
internal fun StepReplay(printed: String, output: String) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    val settings = LocalWorkspacePreferences.current.terminal
    val density = LocalDensity.current
    var fontScale by rememberSaveable { mutableFloatStateOf(1f) }
    var fits by remember(printed) { mutableStateOf<Dp?>(null) }
    StepSection("Output", trailing = { CopyChip { copyText(context, "Output", output, "Output copied", gateway::dispatch) } }) {
        TerminalFrame(fits?.coerceAtMost(IcodeSizes.StepTerminal) ?: IcodeSizes.StepTerminal) {
            TerminalReplay(
                printed = printed,
                settings = settings,
                fontScale = SettingLimits.zoomScale(fontScale, settings.fontSizeSp),
                onScale = { fontScale = SettingLimits.zoomScale(fontScale * it, settings.fontSizeSp) },
                onUrl = { gateway.dispatch(ICodeIntent.OpenExternalUrl(it)) },
                onFits = { rows, rowPx -> fits = with(density) { ((rows + 1) * rowPx).toDp() } },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(output.lineSequence().first(), style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
    }
}

/** A terminal on a step's page: the terminal's own ground, [height] tall, its corners as a code block's. */
@Composable
private fun TerminalFrame(height: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(IcodeShapes.Small).background(IcodeColors.BackgroundDeep)
            .padding(vertical = IcodeSpacing.Space1).height(height),
    ) { content() }
}
