package dev.icode.mobile.domain

/** A new remote's name and address, checked the same way by the form and the backend. */
object GitRemoteRules {
    private val NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")
    /** `scheme://account@` — on a web address, where Git would keep a token in plain text. */
    private val WEB_ACCOUNT = Regex("^https?://[^/@]*@", RegexOption.IGNORE_CASE)

    fun nameError(name: String): String? = when {
        name.isBlank() -> "Enter a name for the remote"
        !NAME.matches(name) || ".." in name || name.endsWith(".lock") ->
            "Use letters, digits, dots, dashes and underscores"
        else -> null
    }

    fun urlError(url: String): String? = when {
        url.isBlank() -> "Enter the repository address"
        url.any { it.isWhitespace() || it.isISOControl() } -> "An address has no spaces"
        WEB_ACCOUNT.containsMatchIn(url) ->
            "Leave the account out of the address; save a token in Git credentials instead"
        else -> null
    }
}
