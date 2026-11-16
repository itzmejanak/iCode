package dev.icode.mobile.domain

// Commit signing (docs/source-control.md): the Signing step of Git Credentials, and a commit's
// signature in Git's own verdict. gpg makes and keeps the keys; Git's global config says which one
// signs, and whether every commit is signed.

/** A secret key gpg holds that can sign. */
data class GitSigningKey(
    val fingerprint: String,
    /** The long key ID, as `git log` and GitHub show it. */
    val keyId: String,
    /** Its first user ID: "Ada Lovelace <ada@example.com>". */
    val userId: String,
    /** "ed25519", "rsa4096". */
    val algorithm: String,
    val createdAtEpochMillis: Long,
    /** Null when it never expires. */
    val expiresAtEpochMillis: Long?,
) {
    /** Its user ID's email: what GitHub matches a commit's email to. */
    val email: String? get() = userId.substringAfterLast('<', "").substringBefore('>').takeIf { it.isNotEmpty() }
}

/** What the Signing step shows: gpg's keys, and what Git's global config signs with. */
data class GitSigningState(
    /** GnuPG is installed in the runtime; null until checked. */
    val gnupgInstalled: Boolean? = null,
    val keys: List<GitSigningKey> = emptyList(),
    /** `user.signingkey`, as Git's global config names it. */
    val signingKey: String? = null,
    /** `commit.gpgsign` in Git's global config. */
    val signsCommits: Boolean = false,
    /** [key]'s public half, armored, for the provider's form. */
    val publicKey: String? = null,
) {
    /** The key Git signs with. */
    val key: GitSigningKey? get() = GitSigningRules.keyFor(signingKey, keys)
}

/** A commit's signature, as Git's `%G?` judges it. */
enum class GitSignatureStatus {
    /** `G`, `U`: good — `U` from a key gpg does not trust as fully, which is the same signature. */
    Verified,
    /** `X`, `Y`: good, but the signature or its key has expired. */
    Expired,
    /** `R`: good, by a key since revoked. */
    Revoked,
    /** `B`: the commit is not what was signed. */
    Bad,
    /** `E`: gpg has no public key to check it with. */
    UnknownKey,
    /** `N`. */
    Unsigned,
}

data class GitCommitSignature(
    val status: GitSignatureStatus,
    /** The signer's user ID, when gpg knows the key. */
    val signer: String?,
    val keyId: String?,
)

/** Git Credentials' work that never waits on the mutation lock: apt, ssh-keygen, gpg and the network. */
sealed interface GitAuthWorkIntent : ICodeIntent

/** The Signing step. Creating a key and changing its passphrase wait on the GnuPG sheet. */
sealed interface SigningIntent : GitAuthWorkIntent {
    data object InstallGnuPg : SigningIntent

    /** A key of gpg's defaults for the default commit identity; Git then signs every commit with it. */
    data object CreateKey : SigningIntent

    data class UseKey(val fingerprint: String) : SigningIntent

    data class SignCommits(val enabled: Boolean) : SigningIntent

    data class ChangePassphrase(val fingerprint: String) : SigningIntent
}

object GitSigningRules {
    /**
     * The keys that can sign, from `gpg --list-secret-keys --with-colons --fixed-list-mode`: not
     * revoked or expired, with signing among their capabilities.
     */
    fun keys(colons: String): List<GitSigningKey> {
        val keys = mutableListOf<GitSigningKey>()
        var primary: List<String>? = null
        var fingerprint: String? = null
        fun finish(userId: String) {
            val record = primary ?: return
            val print = fingerprint ?: return
            primary = null
            if (record.field(VALIDITY) in UNUSABLE || 'S' !in record.field(CAPABILITIES)) return
            keys += GitSigningKey(
                fingerprint = print,
                keyId = record.field(KEY_ID),
                userId = userId,
                algorithm = record.field(CURVE).ifEmpty {
                    ALGORITHMS[record.field(ALGORITHM)]?.plus(record.field(LENGTH)) ?: "algorithm ${record.field(ALGORITHM)}"
                },
                createdAtEpochMillis = (record.field(CREATED).toLongOrNull() ?: 0L) * MILLIS,
                expiresAtEpochMillis = record.field(EXPIRES).toLongOrNull()?.times(MILLIS),
            )
        }
        colons.lineSequence().map { it.split(':') }.forEach { record ->
            when (record.field(TYPE)) {
                "sec" -> {
                    primary = record
                    fingerprint = null
                }
                "fpr" -> if (primary != null && fingerprint == null) fingerprint = record.field(USER_ID)
                "uid" -> finish(unescape(record.field(USER_ID)))
            }
        }
        return keys
    }

