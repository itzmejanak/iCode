package dev.icode.mobile.data.assistant.acp

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.icode.mobile.data.io.KeystoreCipher
import dev.icode.mobile.domain.McpValue

/**
 * What iCode keeps of the coding agents (docs/assistant-and-mcp.md), in its own preferences: the agent
 * new chats go to, and the keys the user entered for an agent, encrypted with a Keystore key of their
 * own. A key is given only to its agent's process, never logged, and never shown again.
 */
internal class AgentStore(
    private val preferences: SharedPreferences,
    private val encrypt: (String) -> String,
    /** Null when this device can no longer decrypt it: it reads as missing. */
    private val decrypt: (String) -> String?,
) {
    constructor(context: Context, cipher: KeystoreCipher = KeystoreCipher(KEY_ALIAS)) : this(
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
        cipher::encrypt,
        cipher::decrypt,
    )

    private val lock = Any()

    /** The agent new chats go to; null for the built-in assistant. */
    fun chosen(): String? = preferences.getString(KEY_CHOSEN, null)

    fun choose(agent: String?) = synchronized(lock) {
        preferences.edit(commit = true) { if (agent == null) remove(KEY_CHOSEN) else putString(KEY_CHOSEN, agent) }
    }

    /** [agent]'s keys by name, and whether each is saved: never their values. */
    fun keys(agent: String, names: List<String>): List<McpValue> = synchronized(lock) {
        names.map { McpValue(it, secret = true, saved = preferences.contains(key(agent, it))) }
    }

    /** [agent]'s saved keys with their values, for its process's environment only. */
    fun reveal(agent: String, names: List<String>): Map<String, String> = synchronized(lock) {
        names.mapNotNull { name -> preferences.getString(key(agent, name), null)?.let(decrypt)?.let { name to it } }.toMap()
    }

    /** [values] saved for [agent], encrypted: a null value keeps the one saved, a blank one forgets it. */
    fun save(agent: String, values: List<McpValue>) = synchronized(lock) {
        preferences.edit(commit = true) {
            values.forEach { value ->
                val typed = value.value ?: return@forEach
                if (typed.isBlank()) remove(key(agent, value.name)) else putString(key(agent, value.name), encrypt(typed))
            }
        }
    }

    private fun key(agent: String, name: String) = "key.$agent.$name"

    companion object {
        const val NAME = "icode.agents"
        const val KEY_ALIAS = "icode.agents.key"
        private const val KEY_CHOSEN = "chosen"
    }
}
