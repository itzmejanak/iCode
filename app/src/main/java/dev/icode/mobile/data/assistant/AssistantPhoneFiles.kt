package dev.icode.mobile.data.assistant

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/**
 * The phone's files as a picker or the camera handed them over: read through Android's
 * [ContentResolver], with the grant the picker gave this process. No storage permission is used.
 */
internal class ContentResolverPhoneFiles(private val resolver: ContentResolver) : PhoneFiles {
    override fun open(uri: String): PhoneFile? {
        val parsed = Uri.parse(uri)
        val name = runCatching {
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: parsed.lastPathSegment ?: return null
        return PhoneFile(name, resolver.getType(parsed)) {
            resolver.openInputStream(parsed) ?: throw java.io.IOException("Nothing to read")
        }
    }
}
