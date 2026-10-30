package dev.icode.mobile.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.GitTransferLabel
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.LostTerminalSession
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.ProcessEndCause
import dev.icode.mobile.domain.countOf

/**
 * What a notice shows wherever it is drawn — the bell, a row of the Notifications page, its sheet
 * (docs/notices-plan.md). [words] marks a detail that is a failure's own words, set in the mono face.
 */
internal data class NoticeView(val icon: ImageVector, val tint: Color, val title: String, val detail: String?, val words: Boolean)

@Composable
internal fun noticeView(notice: AppNotice): NoticeView = when (notice) {
    is AppNotice.Message -> NoticeView(notice.tone.icon, notice.tone.tint, notice.title, notice.detail, words = notice.tone == NoticeTone.Problem)
    is AppNotice.TerminalSessionsEnded -> NoticeView(
        Icons.Outlined.Terminal,
        IcodeColors.Amber,
        if (notice.lost.sessions.size == 1) "A terminal session ended" else "${countOf(notice.lost.sessions.size, "terminal session")} ended",
        "${lostSessionsCause(notice.lost.cause)}.\n${lostSessionsList(notice.lost.sessions)}",
        words = false,
    )
    is AppNotice.GitTransferInterrupted -> NoticeView(
        Icons.Outlined.SyncProblem,
        IcodeColors.Amber,
        "${transferName(notice.label)} did not finish",
        if (notice.label == GitTransferLabel.CLONING) {
            "iCode's process ended while Git was cloning. Nothing of it was kept: clone again.\n${notice.target}"
        } else {
            "iCode's process ended while Git was working. Run it again; Git carries on from what it has.\n${notice.target}"
        },
        words = false,
    )
}

/** A tone's icon. */
internal val NoticeTone.icon: ImageVector
    get() = when (this) {
        NoticeTone.Problem -> Icons.Outlined.ErrorOutline
        NoticeTone.HeadsUp -> Icons.Outlined.WarningAmber
        NoticeTone.Info -> Icons.Outlined.Info
        NoticeTone.Done -> Icons.Outlined.CheckCircle
    }

/** A tone's colour. */
internal val NoticeTone.tint: Color
    @Composable get() = when (this) {
        NoticeTone.Problem -> IcodeColors.Error
        NoticeTone.HeadsUp -> IcodeColors.Amber
        NoticeTone.Info -> IcodeColors.Accent
        NoticeTone.Done -> IcodeColors.Green
    }

/** One notice on the Notifications page: its icon, title, first line, time; a tap opens its sheet. */
@Composable
internal fun NoticeRow(notice: AppNotice, new: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val view = noticeView(notice)
    IcodeCard(modifier, onClick = onOpen) {
        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
            Icon(view.icon, contentDescription = null, tint = view.tint, modifier = Modifier.size(IcodeSizes.IconMd))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    if (new) StatusDot(IcodeColors.Accent)
                    Text(view.title, style = IcodeType.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                view.detail?.let { Text(it.lineSequence().first(), style = IcodeType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Text(noticeTime(LocalContext.current, notice.at), style = IcodeType.Micro.copy(color = IcodeColors.TextMuted))
        }
    }
}

/** A notice in full, over the screen: when, its detail, Copy, Delete, and [onAll] to the page unless it is open. */
@Composable
internal fun NoticeSheet(notice: AppNotice, modifier: Modifier, onDelete: () -> Unit, onAll: (() -> Unit)?, onClose: () -> Unit) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    val view = noticeView(notice)
    IcodeBottomSheet(
        title = view.title,
        onClose = onClose,
        modifier = modifier,
        footer = {
            (notice as? AppNotice.Message)?.link?.let { link ->
                PrimaryButton(link.label, { gateway.dispatch(ICodeIntent.OpenExternalUrl(link.url)) }, Modifier.fillMaxWidth())
            }
            TwoColumnActions(
                left = {
                    val text = listOfNotNull(view.title, view.detail).joinToString("\n\n")
                    SecondaryButton("Copy", { copyText(context, "Notification", text, "Copied", gateway::dispatch) }, Modifier.fillMaxWidth())
                },
                right = { SecondaryButton("Delete", onDelete, Modifier.fillMaxWidth()) },
            )
            onAll?.let { IcodeTextButton("All Notifications", it, Modifier.align(Alignment.CenterHorizontally)) }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            Icon(view.icon, contentDescription = null, tint = view.tint, modifier = Modifier.size(IcodeSizes.IconMd))
            Text(noticeTime(context, notice.at), style = IcodeType.Caption)
            if ((notice as? AppNotice.Message)?.fromWebsite == true) {
                Text("·  iCode website", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
            }
        }
        view.detail?.let { detail ->
            SelectionContainer {
                if (view.words) {
                    CodeSurface(Modifier.fillMaxWidth().clip(IcodeShapes.Small)) { Text(detail, style = IcodeType.MonoSmall) }
                } else {
                    Text(detail, style = IcodeType.Body)
                }
            }
        }
    }
}

/** "10:42" today, with the date before today. */
internal fun noticeTime(context: Context, at: Long): String =
    DateUtils.formatDateTime(context, at, DateUtils.FORMAT_SHOW_TIME or if (DateUtils.isToday(at)) 0 else DateUtils.FORMAT_SHOW_DATE)

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

private fun transferName(label: String): String = when (label) {
    GitTransferLabel.CLONING -> "A clone"
    GitTransferLabel.FETCHING -> "A fetch"
    GitTransferLabel.PULLING -> "A pull"
    GitTransferLabel.PUSHING -> "A push"
    else -> label
}
