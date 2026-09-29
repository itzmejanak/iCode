package dev.icode.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IndeterminateProgressTrack
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.designsystem.IcodeProgressDialog
import dev.icode.mobile.designsystem.IcodeProgressFailedDialog
import dev.icode.mobile.domain.WorkTask
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent

/**
 * The long operation in front, over whatever screen is open
 * (docs/long-operations-plan.md): one at a time, the oldest first. A task sent
 * to the background has no dialog; one that failed comes back to say why.
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
            onHide = { gateway.dispatch(WorkbenchIntent.HideTask(task.id)) },
            onCancel = if (task.cancellable) ({ gateway.dispatch(WorkbenchIntent.CancelTask(task.id)) }) else null,
        )
    }
}

/**
 * Tasks sent to the background, each a thin row with its bar; a tap brings its
 * dialog back. Shown where file work starts, the Explorer; the notification
 * carries them everywhere else.
 */
@Composable
internal fun BackgroundTasks(tasks: List<WorkTask>, gateway: WorkbenchGateway, modifier: Modifier = Modifier) {
    val hidden = tasks.filter { it.hidden && it.failure == null }
    if (hidden.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
        hidden.forEach { task ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Show progress") { gateway.dispatch(WorkbenchIntent.ShowTask(task.id)) }
                    .padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space1),
                verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${task.title.removeSuffix("…")} ${task.subject}",
                        style = IcodeType.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    task.fraction?.let { Text("${(it * 100).toInt()}%", style = IcodeType.Caption) }
                }
                task.fraction?.let { ProgressTrack(it, Modifier.fillMaxWidth()) } ?: IndeterminateProgressTrack(Modifier.fillMaxWidth())
            }
        }
    }
}
