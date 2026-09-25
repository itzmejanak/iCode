package dev.icode.mobile.data.git

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores Git credentials the application owns, encrypted by the Android
 * Keystore.
 *
 * The token never sits in plaintext in preferences: the key material stays in
 * the Keystore and cannot be exported, so a backup or a dump of the app's
 * preferences yields ciphertext only (architecture.md section 21).
 *
 * Credentials are deliberately kept out of the workspace record — a repository
 * the user shares must never carry their token.
 */
internal class GitCredentialStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences("icode.git.credentials", Context.MODE_PRIVATE)

    data class HttpsCredential(val host: String, val username: String, val token: String)

    fun saveHttps(credential: HttpsCredential) {
        require(credential.host.isNotBlank()) { "Enter the host, for example github.com" }
        require(credential.username.isNotBlank()) { "Enter the account name" }
        require(credential.token.isNotBlank()) { "Enter a personal access token" }
        preferences.edit()
            .putString(KEY_HOST, credential.host.trim())
            .putString(KEY_USERNAME, credential.username.trim())
            .putString(KEY_TOKEN, encrypt(credential.token.trim()))
            .apply()
    }

    fun loadHttps(): HttpsCredential? {
        val host = preferences.getString(KEY_HOST, null) ?: return null
        val username = preferences.getString(KEY_USERNAME, null) ?: return null
        val token = preferences.getString(KEY_TOKEN, null)?.let(::decrypt) ?: return null
        return HttpsCredential(host, username, token)
    }

    fun clearHttps() {
        preferences.edit().remove(KEY_HOST).remove(KEY_USERNAME).remove(KEY_TOKEN).apply()
    }

    val hasHttps: Boolean get() = preferences.contains(KEY_TOKEN)

    /** `host` and account only — never the token, which must not reach a log or a screen. */
    fun describeHttps(): String? = loadHttps()?.let { "${it.username}@${it.host}" }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        // The IV is generated per encryption and stored alongside the payload.
        val payload = cipher.iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
        val body = payload.copyOfRange(GCM_IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "icode.git.credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val KEY_HOST = "https.host"
        const val KEY_USERNAME = "https.username"
        const val KEY_TOKEN = "https.token"
    }
}
