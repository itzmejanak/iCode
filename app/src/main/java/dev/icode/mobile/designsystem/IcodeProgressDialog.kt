package dev.icode.mobile.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A long operation in hand, drawn as the reference file manager draws one: its
 * title with Hide at the right, the item in hand on one line, and a bar. While
 * the total is still being counted the bar sweeps — one frozen at zero reads as
 * a hang. A tap outside does nothing; Back and Hide send the work to the
 * background, where it goes on.
 */
@Composable
fun IcodeProgressDialog(
    title: String,
    /** The item in hand; "…" while there is none yet. */
    detail: String,
    /** Null while the total is not known. */
    progress: Float?,
    onHide: () -> Unit,
    /** Offered only where stopping loses nothing that matters. */
    onCancel: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onHide, properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(IcodeSpacing.Space4), contentAlignment = Alignment.Center) {
            IcodeDialogCard(Modifier.widthIn(max = IcodeSizes.DialogMaxWidth).fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = IcodeType.Title, modifier = Modifier.weight(1f))
                        onCancel?.let { DialogAction("CANCEL", it) }
                        DialogAction("HIDE", onHide)
                    }
                    Text(
                        detail,
                        style = IcodeType.Body.copy(color = IcodeColors.TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (progress == null) IndeterminateProgressTrack(Modifier.fillMaxWidth())
                    else ProgressTrack(progress, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The same work, stopped short: why, and OK. It is said once and closed by the user. */
@Composable
fun IcodeProgressFailedDialog(title: String, reason: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(IcodeSpacing.Space4), contentAlignment = Alignment.Center) {
            IcodeDialogCard(Modifier.widthIn(max = IcodeSizes.DialogMaxWidth).fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = IcodeType.Title, modifier = Modifier.weight(1f))
                        DialogAction("OK", onClose)
                    }
                    Text(reason, style = IcodeType.Body.copy(color = IcodeColors.Error))
                }
            }
        }
    }
}

/** A dialog's text action, at least a touch target tall. */
@Composable
private fun DialogAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = IcodeSizes.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = IcodeSpacing.Space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = IcodeType.Label.copy(color = IcodeColors.Accent))
    }
}
