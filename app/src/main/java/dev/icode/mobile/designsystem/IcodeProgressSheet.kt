package dev.icode.mobile.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

/**
 * A long operation in hand, as a sheet (owner's request, 2026-11-04: no dialogs), drawn as the
 * reference file manager draws one: its title, the item in hand on one line, a bar, and under it the
 * counts and share its notification shows too. While the total is still being counted the bar sweeps —
 * one frozen at zero reads as a hang. Hide, ×, Back, a swipe down or a tap outside send the work to
 * the background, where it goes on and its notification carries it.
 */
@Composable
fun IcodeProgressSheet(
    title: String,
    /** The item in hand; "…" while there is none yet. */
    detail: String,
    /** Null while the total is not known. */
    progress: Float?,
    /** "1207 / 11813" once counted; null while counting. */
    counts: String?,
    /** "10%" once counted. */
    share: String?,
    onHide: () -> Unit,
    /** Offered only where stopping loses nothing that matters. */
    onCancel: (() -> Unit)? = null,
) {
    IcodeModalSheet(onClose = onHide) {
        IcodeBottomSheet(
            title = title,
            onClose = onHide,
            modifier = Modifier.align(Alignment.BottomCenter),
            footer = {
                if (onCancel != null) {
                    TwoColumnActions(
                        left = { SecondaryButton("Cancel", onCancel, Modifier.fillMaxWidth()) },
                        right = { PrimaryButton("Hide", onHide, Modifier.fillMaxWidth()) },
                    )
                } else {
                    PrimaryButton("Hide", onHide, Modifier.fillMaxWidth())
                }
            },
        ) {
            Text(
                detail,
                style = IcodeType.Body.copy(color = IcodeColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (progress == null) IndeterminateProgressTrack(Modifier.fillMaxWidth())
            else ProgressTrack(progress, Modifier.fillMaxWidth())
            if (counts != null || share != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(counts.orEmpty(), style = IcodeType.Caption)
                    Text(share.orEmpty(), style = IcodeType.Caption)
                }
            }
        }
    }
}

/** The same work, stopped short: why, and OK. It is said once and closed by the user. */
@Composable
fun IcodeProgressFailedSheet(title: String, reason: String, onClose: () -> Unit) {
    IcodeModalSheet(onClose = onClose) {
        IcodeBottomSheet(
            title = title,
            onClose = onClose,
            modifier = Modifier.align(Alignment.BottomCenter),
            footer = { PrimaryButton("OK", onClose, Modifier.fillMaxWidth()) },
        ) {
            Text(reason, style = IcodeType.Body.copy(color = IcodeColors.Error))
        }
    }
}
