package dev.icode.mobile.data.web

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.icode.mobile.domain.ReportDraft
import dev.icode.mobile.domain.ReportPolicy
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONObject

/**
 * Sends a Report a Bug draft (contracts/api-v1.md § Reports): each attachment straight to Cloudinary
 * with a signature from the website, then the report naming them. Blocking: call from Dispatchers.IO.
 */
internal class ReportSender(private val context: Context, private val client: ICodeWebClient) {

    fun send(draft: ReportDraft, policy: ReportPolicy, progress: (String) -> Unit): Int {
        val files = if (policy.mediaEnabled) draft.attachments.take(policy.maxFiles) else emptyList()
        val media = files.mapIndexed { i, uri ->
            progress("Uploading ${i + 1} of ${files.size}…")
            upload(Uri.parse(uri), policy)
        }
        progress("Sending report…")
        val device = if (draft.attachDevice) DeviceFacts.read(context) else null
        return client.fileReport(WebJson.reportJson(draft, media, device))
    }

    /** (publicId, resourceType) of the uploaded file. */
    private fun upload(uri: Uri, policy: ReportPolicy): Pair<String, String> {
        val resolver = context.contentResolver
        val type = resolver.getType(uri).orEmpty()
        val video = type.startsWith("video/")
        val (name, size) = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) (c.getString(0) ?: "attachment") to c.getLong(1) else null
        } ?: ("attachment" to -1L)
        val max = if (video) policy.maxVideoBytes else policy.maxImageBytes
        if (size > max) throw ICodeWebClient.WebException("$name is over ${max / (1024 * 1024)} MB")

        val (uploadUrl, fields) = client.signReportUpload()
        val boundary = "icode-" + UUID.randomUUID()
        val connection = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = TIMEOUT_MS
            readTimeout = UPLOAD_TIMEOUT_MS
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            DataOutputStream(connection.outputStream).use { out ->
                fields.forEach { (k, v) ->
                    out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n")
                }
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${name.replace("\"", "")}\"\r\n")
                out.writeBytes("Content-Type: ${type.ifEmpty { "application/octet-stream" }}\r\n\r\n")
                resolver.openInputStream(uri)?.use { it.copyTo(out) } ?: throw ICodeWebClient.WebException("Couldn't read $name")
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            if (connection.responseCode !in 200..299) throw ICodeWebClient.WebException("Couldn't upload $name")
            val reply = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            return reply.getString("public_id") to (if (reply.optString("resource_type") == "video") "video" else "image")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val UPLOAD_TIMEOUT_MS = 120_000
    }
}