    /**
     * The key `user.signingkey` names: a fingerprint, a long or short key ID — with `0x` or a
     * trailing `!` as gpg allows — or an email.
     */
    fun keyFor(signingKey: String?, keys: List<GitSigningKey>): GitSigningKey? {
        val spec = signingKey?.trim()?.removePrefix("0x")?.removePrefix("0X")?.removeSuffix("!")?.takeIf { it.isNotEmpty() } ?: return null
        return if ('@' in spec) {
            val email = spec.removePrefix("<").removeSuffix(">")
            keys.firstOrNull { it.email.equals(email, ignoreCase = true) }
        } else {
            keys.firstOrNull { it.fingerprint.endsWith(spec, ignoreCase = true) }
        }
    }

    /** `%G?`, `%GK` and `%GS` as `git log` prints them; null for anything else. */
    fun signature(code: String, keyId: String, signer: String): GitCommitSignature? {
        val status = when (code) {
            "G", "U" -> GitSignatureStatus.Verified
            "X", "Y" -> GitSignatureStatus.Expired
            "R" -> GitSignatureStatus.Revoked
            "B" -> GitSignatureStatus.Bad
            "E" -> GitSignatureStatus.UnknownKey
            "N" -> GitSignatureStatus.Unsigned
            else -> return null
        }
        return GitCommitSignature(status, signer.ifEmpty { null }, keyId.ifEmpty { null })
    }

    /**
     * Why gpg failed, in its own words: the last line that says so — "key generation failed:
     * Operation cancelled" — as gpg first prints what it set up on the way.
     */
    fun failure(stderr: String): String? {
        val lines = stderr.lines().map { it.trim().removePrefix("gpg: ") }.filter(String::isNotEmpty)
        return (lines.lastOrNull { line -> FAILURE_WORDS.any { line.contains(it, ignoreCase = true) } } ?: lines.lastOrNull())
    }

    /** [commitEmail] when GitHub would not match it to [key]'s email, else null. */
    fun unmatchedEmail(key: GitSigningKey, commitEmail: String?): String? =
        commitEmail?.takeUnless { key.email.equals(it, ignoreCase = true) }

    /** gpg's colon listing escapes a colon, and what cannot be printed, as `\x3a`. */
    private fun unescape(text: String): String = ESCAPE.replace(text) { it.groupValues[1].toInt(HEX).toChar().toString() }

    private fun List<String>.field(number: Int): String = getOrNull(number - 1).orEmpty()

    // gpg's DETAILS: the fields of a colon listing, counted from 1.
    private const val TYPE = 1
    private const val VALIDITY = 2
    private const val LENGTH = 3
    private const val ALGORITHM = 4
    private const val KEY_ID = 5
    private const val CREATED = 6
    private const val EXPIRES = 7
    private const val USER_ID = 10
    private const val CAPABILITIES = 12
    private const val CURVE = 17
    private const val MILLIS = 1000L
    private const val HEX = 16
    private val UNUSABLE = setOf("r", "e")
    private val FAILURE_WORDS = listOf("failed", "error", "cancel", "not found")
    private val ALGORITHMS = mapOf("1" to "rsa", "2" to "rsa", "3" to "rsa", "17" to "dsa")
    private val ESCAPE = Regex("""\\x([0-9a-fA-F]{2})""")
}
