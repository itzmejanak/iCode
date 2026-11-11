package dev.icode.mobile.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.icode.mobile.data.storage.WorkspacesProvider
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.files.FileWords
import java.io.File
import dev.icode.mobile.domain.notices.FileNotices

/**
 * iCode's files as other apps receive them (docs/open-with-plan.md): through its one FileProvider,
 * a URI for one file, granted for one hand-off — Android's Open with, its share sheet, or the camera
 * writing the assistant's photo.
 */
internal object SharedFiles {
    /** The URI another app reaches [file] by; null for a file the provider does not serve. */
    fun uriOf(context: Context, file: File): Uri? =
        runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", file) }.getOrNull()

    /**
     * Android's Open with: the apps that show [location]'s kind of file; one it cannot hand out is
     * said through [dispatch]. One handed out is in Recent (docs/explorer-gaps-plan.md §3.14).
     */
    fun open(context: Context, location: String, dispatch: (ICodeIntent) -> Unit) {
        val file = File(location)
        val uri = handable(context, file, dispatch) ?: return
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, typeOf(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Open ${file.name}"))
        dispatch(FilesIntent.Opened(location))
    }

    /**
     * Android's share sheet, [locations] attached: many files in one share (§3.3). One it cannot hand
     * out is said through [dispatch], and nothing is shared.
     */
    fun share(context: Context, locations: List<String>, dispatch: (ICodeIntent) -> Unit) {
        val files = locations.map(::File)
        val uris = files.map { handable(context, it, dispatch) ?: return }
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        // One kind for all, or any; the clip carries the grant through the chooser to the app chosen.
        intent.setType(files.map(::typeOf).distinct().singleOrNull() ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        context.startActivity(Intent.createChooser(intent, "Share ${FileWords.subject(files.map { it.name })}"))
    }

    /** [file]'s URI to hand out; null, and said, when the provider does not serve it. */
    private fun handable(context: Context, file: File, dispatch: (ICodeIntent) -> Unit): Uri? =
        uriOf(context, file) ?: null.also { dispatch(NoticeIntent.Tell(FileNotices.cannotHandOver(file.name))) }

    /** A kind Android does not name goes to every app that takes any file. */
    private fun typeOf(file: File): String = WorkspacesProvider.mimeTypeOf(file) ?: "*/*"
}
