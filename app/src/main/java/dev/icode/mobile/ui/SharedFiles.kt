package dev.icode.mobile.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.icode.mobile.data.storage.WorkspacesProvider
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.NoticeTone
import java.io.File

/**
 * iCode's files as other apps receive them (docs/open-with-plan.md): through its one FileProvider,
 * a URI for one file, granted for one hand-off — Android's Open with, its share sheet, or the camera
 * writing the assistant's photo.
 */
internal object SharedFiles {
    /** The URI another app reaches [file] by; null for a file the provider does not serve. */
    fun uriOf(context: Context, file: File): Uri? =
        runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", file) }.getOrNull()

    /** Android's Open with: the apps that show [location]'s kind of file; one it cannot hand out is said through [dispatch]. */
    fun open(context: Context, location: String, dispatch: (ICodeIntent) -> Unit) = handOut(context, location, "Open", dispatch) { uri, type ->
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
    }

    /** Android's share sheet, [location] attached; one it cannot hand out is said through [dispatch]. */
    fun share(context: Context, location: String, dispatch: (ICodeIntent) -> Unit) = handOut(context, location, "Share", dispatch) { uri, type ->
        // The clip carries the grant through the chooser to the app chosen.
        Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri).apply { clipData = ClipData.newRawUri(null, uri) }
    }

    private fun handOut(context: Context, location: String, verb: String, dispatch: (ICodeIntent) -> Unit, intentOf: (Uri, String) -> Intent) {
        val file = File(location)
        val uri = uriOf(context, file)
            ?: return dispatch(NoticeIntent.Notify(NoticeTone.HeadsUp, "${file.name} cannot be handed to another app"))
        // A kind Android does not name goes to every app that takes any file.
        val intent = intentOf(uri, WorkspacesProvider.mimeTypeOf(file.name) ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "$verb ${file.name}"))
    }
}
