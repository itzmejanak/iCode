package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.BottomBarEntry
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBottomBar
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.TerminalExtraKey
import dev.icode.mobile.ui.LeftToRight
import dev.icode.mobile.ui.TerminalKeyChip
import dev.icode.mobile.ui.terminal.TerminalKeys

/**
 * Control keys a software keyboard cannot produce.
 *
 * The set matches the terminal frame. CTRL and ALT are sticky modifiers rather
 * than immediate keys: tap CTRL then a letter to send Ctrl+letter, which is how
 * a touch terminal makes combinations reachable one-handed. What each key
 * sends is decided by the terminal (see [dev.icode.mobile.ui.terminal.TerminalKeys]).
 */
@Composable
internal fun ExtraKeyRow(
    keys: List<TerminalExtraKey>,
    enabled: Boolean,
    ctrlHeld: Boolean,
    altHeld: Boolean,
    onKey: (TerminalExtraKey) -> Unit,
) = LeftToRight {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IcodeColors.Surface)
            .padding(horizontal = IcodeSpacing.Space2, vertical = IcodeSpacing.Space2),
        // Evenly divided so the whole set fits the width without scrolling.
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        keys.forEach { key ->
            val sticky = when (key) {
                TerminalExtraKey.Ctrl -> ctrlHeld
                TerminalExtraKey.Alt -> altHeld
                else -> false
            }
            TerminalKeyChip(
                key,
                Modifier.weight(1f).height(IcodeSizes.TouchTarget),
                active = sticky,
                compact = true,
                onClick = if (enabled) ({ onKey(key) }) else null,
            )
        }
    }
}

/**
 * The terminal's bar while the keyboard is down (layout.md §17.3): Editor where the editor's bar
 * has Terminal, so one spot goes back and forth between the code and the shell; then, for a running
 * shell, Paste, Stop (Ctrl+C) and Clear (Ctrl+L).
 */
@Composable
internal fun TerminalBar(running: Boolean, keys: TerminalKeys, onEditor: () -> Unit) {
    IcodeBottomBar(
        items = listOf(BottomBarItem("Editor", onClick = onEditor)),
        trailing = {
            BottomBarEntry(BottomBarItem("Paste", onClick = keys::paste, enabled = running))
            BottomBarEntry(BottomBarItem("Stop", onClick = keys::interrupt, enabled = running))
            BottomBarEntry(BottomBarItem("Clear", onClick = keys::clear, enabled = running))
        },
    )
}

@Composable
internal fun RuntimeGate(runtime: RuntimeState, onInstall: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.SectionGap),
    ) {
        when (runtime) {
            is RuntimeState.Downloading -> IcodeSection {
                MonoLine(runtime.label, color = IcodeColors.TextSecondary)
                MonoLine(
                    runtime.transferred + (runtime.fraction?.let { " (${(it * 100).toInt()}%)" } ?: ""),
                    color = IcodeColors.Accent,
                )
            }
            is RuntimeState.Installing -> MonoLine(runtime.label, color = IcodeColors.Accent)
            is RuntimeState.Failed -> {
                EmptyState(title = "The runtime is unavailable", description = runtime.reason)
                if (runtime.recoverable) {
                    PrimaryButton("Retry", onInstall, Modifier.fillMaxWidth())
                }
            }
            else -> {
                EmptyState(
                    title = "The Linux runtime is not installed",
                    description = "Install it to run commands, Git and language tools on this device.",
                )
                PrimaryButton("Install runtime", onInstall, Modifier.fillMaxWidth())
            }
        }
    }
}
