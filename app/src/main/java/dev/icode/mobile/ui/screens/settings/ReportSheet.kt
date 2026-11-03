package dev.icode.mobile.ui.screens.settings

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.KeyChip
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.ReportDraft
import dev.icode.mobile.domain.ReportKind
import dev.icode.mobile.domain.ReportSending
import dev.icode.mobile.domain.ReportState
import dev.icode.mobile.domain.WebIntent
import dev.icode.mobile.ui.LocalICodeGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The website's limits (contracts/api-v1.md § Reports), mirrored for the form's own checks. */
private const val MIN_TITLE = 3
private const val MIN_DESCRIPTION = 10

/**
 * Report a Bug, from Settings › About iCode: the same report the website's /report page sends, in an
 * [IcodeBottomSheet] like every other sheet. The draft lives in [ReportState] and on the phone, so a
 * closed sheet or a failed send keeps what was typed. Device info is attached only when switched on.
 */
@Composable
internal fun ReportSheet(state: ReportState, modifier: Modifier, onClose: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { gateway.dispatch(WebIntent.OpenReport) }
    val draft = state.draft
    fun edit(change: ReportDraft.() -> ReportDraft) = gateway.dispatch(WebIntent.EditReport(draft.change()))
    val policy = state.policy
    val sending = state.sending
    val busy = sending is ReportSending.Sending
    val maxFiles = policy?.maxFiles ?: 3
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxFiles)) { uris ->
        // Kept across restarts with the draft, so the grant has to outlive this activity.
        uris.forEach { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        edit { copy(attachments = (attachments + uris.map(Uri::toString)).distinct().take(maxFiles)) }
    }

    if (sending is ReportSending.Sent) {
        IcodeBottomSheet(
            title = "Report Sent",
            subtitle = if (sending.number > 0) "Report #${sending.number}" else null,
            onClose = { gateway.dispatch(WebIntent.NewReport); onClose() },
            modifier = modifier,
            leading = { Icon(Icons.Outlined.BugReport, contentDescription = null, tint = IcodeColors.Accent) },
            footer = {
                TwoColumnActions(
                    left = { SecondaryButton("Send Another", { gateway.dispatch(WebIntent.NewReport) }, Modifier.fillMaxWidth()) },
                    right = { PrimaryButton("Done", { gateway.dispatch(WebIntent.NewReport); onClose() }, Modifier.fillMaxWidth()) },
                )
            },
        ) {
            Text("Thanks — every report is read. If you left an email, we may follow up.", style = IcodeType.Body)
        }
        return
    }

    val problem = when {
        policy != null && !policy.enabled -> "Reports are closed right now"
        draft.title.trim().length < MIN_TITLE -> "Add a short title"
        draft.description.trim().length < MIN_DESCRIPTION -> "Say what happened"
        else -> null
    }
    IcodeBottomSheet(
        title = "Report a Bug",
        subtitle = "Sent to the iCode team",
        onClose = onClose,
        modifier = modifier,
        leading = { Icon(Icons.Outlined.BugReport, contentDescription = null, tint = IcodeColors.Accent) },
        footer = {
            when (sending) {
                is ReportSending.Sending -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    CircularProgressIndicator(Modifier.size(IcodeSizes.IconSm), color = IcodeColors.Accent, strokeWidth = IcodeSpacing.Micro)
                    Text(sending.step, style = IcodeType.Caption)
                }
                is ReportSending.Failed -> Text(sending.reason, style = IcodeType.Caption.copy(color = IcodeColors.Error))
                else -> problem?.let { Text(it, style = IcodeType.Caption) }
            }
            PrimaryButton(
                text = if (sending is ReportSending.Failed) "Try Again" else "Send Report",
                onClick = { gateway.dispatch(WebIntent.SendReport) },
                modifier = Modifier.fillMaxWidth(),
                enabled = problem == null && !busy,
            )
        },
    ) {
        SectionLabel("Type")
        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            ReportKind.entries.forEach { kind ->
                KeyChip(kind.label, active = draft.kind == kind, onClick = { edit { copy(kind = kind) } }.takeUnless { busy })
            }
        }
        LabeledTextField("Title", draft.title, { edit { copy(title = it.take(120)) } }, placeholder = "Terminal closes when I rotate")
        LabeledTextField(
            "What Happened",
            draft.description,
            { edit { copy(description = it.take(5_000)) } },
            placeholder = "Steps, what you expected, what happened",
            singleLine = false,
        )
        LabeledTextField("Email (Optional)", draft.email, { edit { copy(email = it.take(200)) } }, placeholder = "you@example.com", literal = true)

        if (policy?.mediaEnabled != false) {
            SectionLabel("Screenshots · up to $maxFiles", Modifier.padding(top = IcodeSpacing.SectionBreak))
            Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                draft.attachments.forEach { uri ->
                    Attachment(uri, onRemove = { edit { copy(attachments = attachments - uri) } }.takeUnless { busy })
                }
                if (draft.attachments.size < maxFiles && !busy) {
                    Box(
                        Modifier.size(IcodeSizes.Tile).clip(IcodeShapes.Small).background(IcodeColors.SurfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Add screenshot", tint = IcodeColors.TextSecondary)
                        }
                    }
                }
            }
        }

        IcodeListRow(
            card = true,
            modifier = Modifier.padding(top = IcodeSpacing.SectionBreak),
            title = "Attach Device Info",
            subtitle = "App and Android version, phone model, language. Never your files.",
            subtitleStyle = IcodeType.Caption,
            onClick = { edit { copy(attachDevice = !attachDevice) } }.takeUnless { busy },
            trailing = { IcodeToggle(checked = draft.attachDevice) { on -> edit { copy(attachDevice = on) } } },
        )
    }
}

/** A picked screenshot's thumbnail (a film icon for a recording), with × to drop it. */
@Composable
private fun Attachment(uri: String, onRemove: (() -> Unit)?) {
    val context = LocalContext.current
    val parsed = remember(uri) { Uri.parse(uri) }
    val video = remember(uri) { context.contentResolver.getType(parsed)?.startsWith("video/") == true }
    val thumb by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { context.contentResolver.loadThumbnail(parsed, Size(256, 256), null) }.getOrNull() }
    }
    Box(Modifier.size(IcodeSizes.Tile).clip(IcodeShapes.Small).background(IcodeColors.SurfaceElevated)) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = "Attachment", contentScale = ContentScale.Crop, modifier = Modifier.size(IcodeSizes.Tile))
        } else {
            Icon(
                if (video) Icons.Outlined.Movie else Icons.Outlined.AddPhotoAlternate,
                contentDescription = null,
                tint = IcodeColors.TextSecondary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        onRemove?.let { remove ->
            IconButton(onClick = remove, modifier = Modifier.align(Alignment.TopEnd).size(IcodeSizes.TileRemove)) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove attachment", tint = IcodeColors.TextPrimary, modifier = Modifier.size(IcodeSizes.IconSm))
            }
        }
    }
}
