package dev.icode.mobile.data.language

import android.content.Context
import dev.icode.mobile.data.io.commitOrFail
import dev.icode.mobile.domain.LanguageServiceConfiguration
import org.json.JSONArray
import org.json.JSONObject

/** Where configurations are kept: the app's preferences, or a test's memory. */
internal interface LanguageServiceSaves {
    fun load(): List<LanguageServiceConfiguration>
    fun save(services: List<LanguageServiceConfiguration>)
}

/**
 * The user's language servers, as configured (docs/editor-and-language-services.md). Version 2 adds languages,
 * environment and settings; a version 1 list — v0.1.0 saved those — is read once and written as
 * version 2 at the next save. Process state is never saved.
 */
internal class LanguageServiceStore(context: Context) : LanguageServiceSaves {
    private val preferences = context.applicationContext.getSharedPreferences("language_services", Context.MODE_PRIVATE)

    override fun load(): List<LanguageServiceConfiguration> {
        val saved = preferences.getString(KEY, null) ?: preferences.getString(KEY_V1, null) ?: return emptyList()
        return parse(saved)
    }

    override fun save(services: List<LanguageServiceConfiguration>) {
        preferences.commitOrFail("Unable to save language services") {
            putString(KEY, format(services))
            remove(KEY_V1)
        }
    }

    companion object {
        private const val KEY = "configured_servers_v2"
        private const val KEY_V1 = "configured_servers_v1"

        /** A saved list, of either version: version 1's entries take the new fields' defaults. */
        fun parse(saved: String): List<LanguageServiceConfiguration> {
            val array = JSONArray(saved)
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                LanguageServiceConfiguration(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    command = item.getString("command"),
                    languages = item.optString("languages"),
                    patterns = item.optString("patterns"),
                    rootMarkers = item.optString("rootMarkers"),
                    environment = item.optString("environment"),
                    initializationOptions = item.optString("initializationOptions", "{}"),
                    settings = item.optString("settings", "{}"),
                )
            }
        }

        fun format(services: List<LanguageServiceConfiguration>): String = JSONArray().apply {
            services.forEach { service ->
                put(JSONObject().apply {
                    put("id", service.id)
                    put("name", service.name)
                    put("command", service.command)
                    put("languages", service.languages)
                    put("patterns", service.patterns)
                    put("rootMarkers", service.rootMarkers)
                    put("environment", service.environment)
                    put("initializationOptions", service.initializationOptions)
                    put("settings", service.settings)
                })
            }
        }.toString()
    }
}
