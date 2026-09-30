package dev.icode.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.domain.ItemClipboard
import dev.icode.mobile.domain.WorkbenchIntent

/**
 * What waits on iCode's one clipboard, wherever it can be pasted (docs/workspaces-page-plan.md
 * §2.7): its name, whether it was cut or copied, Paste, and ✕ to let it go. The Workspaces page and
 * the Explorer show the same bar, so what was cut on one is seen on the other.
 */
@Composable
fun ClipboardBar(clipboard: ItemClipboard, onPaste: () -> Unit, modifier: Modifier = Modifier) {
    val gateway = LocalWorkbenchGateway.current
    Column(modifier.fillMaxWidth().background(IcodeColors.SurfaceElevated)) {
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = IcodeSpacing.Space4, end = IcodeSpacing.Space1, top = IcodeSpacing.Space2, bottom = IcodeSpacing.Space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            Icon(
                if (clipboard.cut) Icons.Outlined.ContentCut else Icons.Outlined.ContentCopy,
                contentDescription = null,
                tint = IcodeColors.Accent,
                modifier = Modifier.size(IcodeSizes.IconMd),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    clipboard.name,
                    style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(if (clipboard.cut) "Cut — moves when pasted" else "Copied — pastes as a copy", style = IcodeType.Caption)
            }
            PrimaryButton("Paste", onPaste)
            IconButton(onClick = { gateway.dispatch(WorkbenchIntent.ClearClipboard) }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear the clipboard", tint = IcodeColors.TextSecondary)
            }
        }
    }
}
