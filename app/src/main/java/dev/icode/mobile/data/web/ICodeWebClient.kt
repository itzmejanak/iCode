package dev.icode.mobile.data.web

import dev.icode.mobile.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import dev.icode.mobile.domain.ReportPolicy
import org.json.JSONObject

/**
 * The website's HTTP API (contracts/api-v1.md). Blocking: call from Dispatchers.IO or a worker.
 * Every failure is an exception the caller turns into "not available" — never a crash.
 */
internal class ICodeWebClient(private val baseUrl: String = BuildConfig.ICODE_WEB_URL) {
    /** False in builds without a website address: every web feature then stays off. */
    val configured: Boolean get() = baseUrl.startsWith("https://") || baseUrl.startsWith("http://")

    /** This build updates from its own channel (docs/build-and-release.md); development and staging from none. */
    val updates: Boolean get() = configured && BuildConfig.UPDATE_CHANNEL.isNotEmpty()

    fun appConfig(): WebJson.Config = WebJson.config(getJson("/api/v1/app/config"))

    /** The newest release on this build's channel: another channel's is another package, which this one cannot update to. */
    fun latestRelease(): WebJson.Latest =
        WebJson.latest(getJson("/api/v1/app/releases/latest?channel=${BuildConfig.UPDATE_CHANNEL}&abi=arm64-v8a&versionCode=${BuildConfig.VERSION_CODE}"))

    fun notices(): List<dev.icode.mobile.domain.notices.Notice> =
        WebJson.notices(getJson("/api/v1/app/notices?placement=app&versionCode=${BuildConfig.VERSION_CODE}"))

    /** What the website says of the runtime manifest; [etag] is the one last kept, for a 304. */
    sealed interface ManifestFetch {
        data object NotModified : ManifestFetch
        /** Nothing published for this architecture: the built-in manifest applies. */
        data object NotPublished : ManifestFetch
        data class Fetched(val json: String, val etag: String?) : ManifestFetch
    }

    fun runtimeManifest(abi: String, etag: String?): ManifestFetch {
        if (!configured) throw WebException("No website configured")
        val connection = open("/api/v1/runtime/manifest?abi=$abi", "GET")
        try {
            etag?.let { connection.setRequestProperty("If-None-Match", it) }
            return when (val status = connection.responseCode) {
                200 -> ManifestFetch.Fetched(connection.inputStream.bufferedReader().use { it.readText() }, connection.getHeaderField("ETag"))
                304 -> ManifestFetch.NotModified
                404 -> ManifestFetch.NotPublished
                else -> throw WebException("HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun register(installId: String, facts: DeviceFacts) {
        send("POST", "/api/v1/devices/register", WebJson.deviceJson(installId, facts)).expect(204)
    }

    /** False when the server no longer knows the install: register again. */
    fun heartbeat(installId: String, facts: DeviceFacts): Boolean {
        val body = JSONObject()
            .put("installId", installId)
            .put("appVersionCode", facts.appVersionCode)
            .put("runtimeInstalled", facts.runtimeInstalled)
        val status = send("POST", "/api/v1/devices/heartbeat", body)
        if (status == 404) return false
        status.expect(204)
        return true
    }

    fun forget(installId: String) {
        send("DELETE", "/api/v1/devices/$installId", null).expect(204)
    }

    fun reportPolicy(): ReportPolicy = WebJson.reportPolicy(getJson("/api/v1/reports/config"))

    /** A signed Cloudinary upload for one attachment: (upload URL, form fields). */
    fun signReportUpload(): Pair<String, Map<String, String>> {
        val json = postForJson("/api/v1/reports/uploads", null, 200)
        val fields = json.getJSONObject("fields")
        return json.getString("uploadUrl") to fields.keys().asSequence().associateWith { fields.getString(it) }
    }

    /** Files the report; the website's report number. */
    fun fileReport(body: JSONObject): Int = postForJson("/api/v1/reports", body, 201).optInt("number")

    private fun postForJson(path: String, body: JSONObject?, ok: Int): JSONObject {
        if (!configured) throw WebException("No website configured")
        val connection = open(path, "POST")
        try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray()) }
            val status = connection.responseCode
            if (status != ok) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?.let { runCatching { JSONObject(it).getJSONObject("error").getString("message") }.getOrNull() }
                throw WebException(if (status == 429) "Too many reports — try again in a few minutes" else message ?: "HTTP $status")
            }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun Int.expect(code: Int) {
        if (this != code) throw WebException("HTTP $this")
    }

    private fun open(path: String, method: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "iCode/${BuildConfig.VERSION_NAME} (Android)")
        }

    private fun getJson(path: String): JSONObject {
        if (!configured) throw WebException("No website configured")
        val connection = open(path, "GET")
        try {
            if (connection.responseCode != 200) throw WebException("HTTP ${connection.responseCode}")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun send(method: String, path: String, body: JSONObject?): Int {
        if (!configured) throw WebException("No website configured")
        val connection = open(path, method)
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    class WebException(message: String) : Exception(message)

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
