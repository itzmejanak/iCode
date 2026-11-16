package dev.icode.mobile.data.git

import android.content.Context
import dev.icode.mobile.data.io.KeystoreCipher
import dev.icode.mobile.data.io.commitOrFail
import dev.icode.mobile.domain.GitRemoteRules

/**
 * Stores Git credentials the application owns, encrypted by the Android
 * Keystore.
 *
 * The token never sits in plaintext in preferences: the key material stays in
 * the Keystore and cannot be exported, so a backup or a dump of the app's
 * preferences yields ciphertext only (docs/source-control.md).
 *
 * Credentials are deliberately kept out of the workspace record — a repository
 * the user shares must never carry their token.
 */
internal class GitCredentialStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences("icode.git.credentials", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEY_ALIAS)

    data class HttpsCredential(val host: String, val username: String, val token: String)

    fun saveHttps(credential: HttpsCredential) {
        GitRemoteRules.hostError(credential.host.trim())?.let { throw IllegalArgumentException(it) }
        require(credential.username.isNotBlank()) { "Enter the account name" }
        require(credential.token.isNotBlank()) { "Enter a personal access token" }
        preferences.commitOrFail("Unable to save the token") {
            putString(KEY_HOST, credential.host.trim())
            putString(KEY_USERNAME, credential.username.trim())
            putString(KEY_TOKEN, cipher.encrypt(credential.token.trim()))
        }
    }

    fun loadHttps(): HttpsCredential? {
        val host = preferences.getString(KEY_HOST, null) ?: return null
        val username = preferences.getString(KEY_USERNAME, null) ?: return null
        val token = preferences.getString(KEY_TOKEN, null)?.let(cipher::decrypt) ?: return null
        return HttpsCredential(host, username, token)
    }

    fun clearHttps() {
        preferences.commitOrFail("Unable to remove the token") {
            remove(KEY_HOST)
            remove(KEY_USERNAME)
            remove(KEY_TOKEN)
        }
    }

    val hasHttps: Boolean get() = preferences.contains(KEY_TOKEN)

    /**
     * `account@host` only, read without decrypting the token: describing a
     * credential never brings the secret into memory. A token this device can
     * no longer decrypt is found, and cleared, when it is used.
     */
    fun describeHttps(): String? {
        if (!preferences.contains(KEY_TOKEN)) return null
        val host = preferences.getString(KEY_HOST, null) ?: return null
        val username = preferences.getString(KEY_USERNAME, null) ?: return null
        return "$username@$host"
    }

    private companion object {
        const val KEY_ALIAS = "icode.git.credentials"
        const val KEY_HOST = "https.host"
        const val KEY_USERNAME = "https.username"
        const val KEY_TOKEN = "https.token"
    }
}
