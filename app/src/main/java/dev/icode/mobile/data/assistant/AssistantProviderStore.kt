package dev.icode.mobile.data.assistant

import android.content.Context
import dev.icode.mobile.data.io.KeystoreCipher
import dev.icode.mobile.data.io.commitOrFail
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * The assistant's provider (§4.7, §12 R3, R4, R8, R9): its settings and the choices made on the chat
 * page, and the key, which leaves only as a turn's [AssistantConnection].
 */
internal interface AssistantProviderStore {
    fun settings(): AssistantSettings?

    /**
     * The settings form. [key] null keeps the saved key, unless the provider changed: a key goes with
     * its provider. The limits are [model]'s, kept for it when another is chosen.
     */
    fun save(
        kind: AssistantProviderKind,
        baseUrl: String?,
        model: String,
        key: String?,
        contextWindow: Long?,
        maxOutput: Long?,
    )

    /** The chat page's model picker: the provider and its key stay. */
    fun chooseModel(model: String)

    fun chooseEffort(effort: AssistantEffort?)

    fun chooseMode(mode: AssistantMode)

    /** The chat page's permissions: changes and commands wait for an answer, or run. */
    fun setAskFirst(ask: Boolean)

    /** Model IDs chosen before with the current provider, newest first. */
    fun recentModels(): List<String>

    fun clear()

    /** What a turn connects with; null when nothing is set. */
    fun connection(): AssistantConnection?
}

/** The settings in plain preferences, the API key encrypted by a Keystore key of its own. */
internal class SharedPreferencesAssistantProviderStore(context: Context) : AssistantProviderStore {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEY_ALIAS)

    override fun settings(): AssistantSettings? {
        val kind = kind() ?: return null
        val model = preferences.getString(KEY_MODEL, null) ?: return null
        val limits = limits(kind, model)
        return AssistantSettings(
            kind = kind,
            baseUrl = preferences.getString(KEY_BASE_URL, null),
            model = model,
            hasKey = preferences.contains(KEY_SECRET),
            askBeforeChanges = preferences.getBoolean(KEY_ASK, true),
            effort = preferences.getString(KEY_EFFORT, null)?.let { name -> AssistantEffort.entries.firstOrNull { it.name == name } },
            mode = preferences.getString(KEY_MODE, null)?.let { name -> AssistantMode.entries.firstOrNull { it.name == name } } ?: AssistantMode.Agent,
            contextWindow = limits?.optLong(LIMIT_CONTEXT)?.takeIf { it > 0 },
            maxOutput = limits?.optLong(LIMIT_OUTPUT)?.takeIf { it > 0 },
        )
    }

    override fun save(
        kind: AssistantProviderKind,
        baseUrl: String?,
        model: String,
        key: String?,
        contextWindow: Long?,
        maxOutput: Long?,
    ) {
        val providerChanged = kind() != kind
        val secret = key?.trim()?.takeIf { it.isNotEmpty() }?.let(cipher::encrypt)
        val id = model.trim()
        preferences.commitOrFail("Unable to save the assistant's settings") {
            putString(KEY_KIND, kind.name)
            if (baseUrl == null) remove(KEY_BASE_URL) else putString(KEY_BASE_URL, baseUrl)
            putString(KEY_MODEL, id)
            when {
                secret != null -> putString(KEY_SECRET, secret)
                providerChanged -> remove(KEY_SECRET)
            }
            if (contextWindow == null && maxOutput == null) {
                remove(limitsKey(kind, id))
            } else {
                putString(limitsKey(kind, id), JSONObject().put(LIMIT_CONTEXT, contextWindow ?: 0).put(LIMIT_OUTPUT, maxOutput ?: 0).toString())
            }
            putString(recentKey(kind), recent(kind, id))
        }
    }

    override fun chooseModel(model: String) {
        val kind = kind() ?: return
        val id = model.trim()
        preferences.commitOrFail("Unable to save the assistant's model") {
            putString(KEY_MODEL, id)
            putString(recentKey(kind), recent(kind, id))
        }
    }

    override fun chooseEffort(effort: AssistantEffort?) {
        preferences.commitOrFail("Unable to save the assistant's settings") {
            if (effort == null) remove(KEY_EFFORT) else putString(KEY_EFFORT, effort.name)
        }
    }

    override fun chooseMode(mode: AssistantMode) {
        preferences.commitOrFail("Unable to save the assistant's settings") { putString(KEY_MODE, mode.name) }
    }

    override fun setAskFirst(ask: Boolean) {
        preferences.commitOrFail("Unable to save the assistant's settings") { putBoolean(KEY_ASK, ask) }
    }

    override fun recentModels(): List<String> = kind()?.let(::recentOf).orEmpty()

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

    private fun kind(): AssistantProviderKind? =
        preferences.getString(KEY_KIND, null)?.let { name -> AssistantProviderKind.entries.firstOrNull { it.name == name } }

    private fun limits(kind: AssistantProviderKind, model: String): JSONObject? =
        preferences.getString(limitsKey(kind, model), null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun recentOf(kind: AssistantProviderKind): List<String> =
        preferences.getString(recentKey(kind), null)
            ?.let { text -> runCatching { JSONArray(text).let { array -> (0 until array.length()).map(array::getString) } }.getOrNull() }
            .orEmpty()

    /** [kind]'s recent models with [model] first, at most [MAX_RECENT]. */
    private fun recent(kind: AssistantProviderKind, model: String): String =
        JSONArray((listOf(model) + recentOf(kind).filter { it != model }).take(MAX_RECENT)).toString()

    private fun limitsKey(kind: AssistantProviderKind, model: String) = "limits.${kind.name}.$model"
    private fun recentKey(kind: AssistantProviderKind) = "recent_models.${kind.name}"

    private companion object {
        const val NAME = "icode.assistant"
        const val KEY_ALIAS = "icode.assistant.key"
        const val KEY_KIND = "provider"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_ASK = "ask_before_changes"
        const val KEY_SECRET = "api_key"
        const val KEY_EFFORT = "effort"
        const val KEY_MODE = "mode"
        const val LIMIT_CONTEXT = "context"
        const val LIMIT_OUTPUT = "output"
        const val MAX_RECENT = 8
    }
}
