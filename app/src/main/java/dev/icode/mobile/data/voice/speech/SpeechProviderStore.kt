package dev.icode.mobile.data.voice.speech

import android.content.Context
import android.content.SharedPreferences
import dev.icode.mobile.data.io.KeystoreCipher
import dev.icode.mobile.data.io.commitOrFail
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechProviderKind
import dev.icode.mobile.domain.voice.SpeechWay
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * The speech providers the user added, which one listens and which one speaks (docs/speech-plan.md §4.5,
 * docs/tts-plan.md §4.5): the preferences `icode.speech`. Each key is encrypted by a Keystore key of iCode's own and is in no
 * provider this returns: [key] alone decrypts it, for a hearing.
 */
internal class SpeechProviderStore(
    private val preferences: SharedPreferences,
    private val encrypt: (String) -> String,
    /** Null when this device can no longer decrypt it. */
    private val decrypt: (String) -> String?,
) {
    constructor(context: Context, cipher: KeystoreCipher = KeystoreCipher(KEY_ALIAS)) : this(
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
        cipher::encrypt,
        cipher::decrypt,
    )

    private val lock = Any()

    /** Android's recognizer, with its language as saved, then the user's providers. */
    fun providers(): List<SpeechProvider> = synchronized(lock) {
        listOf(SpeechProvider.Android.copy(language = preferences.getString(KEY_ANDROID_LANGUAGE, null))) + stored()
    }

    /** The one that listens: Android's when the one chosen is gone. */
    fun inUse(): String = chosen(SpeechWay.Listening)

    /** The one that speaks: Android's when the one chosen is gone. */
    fun speaker(): String = chosen(SpeechWay.Speaking)

    private fun chosen(way: SpeechWay): String = synchronized(lock) {
        preferences.getString(choiceOf(way), null)?.takeIf { id -> stored().any { it.id == id } } ?: SpeechProvider.ANDROID
    }

    /**
     * Adds [provider] (ID "") or replaces it, and returns it as kept. [key] null keeps the saved one;
     * a key goes with the kind it was made for. A key given unlinks the assistant's; linked to it, the
     * provider's own is forgotten. Android's keeps only its language.
     */
    fun save(provider: SpeechProvider, key: String?): SpeechProvider = synchronized(lock) {
        if (provider.id == SpeechProvider.ANDROID) {
            preferences.commitOrFail("Unable to save the speech provider") {
                provider.language?.trim()?.takeIf { it.isNotEmpty() }?.let { putString(KEY_ANDROID_LANGUAGE, it) } ?: remove(KEY_ANDROID_LANGUAGE)
            }
            return@synchronized providers().first()
        }
        val existing = stored()
        val saved = existing.firstOrNull { it.id == provider.id }
        val id = saved?.id ?: "s-" + UUID.randomUUID().toString().take(8)
        val kept = provider.copy(
            id = id,
            name = provider.name.trim(),
            address = provider.address?.trim()?.takeIf { it.isNotEmpty() },
            model = provider.model?.trim()?.takeIf { it.isNotEmpty() },
            language = provider.language?.trim()?.takeIf { it.isNotEmpty() },
            voice = provider.voice?.trim()?.takeIf { it.isNotEmpty() },
            voiceModel = provider.voiceModel?.trim()?.takeIf { it.isNotEmpty() },
            hasKey = false,
            keyFrom = provider.keyFrom.takeIf { key.isNullOrBlank() },
        )
        val all = if (saved == null) existing + kept else existing.map { if (it.id == id) kept else it }
        val secret = key?.trim()?.takeIf { it.isNotEmpty() }?.let(encrypt)
        preferences.commitOrFail("Unable to save the speech provider") {
            putString(KEY_PROVIDERS, encode(all))
            when {
                secret != null -> putString(keyOf(id), secret)
                kept.keyFrom != null || (saved != null && saved.kind != kept.kind) -> remove(keyOf(id))
            }
        }
        stored().first { it.id == id }
    }

    /** Forgets [id] and its key; each way it served, Android's serves. */
    fun delete(id: String) = synchronized(lock) {
        val left = stored().filter { it.id != id }
        preferences.commitOrFail("Unable to remove the speech provider") {
            putString(KEY_PROVIDERS, encode(left))
            remove(keyOf(id))
            SpeechWay.entries.map(::choiceOf).filter { preferences.getString(it, null) == id }.forEach(::remove)
        }
    }

    /** [id] serves [way] from now on. */
    fun use(id: String, way: SpeechWay) = synchronized(lock) {
        preferences.commitOrFail("Unable to choose the speech provider") {
            if (id == SpeechProvider.ANDROID) remove(choiceOf(way)) else putString(choiceOf(way), id)
        }
    }

    /** [id]'s key, decrypted for a hearing; one this device can no longer decrypt is removed. */
    fun key(id: String): String? = synchronized(lock) {
        val secret = preferences.getString(keyOf(id), null) ?: return@synchronized null
        decrypt(secret) ?: null.also { runCatching { preferences.commitOrFail("") { remove(keyOf(id)) } } }
    }

    private fun stored(): List<SpeechProvider> {
        val text = preferences.getString(KEY_PROVIDERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { index ->
                val json = array.getJSONObject(index)
                val kind = SpeechProviderKind.of(json.optString("kind"))?.takeIf { it != SpeechProviderKind.Android } ?: return@mapNotNull null
                val id = json.getString("id")
                SpeechProvider(
                    id = id,
                    kind = kind,
                    name = json.getString("name"),
                    address = json.optString("address").ifEmpty { null },
                    model = json.optString("model").ifEmpty { null },
                    language = json.optString("language").ifEmpty { null },
                    hasKey = preferences.contains(keyOf(id)),
                    keyFrom = json.optString("keyFrom").ifEmpty { null },
                    voice = json.optString("voice").ifEmpty { null },
                    voiceModel = json.optString("voiceModel").ifEmpty { null },
                    speed = json.optDouble("speed").takeUnless { it.isNaN() }?.toFloat(),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(providers: List<SpeechProvider>): String = JSONArray(
        providers.map { provider ->
            JSONObject().put("id", provider.id).put("kind", provider.kind.name).put("name", provider.name)
                .apply {
                    provider.address?.let { put("address", it) }
                    provider.model?.let { put("model", it) }
                    provider.language?.let { put("language", it) }
                    provider.keyFrom?.let { put("keyFrom", it) }
                    provider.voice?.let { put("voice", it) }
                    provider.voiceModel?.let { put("voiceModel", it) }
                    provider.speed?.let { put("speed", it.toDouble()) }
                }
        },
    ).toString()

    private fun keyOf(id: String) = "key.$id"

    private fun choiceOf(way: SpeechWay) = when (way) {
        SpeechWay.Listening -> KEY_IN_USE
        SpeechWay.Speaking -> KEY_SPEAKER
    }

    private companion object {
        const val NAME = "icode.speech"
        const val KEY_ALIAS = "icode.speech.key"
        const val KEY_PROVIDERS = "providers"
        const val KEY_IN_USE = "inUse"
        const val KEY_SPEAKER = "speaker"
        const val KEY_ANDROID_LANGUAGE = "androidLanguage"
    }
}
