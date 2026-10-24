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
import dev.icode.mobile.domain.ItemClipboard
import dev.icode.mobile.domain.ICodeIntent

/**
 * What waits on iCode's one clipboard, wherever it can be pasted (docs/workspaces-page-plan.md
 * §2.7): its name, whether it is cut or copied and where Paste puts it — [destination], as the
 * runtime names it; null, at the top of the workspaces, as a workspace of its own — Paste, and ✕ to
 * let it go. What can never be a workspace says why there, and is not pasted
 * ([ItemClipboard.notAWorkspace]). The Workspaces page and the Explorer show the same bar, so what
 * was cut on one is seen on the other (docs/file-explorer-unification-plan.md E3).
 */
@Composable
fun ClipboardBar(clipboard: ItemClipboard, destination: String?, onPaste: () -> Unit, modifier: Modifier = Modifier) {
    val refused = clipboard.notAWorkspace.takeIf { destination == null }
    val gateway = LocalICodeGateway.current
    IcodeActionBar(
        title = clipboard.name,
        modifier = modifier,
        caption = refused ?: when {
            destination == null -> if (clipboard.cut) "Moves here as a workspace" else "Copies here as a workspace"
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
        IconButton(onClick = { gateway.dispatch(ICodeIntent.ClearClipboard) }) {
            Icon(Icons.Outlined.Close, contentDescription = "Clear the clipboard", tint = IcodeColors.TextSecondary)
        }
    }
}
