package dev.icode.mobile.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeActionBar
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.files.FileClipboard

/**
 * What waits on iCode's one clipboard, wherever it can be pasted (docs/workspaces-and-files.md):
 * its name, whether it is cut or copied and where Paste puts it — [destination], as the
 * runtime names it; null, at the top of the workspaces, as a workspace of its own — Paste, and ✕ to
 * let it go. What can never be a workspace says why there, and is not pasted
 * ([FileClipboard.notAWorkspace]). The Workspaces page and the Explorer show the same bar, so what
 * was cut on one is seen on the other (docs/workspaces-and-files.md).
 */
@Composable
fun ClipboardBar(clipboard: FileClipboard, destination: String?, onPaste: () -> Unit, modifier: Modifier = Modifier) {
    val refused = clipboard.notAWorkspace.takeIf { destination == null }
    val gateway = LocalICodeGateway.current
    IcodeActionBar(
        title = clipboard.name,
        modifier = modifier,
        caption = refused ?: when {
            destination == null -> if (clipboard.cut) "Moves here as ${workspaces(clipboard)}" else "Copies here as ${workspaces(clipboard)}"
            clipboard.cut -> "Moves into $destination"
            else -> "Copies into $destination"
        },
        captionColor = if (refused != null) IcodeColors.Amber else IcodeColors.TextSecondary,
        leading = {
            Icon(
                if (clipboard.cut) Icons.Outlined.ContentCut else Icons.Outlined.ContentCopy,
                contentDescription = null,
                tint = IcodeColors.Accent,
                modifier = Modifier.size(IcodeSizes.IconMd),
            )
        },
        iconLast = true,
    ) {
        PrimaryButton("Paste", onPaste, enabled = refused == null)
        IconButton(onClick = { gateway.dispatch(FilesIntent.ClearClipboard) }) {
            Icon(Icons.Outlined.Close, contentDescription = "Clear the clipboard", tint = IcodeColors.TextSecondary)
        }
    }
}

/** "a workspace", or "workspaces" for many. */
private fun workspaces(clipboard: FileClipboard) = if (clipboard.items.size == 1) "a workspace" else "workspaces"
