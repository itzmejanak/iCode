package dev.icode.mobile.ui

import androidx.compose.runtime.Composable
import dev.icode.mobile.designsystem.IcodeProgressDialog
import dev.icode.mobile.designsystem.IcodeProgressFailedDialog
import dev.icode.mobile.domain.WorkTask
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.percentOf

/**
 * The long operation in front, over whatever screen is open
 * (docs/long-operations-plan.md): one at a time, the oldest first. A task sent
 * to the background has no dialog and no row inside iCode: its notification
 * carries it (§6), and a tap on it brings the dialog back, as does running into
 * the task in iCode. One that failed comes back to say why.
 */
@Composable
internal fun TaskDialogs(tasks: List<WorkTask>, gateway: WorkbenchGateway) {
    val task = tasks.firstOrNull { !it.hidden } ?: return
    val failure = task.failure
    if (failure != null) {
        IcodeProgressFailedDialog(
            title = "${task.title.removeSuffix("…")} stopped",
            reason = "${task.subject}: $failure",
            onClose = { gateway.dispatch(WorkbenchIntent.DismissTask(task.id)) },
        )
    } else {
        IcodeProgressDialog(
            title = task.title,
            detail = task.currentItem ?: "…",
            progress = task.fraction,
            counts = task.counts,
            share = task.fraction?.let(::percentOf),
            onHide = { gateway.dispatch(WorkbenchIntent.HideTask(task.id)) },
            onCancel = if (task.cancellable) ({ gateway.dispatch(WorkbenchIntent.CancelTask(task.id)) }) else null,
        )
    }
}
