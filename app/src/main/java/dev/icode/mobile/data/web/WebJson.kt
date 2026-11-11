package dev.icode.mobile.data.web

import dev.icode.mobile.domain.AppLinks
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.WebNotices
import dev.icode.mobile.domain.ReportDraft
import dev.icode.mobile.domain.ReportKind
import dev.icode.mobile.domain.ReportPolicy
import org.json.JSONArray
import org.json.JSONObject

/** The website's JSON (contracts/api-v1.md) read into iCode's types: the one place field names live. */
internal object WebJson {
    data class Config(val links: AppLinks, val supportText: String, val telemetryEnabled: Boolean, val heartbeatMinutes: Long, val checkIntervalHours: Long)

    data class Release(val versionName: String, val versionCode: Int, val downloadUrl: String, val sha256: String)

    data class Latest(val release: Release?, val updateAvailable: Boolean, val updateRequired: Boolean)

    private fun JSONObject.optUrl(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.startsWith("https://") }

    fun links(json: JSONObject) = AppLinks(
        website = json.optUrl("website"),
        documentation = json.optUrl("documentation"),
        releaseNotes = json.optUrl("releaseNotes"),
        reportIssue = json.optUrl("reportIssue"),
        source = json.optUrl("source"),
        licenses = json.optUrl("licenses"),
        privacy = json.optUrl("privacy"),
    )

    fun linksJson(links: AppLinks): JSONObject = JSONObject()
        .put("website", links.website).put("documentation", links.documentation)
        .put("releaseNotes", links.releaseNotes).put("reportIssue", links.reportIssue)
        .put("source", links.source).put("licenses", links.licenses).put("privacy", links.privacy)

    fun config(json: JSONObject): Config {
        val telemetry = json.optJSONObject("telemetry")
        return Config(
            links = links(json.optJSONObject("links") ?: JSONObject()),
            supportText = json.optString("supportText"),
            telemetryEnabled = telemetry?.optBoolean("enabled", true) ?: true,
            heartbeatMinutes = (telemetry?.optLong("heartbeatMinutes", WebPreferences.DEFAULT_HEARTBEAT_MINUTES) ?: WebPreferences.DEFAULT_HEARTBEAT_MINUTES)
                .coerceAtLeast(WebPreferences.DEFAULT_HEARTBEAT_MINUTES),
            checkIntervalHours = (json.optJSONObject("update")?.optLong("checkIntervalHours", WebPreferences.DEFAULT_CHECK_INTERVAL_HOURS)
                ?: WebPreferences.DEFAULT_CHECK_INTERVAL_HOURS).coerceAtLeast(1),
        )
    }

    fun latest(json: JSONObject): Latest {
        val release = json.optJSONObject("release")?.let {
            Release(it.getString("versionName"), it.getInt("versionCode"), it.getString("downloadUrl"), it.getString("sha256"))
        }
        return Latest(release, json.optBoolean("updateAvailable"), json.optBoolean("updateRequired"))
    }

    /**
     * The website's notices as the app's own (contracts/api-v1.md): one tone per level, kept under
     * `web:<id>` so the Notifications page, the bell and the shade treat them as any other.
     * [now] stands in for a missing or unreadable time.
     */
    fun notices(json: JSONObject, now: Long = System.currentTimeMillis()): List<Notice> {
        val array = json.optJSONArray("notices") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WebNotices.fromWebsite(
                id = id,
                at = item.optString("at").let { at -> runCatching { java.time.Instant.parse(at).toEpochMilli() }.getOrDefault(now) },
                level = item.optString("level"),
                title = title,
                body = item.optString("body").takeIf { it.isNotBlank() },
                link = item.optUrl("linkUrl"),
                linkLabel = item.optString("linkLabel").takeIf { l -> l.isNotBlank() && !item.isNull("linkLabel") },
            )
        }
    }

    fun deviceJson(installId: String, facts: DeviceFacts): JSONObject = JSONObject()
        .put("installId", installId)
        .put("appVersionName", facts.appVersionName)
        .put("appVersionCode", facts.appVersionCode)
        .put("androidRelease", facts.androidRelease)
        .put("sdkInt", facts.sdkInt)
        .put("manufacturer", facts.manufacturer)
        .put("model", facts.model)
        .put("abi", facts.abi)
        .put("locale", facts.locale)
        .put("runtimeInstalled", facts.runtimeInstalled)

    fun reportPolicy(json: JSONObject) = ReportPolicy(
        enabled = json.optBoolean("enabled", false),
        mediaEnabled = json.optBoolean("mediaEnabled", false),
        maxFiles = json.optInt("maxFiles", 3),
        maxImageBytes = json.optLong("maxImageBytes", 8L * 1024 * 1024),
        maxVideoBytes = json.optLong("maxVideoBytes", 50L * 1024 * 1024),
    )

    /** POST /api/v1/reports: [media] are Cloudinary (publicId, resourceType) pairs; [device] only when attached. */
    fun reportJson(draft: ReportDraft, media: List<Pair<String, String>>, device: DeviceFacts?): JSONObject = JSONObject()
        .put("kind", draft.kind.wire)
        .put("title", draft.title.trim())
        .put("description", draft.description.trim())
        .put("email", draft.email.trim())
        .put("source", "app")
        .put("device", device?.let { deviceFactsJson(it) } ?: JSONObject.NULL)
        .put("media", JSONArray(media.map { (id, type) -> JSONObject().put("publicId", id).put("resourceType", type) }))

    private fun deviceFactsJson(f: DeviceFacts): JSONObject = JSONObject()
        .put("appVersionName", f.appVersionName).put("appVersionCode", f.appVersionCode)
        .put("androidRelease", f.androidRelease).put("sdkInt", f.sdkInt)
        .put("manufacturer", f.manufacturer).put("model", f.model)
        .put("abi", f.abi).put("locale", f.locale).put("runtimeInstalled", f.runtimeInstalled)

    fun draftJson(d: ReportDraft): JSONObject = JSONObject()
        .put("kind", d.kind.name).put("title", d.title).put("description", d.description)
        .put("email", d.email).put("attachments", JSONArray(d.attachments)).put("attachDevice", d.attachDevice)

    fun draft(json: JSONObject): ReportDraft {
        val files = json.optJSONArray("attachments") ?: JSONArray()
        return ReportDraft(
            kind = runCatching { ReportKind.valueOf(json.optString("kind")) }.getOrDefault(ReportKind.Bug),
            title = json.optString("title"),
            description = json.optString("description"),
            email = json.optString("email"),
            attachments = List(files.length()) { files.getString(it) },
            attachDevice = json.optBoolean("attachDevice", false),
        )
    }
}
