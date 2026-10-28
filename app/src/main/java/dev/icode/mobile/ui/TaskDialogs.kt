package dev.icode.mobile.ui

import androidx.compose.runtime.Composable
import dev.icode.mobile.designsystem.IcodeProgressDialog
import dev.icode.mobile.designsystem.IcodeProgressFailedDialog
import dev.icode.mobile.domain.WorkTask
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.percentOf

/**
 * The long operation in front, over whatever screen is open
 * (docs/long-operations-plan.md): one at a time, the oldest first. A task sent
 * to the background has no dialog and no row inside iCode: its notification
 * carries it (§6), and a tap on it brings the dialog back, as does running into
 * the task in iCode. One that failed comes back to say why; one that ends at
 * once never shows ([WorkTask.quiet]). While an explorer's question is [asking],
 * its sheet is in front instead ([FileAsks]).
 */
@Composable
internal fun TaskDialogs(tasks: List<WorkTask>, gateway: ICodeGateway, asking: Boolean) {
    if (asking) return
    val task = tasks.firstOrNull { !it.hidden && !it.quiet } ?: return
    val failure = task.failure
    if (failure != null) {
        IcodeProgressFailedDialog(
            title = "${task.title.removeSuffix("…")} stopped",
            reason = "${task.subject}: $failure",
            onClose = { gateway.dispatch(ICodeIntent.DismissTask(task.id)) },
        )
    } else {
        IcodeProgressDialog(
            title = task.title,
            detail = task.currentItem ?: "…",
            progress = task.fraction,
            counts = task.counts,
            share = task.fraction?.let(::percentOf),
            onHide = { gateway.dispatch(ICodeIntent.HideTask(task.id)) },
            onCancel = if (task.cancellable) ({ gateway.dispatch(ICodeIntent.CancelTask(task.id)) }) else null,
        )
    }
}
