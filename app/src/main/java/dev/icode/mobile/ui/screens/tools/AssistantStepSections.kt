package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.SettingLimits
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.copyText
import dev.icode.mobile.ui.terminal.TerminalHost
import dev.icode.mobile.ui.terminal.TerminalKeys
import dev.icode.mobile.ui.terminal.TerminalReplay

// A step's page, part by part (docs/assistant-sheets-plan.md §2.2): its texts under their names, and a
// command's terminal (docs/agent-control-plan.md §6) — the tab it ran in, live while open, else what it
// printed — which the menu's Terminal sheet shows too, for the chat's own tab.

/**
 * A step's [text] under its [label], all of it, selectable, with Copy at its corner. On a page that
 * does not scroll, [modifier] gives it its share, and what does not fit scrolls within it ([bounded]).
 */
@Composable
internal fun StepText(label: String, text: String, modifier: Modifier = Modifier, bounded: Boolean = false) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    StepSection(label, trailing = { CopyChip { copyText(context, label, text, "$label copied", gateway::dispatch) } }, modifier) {
        CodeSurface(Modifier.fillMaxWidth().then(if (bounded) Modifier.weight(1f, fill = false) else Modifier), padding = PaddingValues(IcodeSpacing.Space2)) {
            SelectionContainer {
                Text(
                    text,
                    style = IcodeType.MonoSmall.copy(color = IcodeColors.TextPrimary),
                    modifier = if (bounded) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                )
            }
        }
    }
}

/** A part of a step's page: its name in small capitals, [trailing] at its end, and what it holds. */
@Composable
internal fun StepSection(
    label: String,
    trailing: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.padding(top = IcodeSpacing.Space2), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        StepHeading(label, trailing)
        content()
    }
}

/** A step section's name in small capitals, [trailing] at its end; with no name, [trailing] alone, at the end. */
@Composable
private fun StepHeading(label: String?, trailing: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
        if (label != null) SectionLabel(label, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        trailing()
    }
}

/**
 * Whether a sheet's live terminal is typed into — it has the focus, and the keyboard is up — and what
 * hears its focus; [key] starts it afresh. Typed into, the sheet is the terminal (docs/agent-control-plan.md §6).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberTyping(key: Any?, live: Boolean): Pair<Boolean, (Boolean) -> Unit> {
    var focused by remember(key) { mutableStateOf(false) }
    return (live && focused && WindowInsets.isImeVisible) to { has: Boolean -> focused = has }
}

/**
 * The chat's own terminal tab, from the menu (docs/agent-control-plan.md §6): the shell its commands run
 * in, the user's to use too, as a step's terminal is — typed into, the whole sheet. [tab] is the tab the
 * menu opened, null until it has.
 */
@Composable
internal fun AssistantTerminalSheet(tab: String?, modifier: Modifier, onClose: () -> Unit) {
    val snapshot = LocalICodeSnapshot.current
    val live = tab?.let { id -> snapshot.terminal.sessions.firstOrNull { it.id == id && it.isRunning } }
    val (typing, onFocus) = rememberTyping(tab, live != null)
    IcodeBottomSheet(
        title = "Terminal",
        onClose = onClose,
        modifier = modifier,
        subtitle = live?.title,
        scroll = false,
        ground = if (typing) IcodeColors.BackgroundDeep else IcodeColors.Surface,
        edgeToEdge = typing,
    ) {
        when {
            live != null -> ChatTerminal(live.id, typing, onFocus, onClose, Modifier.weight(1f, fill = typing), heading = false)
            snapshot.runtime !is RuntimeState.Ready -> Hint("Install the Linux runtime to use the terminal")
            tab != null -> Hint("This terminal has ended: Terminal opens a new one")
            else -> Hint("Opening…")
        }
    }
}

/**
 * A chat's terminal tab [session], live: the Terminal panel's own view, so what is typed there goes to
 * the command running, or to the shell once it has ended — the user's own commands — and the tab's
 * later output shows here as there. Its [heading] names the tab, with Open in Terminal, which shows the
 * tab there, the sheet closed. For a command's step, [ended] — its result — says how it ended under it.
 * [onFocus] hears the view take the keyboard and let it go. [typing], it is the whole sheet, black and
 * edge to edge as the Terminal panel (docs/agent-control-plan.md §6): the terminal down to the keyboard,
 * the extra keys on it, and — while the sheet's ⓘ says so ([showCommand]) — [command] over its top, with
 * Copy and Open in Terminal. One view throughout, so the shell and its keyboard stay as they are.
 */
