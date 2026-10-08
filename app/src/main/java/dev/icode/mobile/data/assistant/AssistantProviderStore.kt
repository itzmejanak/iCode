package dev.icode.mobile.data.assistant

import android.content.Context
import android.content.SharedPreferences
import dev.icode.mobile.data.io.KeystoreCipher
import dev.icode.mobile.data.io.commitOrFail
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** A provider as kept: its models as the user listed them, and whether a key is saved — never the key. */
internal data class StoredProvider(
    val id: String,
    val kind: AssistantProviderKind,
    val name: String,
    val baseUrl: String?,
    val hasKey: Boolean,
    val models: List<AssistantModelSpec>,
)

/** Everything the assistant's settings keep, but the keys. */
internal data class StoredSettings(
    val providers: List<StoredProvider> = emptyList(),
    val choice: AssistantChoice? = null,
    val askBeforeChanges: Boolean = true,
    val effort: AssistantEffort? = null,
    val mode: AssistantMode = AssistantMode.Code,
    /** The model that writes commit messages, while it is listed; null: the chat's. */
    val commitChoice: AssistantChoice? = null,
)

/**
 * The assistant's providers and the chat page's choices (§4.7, §13 P1–P3). Each provider's key is
 * its own, and leaves only as a turn's [AssistantConnection].
 */
internal interface AssistantProviderStore {
    fun settings(): StoredSettings

    /**
     * Adds a provider ([id] "") or replaces one, and returns its ID. [key] null keeps the saved key;
     * [baseUrl] is null where the kind asks none. The chat's choice stays valid: a model taken out
     * of the chosen provider moves the choice to its first.
     */
    fun saveProvider(id: String, kind: AssistantProviderKind, name: String, baseUrl: String?, key: String?, models: List<AssistantModelSpec>): String

    /** Forgets a provider and its key; the choice moves to the first model left, if any. */
    fun removeProvider(id: String)

    fun choose(choice: AssistantChoice)

    /** The model that writes commit messages; null: the chat's. One no longer listed is not kept. */
    fun chooseCommitModel(choice: AssistantChoice?)

    fun chooseEffort(effort: AssistantEffort?)

    fun chooseMode(mode: AssistantMode)

    fun setAskFirst(ask: Boolean)

    /** Where [provider] is, with its key decrypted; null when it is gone. */
    fun connection(provider: String): AssistantConnection?
}

