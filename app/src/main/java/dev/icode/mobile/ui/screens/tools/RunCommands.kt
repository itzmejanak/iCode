package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeActionSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RunConfiguration
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.SheetIcon

/**
 * What the Run tab asks about the commands the user saved (docs/runtime-and-terminal.md), one at a time:
 * a new one, from the header's +; or, for the saved command [id], its sheet, from a long press, then
 * its edit or its delete.
 */
internal data class RunCommandAsk(val what: What, val id: String? = null) {
    enum class What { New, Sheet, Edit, Delete }

    companion object {
        /** Kept as the screen turns. */
        val Saver = listSaver<RunCommandAsk?, String>(
            save = { listOfNotNull(it?.what?.name, it?.id) },
            restore = { saved -> saved.firstOrNull()?.let { RunCommandAsk(What.valueOf(it), saved.getOrNull(1)) } },
        )
    }
}

/**
 * The Run tab's [ask], over the whole tools panel: a saved command's sheet
 * (docs/workspaces-and-files.md) — Edit…, and Delete, last — or the sheet that makes, changes or
 * deletes one. [onAsk] moves on
 * to the next, or to none.
 */
@Composable
internal fun BoxScope.RunCommandAsks(ask: RunCommandAsk?, onAsk: (RunCommandAsk?) -> Unit) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val command = state.run.configurations.filterIsInstance<RunConfiguration.Command>().firstOrNull { it.id == ask?.id }
    val done = { onAsk(null) }
    when (ask?.what) {
        null -> Unit
        RunCommandAsk.What.New -> RunCommandSheet(existing = null, onDismiss = done)
        RunCommandAsk.What.Sheet -> command?.let {
            IcodeSheetOverlay(onClose = done) {
                IcodeActionSheet(
                    title = it.name,
                    actions = listOf(
                        IcodeMenuAction("Edit…", Icons.Outlined.Edit, { onAsk(RunCommandAsk(RunCommandAsk.What.Edit, it.id)) }),
                        IcodeMenuAction("Delete", Icons.Outlined.Delete, { onAsk(RunCommandAsk(RunCommandAsk.What.Delete, it.id)) }, destructive = true),
                    ),
                    onClose = done,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    detail = it.command,
                    leading = { SheetIcon(Icons.Outlined.Terminal, IcodeColors.Accent) },
                )
            }
        }
        RunCommandAsk.What.Edit -> command?.let { RunCommandSheet(existing = it, onDismiss = done) }
        RunCommandAsk.What.Delete -> command?.let {
            IcodeConfirmSheet(
                title = "Delete ${it.name}?",
                confirmLabel = "Delete",
                destructive = true,
                onConfirm = { gateway.dispatch(ICodeIntent.DeleteRunCommand(it.id)); done() },
                onDismiss = done,
            ) {
                MonoLine(it.command, style = IcodeType.MonoSmall, maxLines = 2)
            }
        }
    }
}

/** A saved command, new or changed: a name, which may be left to the command's first word, and the command line. */
@Composable
private fun RunCommandSheet(existing: RunConfiguration.Command?, onDismiss: () -> Unit) {
    val gateway = LocalICodeGateway.current
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var command by rememberSaveable(existing?.id) { mutableStateOf(existing?.command.orEmpty()) }
    IcodeConfirmSheet(
        title = if (existing == null) "New command" else "Edit command",
        confirmLabel = "Save",
        confirmEnabled = command.isNotBlank(),
        onConfirm = {
            gateway.dispatch(ICodeIntent.SaveRunCommand(existing?.id, name, command))
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        Text("It runs in bash at the workspace's top, with your login environment.", style = IcodeType.Body, color = IcodeColors.TextSecondary)
        LabeledTextField("Command", command, { command = it }, placeholder = "npm run dev", literal = true)
        LabeledTextField("Name", name, { name = it }, placeholder = "dev server")
    }
}