@Composable
internal fun ChatTerminal(
    session: String,
    typing: Boolean,
    onFocus: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    heading: Boolean = true,
    command: String? = null,
    showCommand: Boolean = false,
    ended: String? = null,
) {
    val gateway = LocalICodeGateway.current
    val settings = LocalWorkspacePreferences.current.terminal
    val tab = LocalICodeSnapshot.current.terminal.sessions.firstOrNull { it.id == session }
    val keys = remember { TerminalKeys() }
    LaunchedEffect(settings.extraKeys) { keys.keepOnly(settings.extraKeys) }
    var fontScale by rememberSaveable { mutableFloatStateOf(1f) }
    val open: @Composable RowScope.() -> Unit = {
        CornerChip("Open in Terminal", Icons.Outlined.Terminal) {
            onClose()
            gateway.dispatch(ICodeIntent.ShowTerminalSession(session))
        }
    }
    val context = LocalContext.current
    Column(
        modifier.padding(top = IcodeSpacing.Space2),
        verticalArrangement = Arrangement.spacedBy(if (typing) IcodeSpacing.None else IcodeSpacing.Space1),
    ) {
        if (!typing) StepHeading(listOfNotNull("Terminal", tab?.title).joinToString(" · ").takeIf { heading }, open)
        // Its 300 dp, or what the page has left; typed into, all of it.
        TerminalFrame(IcodeSizes.StepTerminal.takeUnless { typing }, Modifier.weight(1f, fill = typing)) {
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
                onFocus = onFocus,
            )
            if (typing && showCommand && command != null) RanCommand(command, open, Modifier.align(Alignment.TopCenter))
        }
        if (!typing && ended != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                Text(ended.lineSequence().first(), style = IcodeType.Caption.copy(color = IcodeColors.TextMuted), modifier = Modifier.weight(1f))
                CopyChip { copyText(context, "Output", ended, "Output copied", gateway::dispatch) }
            }
        }
        // The keys a soft keyboard lacks, on it; with no keyboard up, nothing is typed.
        if (typing && settings.extraKeys.isNotEmpty()) {
            ExtraKeyRow(settings.extraKeys, enabled = tab?.isRunning == true, ctrlHeld = keys.ctrl, altHeld = keys.alt, onKey = keys::press)
        }
    }
}

/** ⓘ: the command a terminal typed into runs, [shown] over it or not. */
@Composable
internal fun CommandToggle(shown: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (shown) Icons.Filled.Info else Icons.Outlined.Info,
            contentDescription = if (shown) "Hide the command" else "Show the command",
            tint = if (shown) IcodeColors.Accent else IcodeColors.TextSecondary,
        )
    }
}

/** The command a terminal typed into runs, over its top: all of it, selectable, Copy and [trailing] at its corner. */
@Composable
private fun RanCommand(command: String, trailing: @Composable RowScope.() -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    Column(
        modifier.fillMaxWidth().padding(IcodeSpacing.Space2).clip(IcodeShapes.Small).background(IcodeColors.Surface)
            .padding(IcodeSpacing.Space3),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        StepHeading("Command") {
            CopyChip { copyText(context, "Command", command, "Command copied", gateway::dispatch) }
            trailing()
        }
        SelectionContainer { Text(command, style = IcodeType.MonoSmall.copy(color = IcodeColors.TextPrimary)) }
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

/**
 * A terminal on a step's page: the terminal's own ground, [height] tall, its corners as a code block's;
 * without a height, flush in all [modifier] gives it, as the Terminal panel's.
 */
@Composable
private fun TerminalFrame(height: Dp?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxWidth().then(
            if (height != null) Modifier.clip(IcodeShapes.Small).background(IcodeColors.BackgroundDeep).padding(vertical = IcodeSpacing.Space1).height(height)
            else Modifier.fillMaxHeight(),
        ),
    ) { content() }
}
