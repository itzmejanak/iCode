package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeActionSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.domain.AssistantUndo
import dev.icode.mobile.domain.UndoIntent
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.SheetIcon

/**
 * Undo back to before the user's message at [index] (docs/assistant-and-mcp.md): its place's files and
 * the chat, the files only, or the chat only. Files only where they were kept, [checkpoint].
 */
@Composable
internal fun AssistantUndoSheet(index: Int, checkpoint: Boolean, modifier: Modifier, onClose: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val undo = { files: Boolean, chat: Boolean ->
        gateway.dispatch(UndoIntent.Message(index, files, chat))
        onClose()
    }
    IcodeActionSheet(
        title = "Undo",
        detail = "Back to before this message",
        actions = listOfNotNull(
            IcodeMenuAction("Files and chat", Icons.AutoMirrored.Outlined.Undo, { undo(true, true) }).takeIf { checkpoint },
            IcodeMenuAction("Files only", Icons.Outlined.FolderOpen, { undo(true, false) }).takeIf { checkpoint },
            IcodeMenuAction("Chat only", Icons.Outlined.ChatBubbleOutline, { undo(false, true) }),
        ),
        onClose = onClose,
        modifier = modifier,
        leading = { SheetIcon(Icons.AutoMirrored.Outlined.Undo, IcodeColors.Accent) },
    )
}

/** An Undo waiting for the user: the files changed outside the chat's own work since, undone too unless they cancel. */
@Composable
internal fun AssistantUndoAsk(undo: AssistantUndo) {
    val gateway = LocalICodeGateway.current
    IcodeConfirmSheet(
        title = "Changed outside this chat",
        confirmLabel = "Undo anyway",
        onConfirm = { gateway.dispatch(UndoIntent.Message(undo.index, undo.files, undo.chat, confirmed = true)) },
        onDismiss = { gateway.dispatch(UndoIntent.Dismiss) },
        destructive = true,
    ) {
        Column(Modifier.heightIn(max = IcodeSizes.TouchTarget * VISIBLE_PATHS).verticalScroll(rememberScrollState())) {
            undo.changed.forEach { MonoLine(it, color = IcodeColors.TextSecondary, style = IcodeType.MonoSmall, maxLines = 1) }
        }
    }
}

/** The paths the list shows before it scrolls. */
private const val VISIBLE_PATHS = 4
