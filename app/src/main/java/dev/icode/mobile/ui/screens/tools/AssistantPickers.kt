package dev.icode.mobile.ui.screens.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.FileViewer
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.ui.LocalFileViewer
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.SharedFiles
import dev.icode.mobile.ui.rememberThumbnail
import dev.icode.mobile.ui.ThumbnailKind
import java.io.File
import dev.icode.mobile.domain.notices.AssistantNotices

/** A file going with the message: a workspace file's location, or a phone file's `content://` URI. */
internal data class DraftFile(val location: String, val name: String, val kind: AssistantAttachmentKind)

/** What Attach opens on the phone (§13 P6): no permission is asked for any of them; [camera] is null on a phone without one. */
internal class AssistantPickers(val photos: () -> Unit, val files: () -> Unit, val camera: (() -> Unit)?)

/**
 * The phone's own pickers — the Photo Picker, the document picker, the camera — handing back the
 * files chosen, described ([describe]). A device with no camera app says so.
 */
@Composable
internal fun rememberAssistantPickers(onPicked: (List<DraftFile>) -> Unit): AssistantPickers {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    var shot by rememberSaveable { mutableStateOf<String?>(null) }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(AssistantRules.MAX_ATTACHMENTS)) { uris ->
        onPicked(uris.mapNotNull { describe(context, it) })
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onPicked(uris.mapNotNull { describe(context, it) })
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = shot?.let(Uri::parse)
        if (taken && uri != null) onPicked(listOfNotNull(describe(context, uri)))
        shot = null
    }
    return remember(context) {
        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        AssistantPickers(
            photos = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            files = { launch(gateway::dispatch) { files.launch(arrayOf("*/*")) } },
            camera = {
                launch(gateway::dispatch) {
                    val uri = cameraFile(context)
                    shot = uri.toString()
                    camera.launch(uri)
                }
            }.takeIf { hasCamera },
        )
    }
}

private fun launch(dispatch: (ICodeIntent) -> Unit, start: () -> Unit) {
    try {
        start()
    } catch (missing: ActivityNotFoundException) {
        dispatch(NoticeIntent.Tell(AssistantNotices.noApp()))
    }
}

/** A new photo's place in iCode's cache, shared with the camera app for this capture alone; older ones are cleared. */
private fun cameraFile(context: Context): Uri {
    val folder = File(context.cacheDir, CAMERA_FOLDER).apply { mkdirs() }
    val dayAgo = System.currentTimeMillis() - DAY_MILLIS
    folder.listFiles()?.filter { it.lastModified() < dayAgo }?.forEach(File::delete)
    val photo = File(folder, "photo-${System.currentTimeMillis()}.jpg")
    return checkNotNull(SharedFiles.uriOf(context, photo))
}

/** A picked file's name and kind, as Android describes it; null when it cannot be read already. */
private fun describe(context: Context, uri: Uri): DraftFile? = runCatching {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment ?: return@runCatching null
    DraftFile(uri.toString(), name, AssistantRules.attachmentKind(name, resolver.getType(uri)))
}.getOrNull()

/**
 * An image file — kept with a message, or picked — drawn small ([rememberThumbnail]), the way the
 * camera held it; its kind's icon until then, or if it cannot be read.
 */
@Composable
internal fun AssistantThumbnail(location: String, kind: AssistantAttachmentKind, modifier: Modifier) {
    val image = rememberThumbnail(location, ThumbnailKind.Picture.takeIf { kind == AssistantAttachmentKind.Image })
    Box(modifier.clip(IcodeShapes.Small).background(IcodeColors.SurfaceElevated), contentAlignment = Alignment.Center) {
        val shown = image
        if (shown != null) {
            Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(
                kind.icon,
                contentDescription = null,
                tint = IcodeColors.TextMuted,
                modifier = Modifier.size(IcodeSizes.IconMd),
            )
        }
    }
}

/**
 * What a tap on a file going with a message opens (docs/file-viewers-plan.md): a picture, at
 * [picture], in the image viewer; a file of the chat's workspace, at [workspaceFile], in the editor;
 * nothing for a phone's other files, kept only as they were sent.
 */
@Composable
internal fun attachmentOpener(name: String, kind: AssistantAttachmentKind, picture: String?, workspaceFile: String?): (() -> Unit)? {
    val viewer = LocalFileViewer.current
    val gateway = LocalICodeGateway.current
    return when {
        kind == AssistantAttachmentKind.Image && picture != null -> { { viewer.show(picture, name, FileViewer.Image) } }
        workspaceFile != null -> { { gateway.dispatch(ICodeIntent.OpenFile(workspaceFile)) } }
        else -> null
    }
}

/** [this], tapped to [open] when there is something to open. */
internal fun Modifier.tappable(open: (() -> Unit)?): Modifier =
    if (open == null) this else clip(IcodeShapes.Small).clickable(onClick = open)

/** A file's kind, as its icon says it. */
internal val AssistantAttachmentKind.icon: ImageVector
    get() = when (this) {
        AssistantAttachmentKind.Image -> Icons.Outlined.Image
        AssistantAttachmentKind.Document -> Icons.Outlined.PictureAsPdf
        AssistantAttachmentKind.Text -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }

private const val CAMERA_FOLDER = "assistant-camera"
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** The size a file going with the message is drawn at in the composer. */
internal val DRAFT_THUMBNAIL = IcodeSizes.BadgeMd
