package dev.icode.mobile.domain

/**
 * A Git hosting provider.
 *
 * Choosing one up front lets the screen show a single relevant action instead
 * of every host's button at once. [Other] covers self-hosted and anything not
 * listed, where the app can only offer the generic fields.
 */
enum class GitProvider(
    val displayName: String,
    val host: String,
    /** Where this provider accepts a new SSH public key. */
    val sshKeyUrl: String?,
    /** Where this provider issues personal access tokens. */
    val tokenUrl: String?,
    /** Where this provider accepts a new GPG public key, for signed commits; null where iCode only offers Copy. */
    val gpgKeyUrl: String?,
) {
    GitHub(
        displayName = "GitHub",
        host = "github.com",
        sshKeyUrl = "https://github.com/settings/ssh/new",
        tokenUrl = "https://github.com/settings/tokens",
        gpgKeyUrl = "https://github.com/settings/gpg/new",
    ),
    GitLab(
        displayName = "GitLab",
        host = "gitlab.com",
        sshKeyUrl = "https://gitlab.com/-/user_settings/ssh_keys",
        tokenUrl = "https://gitlab.com/-/user_settings/personal_access_tokens",
        gpgKeyUrl = "https://gitlab.com/-/user_settings/gpg_keys",
    ),
    Bitbucket(
        displayName = "Bitbucket",
        host = "bitbucket.org",
        sshKeyUrl = "https://bitbucket.org/account/settings/ssh-keys/",
        tokenUrl = "https://bitbucket.org/account/settings/app-passwords/",
        gpgKeyUrl = null,
    ),
    Other(
        displayName = "Other / self-hosted",
        host = "",
        sshKeyUrl = null,
        tokenUrl = null,
        gpgKeyUrl = null,
    ),
}
