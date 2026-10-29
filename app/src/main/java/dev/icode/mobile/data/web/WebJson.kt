package dev.icode.mobile.data.web

import dev.icode.mobile.domain.AppLinks
import dev.icode.mobile.domain.Notice
import dev.icode.mobile.domain.NoticeLevel
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

    fun notices(json: JSONObject): List<Notice> {
        val array = json.optJSONArray("notices") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val level = when (item.optString("level")) {
                "critical" -> NoticeLevel.Critical
                "warning" -> NoticeLevel.Warning
                else -> NoticeLevel.Info
            }
            Notice(id, item.optString("title"), item.optString("body"), level)
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
}
