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

/**
 * Which runtime manifest this phone uses (contracts/api-v1.md § Runtime): the website's published
 * one, [revision], when it has one that passes the app's checks; otherwise the one built into iCode.
 */
data class ManifestOrigin(val fromWebsite: Boolean = false, val revision: Int? = null)

sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    /** [required]: this build is below the website's minimum supported version. */
    data class Available(val versionName: String, val downloadUrl: String, val sha256: String, val required: Boolean) : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}

enum class ReportKind(val wire: String, val label: String) { Bug("bug", "Bug"), Issue("issue", "Issue"), Suggestion("suggestion", "Suggestion") }

/**
 * What the Report a Bug sheet holds (Settings › About iCode). [attachments] are content URIs the
 * photo picker granted; device info is attached only when [attachDevice] — never workspace content.
 */
data class ReportDraft(
    val kind: ReportKind = ReportKind.Bug,
    val title: String = "",
    val description: String = "",
    val email: String = "",
    val attachments: List<String> = emptyList(),
    val attachDevice: Boolean = false,
)

/** The website's answer about reports (GET /api/v1/reports/config); null until read. */
data class ReportPolicy(val enabled: Boolean, val mediaEnabled: Boolean, val maxFiles: Int, val maxImageBytes: Long, val maxVideoBytes: Long)

sealed interface ReportSending {
    data object Idle : ReportSending
    data class Sending(val step: String) : ReportSending
    data class Sent(val number: Int) : ReportSending
    /** The draft stays as typed, so Send tries again. */
    data class Failed(val reason: String) : ReportSending
}

data class ReportState(
    val draft: ReportDraft = ReportDraft(),
    val policy: ReportPolicy? = null,
    val sending: ReportSending = ReportSending.Idle,
)

data class WebState(
    val consent: DeviceInfoConsent = DeviceInfoConsent.Unanswered,
    /** Null until the website answered once (or a cached answer was read). */
    val links: AppLinks? = null,
    val supportText: String = "",
    val update: UpdateCheck = UpdateCheck.Idle,
    /** The runtime manifest in use: the website's override, or the built-in one. */
    val manifest: ManifestOrigin = ManifestOrigin(),
    val report: ReportState = ReportState(),
) {
    /** Onboarding still has to ask. */
    val consentPending: Boolean get() = consent == DeviceInfoConsent.Unanswered
}

sealed interface WebIntent : ICodeIntent {
    /** Reads Settings › Application's links and policy; cheap, cached by ETag. */
    data object RefreshAppConfig : WebIntent
    data object CheckForUpdates : WebIntent
    /** Onboarding's answer or Settings' toggle. Turning it off deletes the server's record. */
    data class SetDeviceInfoConsent(val shared: Boolean) : WebIntent
    /** The Report a Bug sheet opened: read the website's report policy. */
    data object OpenReport : WebIntent
    /** Every edit of the sheet; the draft is kept on the phone until it is sent. */
    data class EditReport(val draft: ReportDraft) : WebIntent
    data object SendReport : WebIntent
    /** After Sent: an empty draft for the next one. */
    data object NewReport : WebIntent
}
