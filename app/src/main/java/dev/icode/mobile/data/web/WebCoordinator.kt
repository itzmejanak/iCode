package dev.icode.mobile.data.web

import android.content.Context
import dev.icode.mobile.data.notices.NoticeStore
import dev.icode.mobile.data.runtime.ManifestSource
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.DeviceInfoConsent
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ReportDraft
import dev.icode.mobile.domain.ReportSending
import dev.icode.mobile.domain.ReportState
import dev.icode.mobile.domain.UpdateCheck
import dev.icode.mobile.domain.WebIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The website's part of the gateway: Settings › Application, update checks and the consent answer. */
internal class WebCoordinator(
    private val context: Context,
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
) {
    private val prefs = WebPreferences(context)
    private val client = ICodeWebClient()
    private val reporter = DeviceInfoReporter(context.applicationContext, prefs, client)
    private val reports = ReportSender(context.applicationContext, client)
    private val notices = NoticeStore.get(context)

    /** Publishes what is known offline, then refreshes from the site. */
    fun start() {
        stateHolder.update {
            it.copy(
                web = it.web.copy(
                    consent = prefs.consent,
                    links = prefs.cachedLinks,
                    supportText = prefs.cachedSupportText,
                    report = ReportState(draft = prefs.reportDraft),
                    manifest = ManifestSource.origin(context.applicationContext),
                ),
            )
        }
        scope.launch(Dispatchers.IO) {
            refreshConfig()
            refreshFromWebsite()
            if (updateCheckDue()) checkForUpdates()
            reporter.schedule()
        }
    }

    /** iCode came to the foreground: a heartbeat now, so the website's "active now" is current. */
    fun onForeground() {
        scope.launch(Dispatchers.IO) {
            reporter.report()
            if (System.currentTimeMillis() - prefs.lastRefreshAt >= WebPreferences.REFRESH_MILLIS) refreshFromWebsite()
        }
    }

    fun handle(intent: WebIntent) {
        when (intent) {
            WebIntent.RefreshAppConfig -> scope.launch(Dispatchers.IO) { refreshConfig(); refreshFromWebsite() }
            WebIntent.CheckForUpdates -> scope.launch(Dispatchers.IO) { checkForUpdates() }
            is WebIntent.SetDeviceInfoConsent -> scope.launch(Dispatchers.IO) { setConsent(intent.shared) }
            WebIntent.OpenReport -> scope.launch(Dispatchers.IO) { refreshReportPolicy() }
            is WebIntent.EditReport -> {
                prefs.reportDraft = intent.draft
                updateReport { it.copy(draft = intent.draft, sending = if (it.sending is ReportSending.Sending) it.sending else ReportSending.Idle) }
            }
            WebIntent.SendReport -> scope.launch(Dispatchers.IO) { sendReport() }
            WebIntent.NewReport -> {
                prefs.reportDraft = ReportDraft()
                updateReport { it.copy(draft = ReportDraft(), sending = ReportSending.Idle) }
            }
        }
    }

    private fun updateReport(change: (ReportState) -> ReportState) =
        stateHolder.update { it.copy(web = it.web.copy(report = change(it.web.report))) }

    private fun refreshReportPolicy() {
        if (!client.configured) return
        runCatching { client.reportPolicy() }.onSuccess { policy -> updateReport { it.copy(policy = policy) } }
    }

    private fun sendReport() {
        val state = stateHolder.current.web.report
        if (state.sending is ReportSending.Sending) return
        val failed = { reason: String -> updateReport { it.copy(sending = ReportSending.Failed(reason)) } }
        if (!client.configured) return failed("Reports are not available in this build")
        updateReport { it.copy(sending = ReportSending.Sending("Connecting…")) }
        val policy = state.policy ?: runCatching { client.reportPolicy() }.getOrNull()
            ?: return failed("Couldn't reach the iCode website — your report is saved, try again")
        if (!policy.enabled) return failed("Reports are closed right now")
        runCatching {
            reports.send(state.draft, policy) { step -> updateReport { it.copy(sending = ReportSending.Sending(step)) } }
        }.fold(
            onSuccess = { number ->
                prefs.reportDraft = ReportDraft()
                updateReport { it.copy(draft = ReportDraft(), policy = policy, sending = ReportSending.Sent(number)) }
            },
            onFailure = { e ->
                failed((e as? ICodeWebClient.WebException)?.message ?: "Couldn't send — your report is saved, try again")
            },
        )
    }

    private fun refreshConfig() {
        if (!client.configured) return
        runCatching { client.appConfig() }.onSuccess { config ->
            prefs.cachedLinks = config.links
            prefs.cachedSupportText = config.supportText
            prefs.telemetryEnabled = config.telemetryEnabled
            prefs.heartbeatMinutes = config.heartbeatMinutes
            prefs.checkIntervalHours = config.checkIntervalHours
            stateHolder.update { it.copy(web = it.web.copy(links = config.links, supportText = config.supportText)) }
        }
    }

    /** Notices and the runtime manifest: asked for at start, on coming to the foreground, and on request. */
    private suspend fun refreshFromWebsite() {
        if (!client.configured) return
        prefs.lastRefreshAt = System.currentTimeMillis()
        refreshNotices()
        val origin = ManifestSource.refresh(context.applicationContext, client)
        stateHolder.update { it.copy(web = it.web.copy(manifest = origin)) }
    }

    /**
     * The website's notices join the Notifications page as the app's own (docs/notices-plan.md):
     * kept first, so none is lost before the bell listens, then said, so the bell's line unfolds.
     * One already kept, or one the user deleted, is left alone.
     */
    private suspend fun refreshNotices() {
        val fresh = runCatching { client.notices() }.getOrNull()?.filterNot { notices.knows(it.id) } ?: return
        fresh.sortedBy { it.at }.forEach { notice ->
            notices.add(notice)
            stateHolder.emit(ICodeEffect.Notice(notice))
        }
    }

    /** The admin's check interval has passed since the last successful check. */
    private fun updateCheckDue(): Boolean =
        client.configured && System.currentTimeMillis() - prefs.lastUpdateCheckAt >= prefs.checkIntervalHours * 3_600_000L

    private fun checkForUpdates() {
        stateHolder.update { it.copy(web = it.web.copy(update = UpdateCheck.Checking)) }
        val result = if (!client.configured) {
            UpdateCheck.Failed("Updates are not available in this build")
        } else {
            runCatching { client.latestRelease() }.fold(
                onSuccess = { latest ->
                    val release = latest.release
                    if (release == null || !latest.updateAvailable) UpdateCheck.UpToDate
                    else UpdateCheck.Available(release.versionName, release.downloadUrl, release.sha256, latest.updateRequired)
                },
                onFailure = { UpdateCheck.Failed("Couldn't reach the iCode website") },
            )
        }
        if (result !is UpdateCheck.Failed) prefs.lastUpdateCheckAt = System.currentTimeMillis()
        stateHolder.update { it.copy(web = it.web.copy(update = result)) }
    }

    private fun setConsent(shared: Boolean) {
        val previous = prefs.consent
        prefs.consent = if (shared) DeviceInfoConsent.Shared else DeviceInfoConsent.NotShared
        stateHolder.update { it.copy(web = it.web.copy(consent = prefs.consent)) }
        if (shared) reporter.report() else if (previous == DeviceInfoConsent.Shared) reporter.forget()
        reporter.schedule()
    }
}
