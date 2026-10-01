package dev.icode.mobile.data.assistant

import android.content.Context
import dev.icode.mobile.data.io.KeystoreCipher
import dev.icode.mobile.data.io.commitOrFail
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantSettings

/** The assistant's provider (§4.7): its settings, and the key, which leaves only as a turn's [AssistantConnection]. */
internal interface AssistantProviderStore {
    fun settings(): AssistantSettings?

    /** [key] null keeps the saved key, unless the provider changed: a key goes with its provider. */
    fun save(kind: AssistantProviderKind, baseUrl: String?, model: String, key: String?, askBeforeChanges: Boolean)

    fun clear()

    /** What a turn connects with; null when nothing is set. */
    fun connection(): AssistantConnection?
}

/** The settings in plain preferences, the API key encrypted by a Keystore key of its own. */
internal class SharedPreferencesAssistantProviderStore(context: Context) : AssistantProviderStore {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEY_ALIAS)

    override fun settings(): AssistantSettings? {
        val kind = preferences.getString(KEY_KIND, null)
            ?.let { name -> AssistantProviderKind.entries.firstOrNull { it.name == name } } ?: return null
        val model = preferences.getString(KEY_MODEL, null) ?: return null
        return AssistantSettings(
            kind = kind,
            baseUrl = preferences.getString(KEY_BASE_URL, null),
            model = model,
            hasKey = preferences.contains(KEY_SECRET),
            askBeforeChanges = preferences.getBoolean(KEY_ASK, true),
        )
    }

    override fun save(kind: AssistantProviderKind, baseUrl: String?, model: String, key: String?, askBeforeChanges: Boolean) {
        val providerChanged = settings()?.kind != kind
        val secret = key?.trim()?.takeIf { it.isNotEmpty() }?.let(cipher::encrypt)
        preferences.commitOrFail("Unable to save the assistant's settings") {
            putString(KEY_KIND, kind.name)
            if (baseUrl == null) remove(KEY_BASE_URL) else putString(KEY_BASE_URL, baseUrl)
            putString(KEY_MODEL, model.trim())
            putBoolean(KEY_ASK, askBeforeChanges)
            when {
                secret != null -> putString(KEY_SECRET, secret)
                providerChanged -> remove(KEY_SECRET)
            }
        }
    }

    override fun clear() {
        preferences.commitOrFail("Unable to remove the assistant's settings") { clear() }
    }

    /** A key this device can no longer decrypt is removed: the provider's refusal then asks for it again. */
    override fun connection(): AssistantConnection? {
        val settings = settings() ?: return null
        val stored = preferences.getString(KEY_SECRET, null)
        val key = stored?.let(cipher::decrypt)
        if (stored != null && key == null) runCatching { preferences.commitOrFail("") { remove(KEY_SECRET) } }
        return AssistantConnection(settings.kind, settings.baseUrl, settings.model, key)
    }

    private companion object {
        const val NAME = "icode.assistant"
        const val KEY_ALIAS = "icode.assistant.key"
        const val KEY_KIND = "provider"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_ASK = "ask_before_changes"
        const val KEY_SECRET = "api_key"
    }
}
