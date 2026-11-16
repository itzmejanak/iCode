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
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.DetailRow
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
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticeAction
import dev.icode.mobile.domain.notices.NoticePlace
import dev.icode.mobile.domain.notices.NoticeRules
import dev.icode.mobile.domain.notices.NoticeTone

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

/** A tone as the sheet names it. */
internal val NoticeTone.word: String
    get() = when (this) {
        NoticeTone.Problem -> "Problem"
        NoticeTone.HeadsUp -> "Heads-up"
        NoticeTone.Info -> "Info"
        NoticeTone.Done -> "Done"
    }

/**
 * One notice on the Notifications page (docs/engineering-rules.md): its icon, title — with
 * how many times, folded — where, and when; a dot while it is [new] and needs the user. A tap opens
 * its sheet.
 */
@Composable
internal fun NoticeRow(notice: Notice, new: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    IcodeCard(modifier, onClick = onOpen) {
        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
            Icon(notice.tone.icon, contentDescription = null, tint = notice.tone.tint, modifier = Modifier.size(IcodeSizes.IconMd))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    if (new && NoticeRules.counted(notice.copy(read = false))) StatusDot(IcodeColors.Accent)
                    Text(titleOf(notice), style = IcodeType.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val where = notice.place?.line?.takeIf { it.isNotEmpty() }
                Text(where ?: notice.source.label, style = IcodeType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(noticeTime(LocalContext.current, notice.at), style = IcodeType.Micro.copy(color = IcodeColors.TextMuted))
        }
    }
}

/**
 * A notice in full, over the screen: what happened; when, its kind and where it is from; where
 * it happened, in full; why; the tool's own words, folded; what it offers now ([onAct]); Copy, Delete,
 * and [onAll] to the page unless it is open.
 */
@Composable
internal fun NoticeSheet(notice: Notice, modifier: Modifier, onAct: (NoticeAction) -> Unit, onDelete: () -> Unit, onAll: (() -> Unit)?, onClose: () -> Unit) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    val actions = NoticeRules.available(notice, LocalICodeSnapshot.current)
    var outputShown by rememberSaveable(notice.id) { mutableStateOf(false) }
    IcodeBottomSheet(
        title = titleOf(notice),
        onClose = onClose,
        modifier = modifier,
        // What the notice says is its title: all of it, never cut.
        titleLines = Int.MAX_VALUE,
        footer = {
            actions.firstOrNull()?.let { PrimaryButton(it.label, { onAct(it) }, Modifier.fillMaxWidth()) }
            actions.getOrNull(1)?.let { SecondaryButton(it.label, { onAct(it) }, Modifier.fillMaxWidth()) }
            TwoColumnActions(
                left = {
                    val text = listOfNotNull(notice.title, notice.place?.line, notice.detail, notice.output).joinToString("\n\n")
                    SecondaryButton("Copy", { copyText(context, "Notification", text, "Copied", gateway::dispatch) }, Modifier.fillMaxWidth())
                },
                right = { SecondaryButton("Delete", onDelete, Modifier.fillMaxWidth()) },
            )
            onAll?.let { IcodeTextButton("All Notifications", it, Modifier.align(Alignment.CenterHorizontally)) }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            Icon(notice.tone.icon, contentDescription = null, tint = notice.tone.tint, modifier = Modifier.size(IcodeSizes.IconMd))
            Text(listOf(noticeTime(context, notice.at), notice.tone.word, notice.source.label).joinToString("  ·  "), style = IcodeType.Caption)
        }
        notice.place?.let { PlaceRows(it) }
        notice.detail?.let { SelectionContainer { Text(it, style = IcodeType.Body) } }
        notice.output?.let { output ->
            IcodeTextButton(if (outputShown) "Hide Output" else "Show Output", { outputShown = !outputShown })
            if (outputShown) {
                SelectionContainer {
                    CodeSurface(Modifier.fillMaxWidth().clip(IcodeShapes.Small)) { Text(output, style = IcodeType.MonoSmall) }
                }
            }
        }
    }
}

/** Where it happened, a row for each part it has. */
@Composable
private fun PlaceRows(place: NoticePlace) {
    Column {
        place.workspace?.let { DetailRow(if (place.workspaceLocation != null) "Workspace" else "Place", it) }
        place.repository?.let { DetailRow("Repository", it) }
        place.branch?.let { DetailRow("Branch", it) }
        place.remote?.let { DetailRow("Goes to", it) }
        place.subject?.let { DetailRow("About", it) }
    }
}

/** "Saved notes.md · 3 times": a title, with how many times it was told. */
internal fun titleOf(notice: Notice): String = if (notice.repeats > 1) "${notice.title} · ${notice.repeats} times" else notice.title

/** "10:42" today, with the date before today. */
internal fun noticeTime(context: Context, at: Long): String =
    DateUtils.formatDateTime(context, at, DateUtils.FORMAT_SHOW_TIME or if (DateUtils.isToday(at)) 0 else DateUtils.FORMAT_SHOW_DATE)
