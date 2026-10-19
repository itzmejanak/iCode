package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeTabStrip
import dev.icode.mobile.domain.TerminalSessionState
import dev.icode.mobile.domain.TerminalStatus

/**
 * The terminal's sessions as tabs, each marked by its state — the Terminal icon in green while its
 * shell runs, the error icon in red once it has ended or failed: a shape as well as a colour.
 */
@Composable
internal fun TerminalTabStrip(
    sessions: List<TerminalSessionState>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onReorder: (String, String) -> Unit,
    onClose: (String) -> Unit,
) {
    IcodeTabStrip(
        tabs = sessions,
        keyOf = { it.id },
        title = { it.title },
        description = { "${it.title}, ${statusWord(it.status)}, long press and drag to reorder" },
        selected = selectedId,
        onSelect = onSelect,
        onReorder = onReorder,
        onClose = onClose,
        mark = { session, _ ->
            val running = session.status == TerminalStatus.Running
            Icon(
                if (running) Icons.Outlined.Terminal else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = if (running) IcodeColors.Green else IcodeColors.Error,
                modifier = Modifier.size(IcodeSizes.IconSm),
            )
        },
    )
}

private fun statusWord(status: TerminalStatus): String = when (status) {
    TerminalStatus.Running -> "running"
    TerminalStatus.Exited -> "ended"
    TerminalStatus.Failed -> "failed to start"
}
