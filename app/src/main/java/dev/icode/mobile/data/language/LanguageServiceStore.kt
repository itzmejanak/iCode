package dev.icode.mobile.data.language

import android.content.Context
import dev.icode.mobile.domain.LanguageServiceConfiguration
import org.json.JSONArray
import org.json.JSONObject

/** App-level server recipes. The running process/PID is intentionally session-only. */
internal class LanguageServiceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("language_services", Context.MODE_PRIVATE)

    fun load(): List<LanguageServiceConfiguration> {
        val saved = preferences.getString(KEY, null) ?: return emptyList()
        val array = JSONArray(saved)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            LanguageServiceConfiguration(
                id = item.getString("id"),
                name = item.getString("name"),
                command = item.getString("command"),
                patterns = item.optString("patterns"),
                rootMarkers = item.optString("rootMarkers"),
                initializationOptions = item.optString("initializationOptions", "{}"),
            )
        }
    }

    fun save(services: List<LanguageServiceConfiguration>) {
        val array = JSONArray()
        services.forEach { service ->
            array.put(JSONObject().apply {
                put("id", service.id)
                put("name", service.name)
                put("command", service.command)
                put("patterns", service.patterns)
                put("rootMarkers", service.rootMarkers)
                put("initializationOptions", service.initializationOptions)
            })
        }
        check(preferences.edit().putString(KEY, array.toString()).commit()) {
            "Unable to save language services"
        }
    }

    private companion object { const val KEY = "configured_servers_v1" }
}
