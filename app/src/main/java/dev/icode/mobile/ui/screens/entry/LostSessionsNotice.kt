package dev.icode.mobile.ui.screens.entry

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.LostTerminalSession
import dev.icode.mobile.domain.ProcessEndCause
import dev.icode.mobile.domain.countOf

/**
 * One "terminal sessions ended" notice on the Notifications page: which
 * sessions ended with iCode's previous process, why and when — instead of an
 * empty terminal that pretends nothing happened.
 */
@Composable
fun LostSessionsNotice(
    notice: AppNotice.TerminalSessionsEnded,
    new: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lost = notice.lost
    val flags = DateUtils.FORMAT_SHOW_TIME or if (DateUtils.isToday(notice.at)) 0 else DateUtils.FORMAT_SHOW_DATE
    IcodeCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
            Icon(Icons.Outlined.Terminal, contentDescription = null, tint = IcodeColors.Amber, modifier = Modifier.size(IcodeSizes.IconMd))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    if (new) StatusDot(IcodeColors.Accent)
                    Text(
                        if (lost.sessions.size == 1) "A terminal session ended" else "${countOf(lost.sessions.size, "terminal session")} ended",
                        style = IcodeType.Body,
                    )
                }
                Text(lostSessionsCause(lost.cause) + ".", style = IcodeType.Caption)
                Text(lostSessionsList(lost.sessions), style = IcodeType.MonoSmall)
                Text(DateUtils.formatDateTime(context, notice.at, flags), style = IcodeType.Micro)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(IcodeSizes.TouchTarget)) {
                Icon(Icons.Outlined.Close, contentDescription = "Delete notification", tint = IcodeColors.TextSecondary)
            }
        }
    }
}

/** What ended iCode's previous process, in the user's terms. */
internal fun lostSessionsCause(cause: ProcessEndCause): String = when (cause) {
    ProcessEndCause.StoppedByUser -> "iCode was stopped from Android (Force stop, Task Manager or Recents)"
    ProcessEndCause.StoppedByUserOrUpdated -> "iCode was stopped from Android or updated"
    ProcessEndCause.Updated -> "iCode was updated"
    ProcessEndCause.PhoneRestarted -> "The phone restarted"
    ProcessEndCause.LowMemory -> "Android closed iCode to free memory"
    ProcessEndCause.Crashed -> "iCode crashed"
    ProcessEndCause.NotResponding -> "iCode stopped responding and Android closed it"
    ProcessEndCause.PermissionChanged -> "Android closed iCode after one of its permissions changed"
    ProcessEndCause.Killed -> "iCode's process was killed"
    ProcessEndCause.Other -> "iCode's process ended"
    ProcessEndCause.Unrecorded -> "iCode's process ended; Android kept no record of why"
}

/** "bash · superpowers, bash 2 · superpowers" — tab names as they were, with their workspaces. */
internal fun lostSessionsList(sessions: List<LostTerminalSession>): String =
    sessions.joinToString(", ") { session -> listOfNotNull(session.title, session.workspace).joinToString(" · ") }
