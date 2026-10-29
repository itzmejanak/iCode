package dev.icode.mobile.data.web

import android.content.Context
import dev.icode.mobile.domain.AppLinks
import dev.icode.mobile.domain.DeviceInfoConsent
import java.util.UUID
import org.json.JSONObject

/** iCode's own record of the website: the consent answer, the install ID, and the last config read. */
internal class WebPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("icode_web", Context.MODE_PRIVATE)

    var consent: DeviceInfoConsent
        get() = runCatching { DeviceInfoConsent.valueOf(prefs.getString(KEY_CONSENT, null)!!) }.getOrDefault(DeviceInfoConsent.Unanswered)
        set(value) = prefs.edit().putString(KEY_CONSENT, value.name).apply()

    /** A random ID made on opt-in, never derived from the device; null while not sharing. */
    val installId: String? get() = prefs.getString(KEY_INSTALL_ID, null)

    fun newInstallId(): String = UUID.randomUUID().toString().also { prefs.edit().putString(KEY_INSTALL_ID, it).apply() }

    fun clearInstallId() = prefs.edit().remove(KEY_INSTALL_ID).remove(KEY_REGISTERED).apply()

    /** The facts last registered, so a change (an update, a new runtime) registers again. */
    var registeredFingerprint: String?
        get() = prefs.getString(KEY_REGISTERED, null)
        set(value) = prefs.edit().putString(KEY_REGISTERED, value).apply()

    /** The server's switch and interval, kept from the last config read. */
    var telemetryEnabled: Boolean
        get() = prefs.getBoolean(KEY_TELEMETRY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TELEMETRY_ENABLED, value).apply()

    var heartbeatMinutes: Long
        get() = prefs.getLong(KEY_HEARTBEAT, DEFAULT_HEARTBEAT_MINUTES)
        set(value) = prefs.edit().putLong(KEY_HEARTBEAT, value).apply()

    /** The last config's links, so About shows them offline. */
    var cachedLinks: AppLinks?
        get() = prefs.getString(KEY_LINKS, null)?.let { runCatching { WebJson.links(JSONObject(it)) }.getOrNull() }
        set(value) = prefs.edit().putString(KEY_LINKS, value?.let { WebJson.linksJson(it).toString() }).apply()

    var cachedSupportText: String
        get() = prefs.getString(KEY_SUPPORT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SUPPORT, value).apply()

    /** How often iCode checks for updates by itself, from the last config read. */
    var checkIntervalHours: Long
        get() = prefs.getLong(KEY_CHECK_INTERVAL, DEFAULT_CHECK_INTERVAL_HOURS)
        set(value) = prefs.edit().putLong(KEY_CHECK_INTERVAL, value).apply()

    var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    val dismissedNotices: Set<String> get() = prefs.getStringSet(KEY_DISMISSED, emptySet()).orEmpty()

    fun dismissNotice(id: String) = prefs.edit().putStringSet(KEY_DISMISSED, dismissedNotices + id).apply()

    companion object {
        const val DEFAULT_CHECK_INTERVAL_HOURS = 24L
        private const val KEY_CHECK_INTERVAL = "check_interval_hours"
        private const val KEY_LAST_CHECK = "last_update_check_at"
        private const val KEY_DISMISSED = "dismissed_notices"
        /** WorkManager's floor for periodic work. */
        const val DEFAULT_HEARTBEAT_MINUTES = 15L
        private const val KEY_CONSENT = "device_info_consent"
        private const val KEY_INSTALL_ID = "install_id"
        private const val KEY_REGISTERED = "registered_fingerprint"
        private const val KEY_TELEMETRY_ENABLED = "telemetry_enabled"
        private const val KEY_HEARTBEAT = "heartbeat_minutes"
        private const val KEY_LINKS = "links"
        private const val KEY_SUPPORT = "support_text"
    }
}