/** The settings in plain preferences, each key encrypted by a Keystore key of iCode's own. */
internal class SharedPreferencesAssistantProviderStore(
    private val preferences: SharedPreferences,
    private val encrypt: (String) -> String,
    /** Null when this device can no longer decrypt it. */
    private val decrypt: (String) -> String?,
) : AssistantProviderStore {
    constructor(context: Context, cipher: KeystoreCipher = KeystoreCipher(KEY_ALIAS)) : this(
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
        cipher::encrypt,
        cipher::decrypt,
    )

    private val lock = Any()

    override fun settings(): StoredSettings = synchronized(lock) {
        val providers = providers()
        StoredSettings(
            providers = providers,
            choice = preferences.getString(KEY_CHOICE_PROVIDER, null)?.let { provider ->
                preferences.getString(KEY_CHOICE_MODEL, null)?.let { AssistantChoice(provider, it) }
            },
            askBeforeChanges = preferences.getBoolean(KEY_ASK, true),
            effort = preferences.getString(KEY_EFFORT, null)?.let { name -> AssistantEffort.entries.firstOrNull { it.name == name } },
            mode = preferences.getString(KEY_MODE, null)?.let { name ->
                // "Agent" was renamed to Code in 2026-10; a saved "Agent" reads as Code.
                if (name == "Agent") AssistantMode.Code else AssistantMode.entries.firstOrNull { it.name == name }
            } ?: AssistantMode.Code,
            // A model taken out of its provider, or a provider removed, leaves the chat's model to write them.
            commitChoice = preferences.getString(KEY_COMMIT_PROVIDER, null)?.let { provider ->
                preferences.getString(KEY_COMMIT_MODEL, null)?.let { AssistantChoice(provider, it) }
            }?.takeIf { listed(providers, it) },
        )
    }

    override fun saveProvider(
        id: String,
        kind: AssistantProviderKind,
        name: String,
        baseUrl: String?,
        key: String?,
        models: List<AssistantModelSpec>,
    ): String = synchronized(lock) {
        val existing = providers()
        val saved = existing.firstOrNull { it.id == id }
        val providerId = saved?.id ?: newId()
        val secret = key?.trim()?.takeIf { it.isNotEmpty() }?.let(encrypt)
        val provider = StoredProvider(providerId, kind, name.trim(), baseUrl, hasKey = false, models.map { it.copy(id = it.id.trim()) })
        val all = if (saved == null) existing + provider else existing.map { if (it.id == providerId) provider else it }
        preferences.commitOrFail("Unable to save the assistant's provider") {
            putString(KEY_PROVIDERS, encode(all))
            when {
                secret != null -> putString(keyOf(providerId), secret)
                // A key goes with the kind it was made for.
                saved != null && saved.kind != kind -> remove(keyOf(providerId))
            }
            validChoice(all)?.let { putChoice(it) } ?: removeChoice()
        }
        providerId
    }

    override fun removeProvider(id: String) = synchronized(lock) {
        val left = providers().filter { it.id != id }
        preferences.commitOrFail("Unable to remove the assistant's provider") {
            putString(KEY_PROVIDERS, encode(left))
            remove(keyOf(id))
            validChoice(left)?.let { putChoice(it) } ?: removeChoice()
        }
    }

    override fun choose(choice: AssistantChoice) = synchronized(lock) {
        if (!listed(providers(), choice)) return@synchronized
        preferences.commitOrFail("Unable to save the assistant's model") { putChoice(choice) }
    }

    override fun chooseCommitModel(choice: AssistantChoice?) = synchronized(lock) {
        if (choice != null && !listed(providers(), choice)) return@synchronized
        preferences.commitOrFail("Unable to save the commit messages' model") {
            if (choice == null) {
                remove(KEY_COMMIT_PROVIDER)
                remove(KEY_COMMIT_MODEL)
            } else {
                putString(KEY_COMMIT_PROVIDER, choice.provider)
                putString(KEY_COMMIT_MODEL, choice.model)
            }
        }
    }

    private fun listed(providers: List<StoredProvider>, choice: AssistantChoice) =
        providers.any { provider -> provider.id == choice.provider && provider.models.any { it.id == choice.model } }

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

    /** A key this device can no longer decrypt is removed: the provider's refusal then asks for it again. */
    override fun connection(provider: String): AssistantConnection? = synchronized(lock) {
        val stored = providers().firstOrNull { it.id == provider } ?: return@synchronized null
        val secret = preferences.getString(keyOf(provider), null)
        val key = secret?.let(decrypt)
        if (secret != null && key == null) runCatching { preferences.commitOrFail("") { remove(keyOf(provider)) } }
        AssistantConnection(stored.kind, stored.baseUrl, key)
    }

    private fun providers(): List<StoredProvider> {
        val text = preferences.getString(KEY_PROVIDERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { index ->
                val json = array.getJSONObject(index)
                val kind = AssistantProviderKind.entries.firstOrNull { it.name == json.getString("kind") } ?: return@mapNotNull null
                val id = json.getString("id")
                val models = json.getJSONArray("models")
                StoredProvider(
                    id = id,
                    kind = kind,
                    name = json.getString("name"),
                    baseUrl = json.optString("baseUrl").ifEmpty { null },
                    hasKey = preferences.contains(keyOf(id)),
                    models = (0 until models.length()).map { decodeModel(models.getJSONObject(it)) },
                )
            }
        }.getOrDefault(emptyList())
    }

    /** The choice as saved when it names a model still listed; else the first model of the first provider that has one. */
    private fun validChoice(providers: List<StoredProvider>): AssistantChoice? {
        val provider = preferences.getString(KEY_CHOICE_PROVIDER, null)
        val model = preferences.getString(KEY_CHOICE_MODEL, null)
        val saved = providers.firstOrNull { it.id == provider }
        if (saved != null && saved.models.any { it.id == model }) return AssistantChoice(saved.id, model!!)
        // The chosen provider stays chosen while it has a model, on its first.
        saved?.models?.firstOrNull()?.let { return AssistantChoice(saved.id, it.id) }
        return providers.firstOrNull { it.models.isNotEmpty() }?.let { AssistantChoice(it.id, it.models.first().id) }
    }

    private fun SharedPreferences.Editor.putChoice(choice: AssistantChoice) {
        putString(KEY_CHOICE_PROVIDER, choice.provider)
        putString(KEY_CHOICE_MODEL, choice.model)
    }

    private fun SharedPreferences.Editor.removeChoice() {
        remove(KEY_CHOICE_PROVIDER)
        remove(KEY_CHOICE_MODEL)
    }

    private fun encode(providers: List<StoredProvider>): String = JSONArray().apply {
        providers.forEach { provider ->
            put(
                JSONObject()
                    .put("id", provider.id)
                    .put("kind", provider.kind.name)
                    .put("name", provider.name)
                    .put("baseUrl", provider.baseUrl.orEmpty())
                    .put("models", JSONArray().apply { provider.models.forEach { put(encodeModel(it)) } }),
            )
        }
    }.toString()

    private fun encodeModel(spec: AssistantModelSpec) = JSONObject().put("id", spec.id).apply {
        spec.contextWindow?.let { put("context", it) }
        spec.maxOutput?.let { put("output", it) }
        if (spec.images) put("images", true)
        if (spec.reasoning) put("reasoning", true)
    }

    private fun decodeModel(json: JSONObject) = AssistantModelSpec(
        id = json.getString("id"),
        contextWindow = json.optLong("context").takeIf { it > 0 },
        maxOutput = json.optLong("output").takeIf { it > 0 },
        images = json.optBoolean("images"),
        reasoning = json.optBoolean("reasoning"),
    )

    private fun newId() = "p-" + UUID.randomUUID().toString().take(8)
    private fun keyOf(provider: String) = "key.$provider"

    private companion object {
        const val NAME = "icode.assistant"
        const val KEY_ALIAS = "icode.assistant.key"
        const val KEY_PROVIDERS = "providers"
        const val KEY_CHOICE_PROVIDER = "choice.provider"
        const val KEY_CHOICE_MODEL = "choice.model"
        const val KEY_COMMIT_PROVIDER = "commit.provider"
        const val KEY_COMMIT_MODEL = "commit.model"
        const val KEY_ASK = "ask_before_changes"
        const val KEY_EFFORT = "effort"
        const val KEY_MODE = "mode"
    }
}
