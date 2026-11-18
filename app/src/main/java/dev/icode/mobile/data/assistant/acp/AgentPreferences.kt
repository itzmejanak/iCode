package dev.icode.mobile.data.assistant.acp

import android.content.Context
import android.content.SharedPreferences
import dev.icode.mobile.data.io.KeystoreCipher
import dev.icode.mobile.data.io.commitOrFail
import org.json.JSONObject

/**
 * What iCode keeps about the coding agents in the chats (docs/assistant-and-mcp.md): the agent chosen
 * last, which new chats start with; the agents signed in, which are not checked again; the untested
 * ones already said so; and the keys entered for an agent, encrypted by a key of the Android Keystore,
 * given only to its process and never shown again.
 */
internal class AgentPreferences(
    private val preferences: SharedPreferences,
    private val encrypt: (String) -> String,
    /** Null when this device can no longer decrypt it: the keys read as missing. */
    private val decrypt: (String) -> String?,
) {
    constructor(context: Context, cipher: KeystoreCipher = KeystoreCipher(KEY_ALIAS)) : this(
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
        cipher::encrypt,
        cipher::decrypt,
    )

    /** The agent chosen last; null once a model is. */
    var last: String?
        get() = preferences.getString(KEY_LAST, null)
        set(value) = preferences.commitOrFail("Unable to save the chosen agent") { putString(KEY_LAST, value) }

    fun signedIn(): Set<String> = preferences.getStringSet(KEY_SIGNED_IN, emptySet()).orEmpty()

    fun signedIn(id: String, yes: Boolean) = preferences.commitOrFail("Unable to save the agent's sign-in") {
        putStringSet(KEY_SIGNED_IN, if (yes) signedIn() + id else signedIn() - id)
    }

    fun untestedSaid(): Set<String> = preferences.getStringSet(KEY_UNTESTED, emptySet()).orEmpty()

    fun said(id: String) = preferences.commitOrFail("Unable to save the agent's note") { putStringSet(KEY_UNTESTED, untestedSaid() + id) }

    /** [id]'s keys, by variable. */
    fun keys(id: String): Map<String, String> {
        val stored = preferences.getString(KEY_KEYS + id, null)?.let(decrypt) ?: return emptyMap()
        return runCatching { JSONObject(stored).let { json -> json.keys().asSequence().associateWith(json::getString) } }.getOrDefault(emptyMap())
    }

    /** [id]'s keys kept, those left empty taken out. */
    fun saveKeys(id: String, values: Map<String, String>) {
        val kept = (keys(id) + values).filterValues { it.isNotEmpty() }
        preferences.commitOrFail("Unable to save the agent's keys") { putString(KEY_KEYS + id, encrypt(JSONObject(kept).toString())) }
    }

    /** [id] removed with everything: its keys go, and it is checked again if it comes back. */
    fun forget(id: String) = preferences.commitOrFail("Unable to forget the agent") {
        remove(KEY_KEYS + id)
        putStringSet(KEY_SIGNED_IN, signedIn() - id)
    }

    private companion object {
        const val NAME = "icode.agents"
        const val KEY_ALIAS = "icode.agents.key"
        const val KEY_LAST = "last"
        const val KEY_SIGNED_IN = "signed-in"
        const val KEY_UNTESTED = "untested-said"
        const val KEY_KEYS = "keys."
    }
}
