package dev.icode.mobile.domain

/**
 * What iCode reads from, and sends to, the iCode website (contracts/api-v1.md in icode-web).
 * Every part is optional: offline, or with the site down, nothing here blocks local work
 * (docs/user-stories.md US-WEB-003).
 */

/** The user's answer to sharing anonymous device info (docs/device-telemetry-plan.md). */
enum class DeviceInfoConsent { Unanswered, Shared, NotShared }

/** Settings › Application's links, as the website's admin set them; null where none is set. */
data class AppLinks(
    val website: String? = null,
    val documentation: String? = null,
    val releaseNotes: String? = null,
    val reportIssue: String? = null,
    val source: String? = null,
    val licenses: String? = null,
    val privacy: String? = null,
)

enum class NoticeLevel { Info, Warning, Critical }

/** An announcement the website's admin published for this version (Admin › Notices). */
data class Notice(val id: String, val title: String, val body: String, val level: NoticeLevel)

sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    /** [required]: this build is below the website's minimum supported version. */
    data class Available(val versionName: String, val downloadUrl: String, val sha256: String, val required: Boolean) : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}

data class WebState(
    val consent: DeviceInfoConsent = DeviceInfoConsent.Unanswered,
    /** Null until the website answered once (or a cached answer was read). */
    val links: AppLinks? = null,
    val supportText: String = "",
    val update: UpdateCheck = UpdateCheck.Idle,
    /** Active notices the user hasn't dismissed. */
    val notices: List<Notice> = emptyList(),
) {
    /** Onboarding still has to ask. */
    val consentPending: Boolean get() = consent == DeviceInfoConsent.Unanswered
}

sealed interface WebIntent : ICodeIntent {
    /** Reads Settings › Application's links and policy; cheap, cached by ETag. */
    data object RefreshAppConfig : WebIntent
    data object CheckForUpdates : WebIntent
    /** Hides a notice on this phone for good. */
    data class DismissNotice(val id: String) : WebIntent
    /** Onboarding's answer or Settings' toggle. Turning it off deletes the server's record. */
    data class SetDeviceInfoConsent(val shared: Boolean) : WebIntent
}
