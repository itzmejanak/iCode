package dev.icode.mobile.data.web

import android.content.Context
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.DeviceInfoConsent
import dev.icode.mobile.domain.UpdateCheck
import dev.icode.mobile.domain.WebIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The website's part of the gateway: Settings › Application, update checks and the consent answer. */
internal class WebCoordinator(
    context: Context,
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
) {
    private val prefs = WebPreferences(context)
    private val client = ICodeWebClient()
    private val reporter = DeviceInfoReporter(context.applicationContext, prefs, client)

    /** Publishes what is known offline, then refreshes from the site. */
    fun start() {
        stateHolder.update {
            it.copy(web = it.web.copy(consent = prefs.consent, links = prefs.cachedLinks, supportText = prefs.cachedSupportText))
        }
        scope.launch(Dispatchers.IO) {
            refreshConfig()
            refreshNotices()
            if (updateCheckDue()) checkForUpdates()
            reporter.schedule()
        }
    }

    /** iCode came to the foreground: a heartbeat now, so the website's "active now" is current. */
    fun onForeground() {
        scope.launch(Dispatchers.IO) { reporter.report() }
    }

    fun handle(intent: WebIntent) {
        when (intent) {
            WebIntent.RefreshAppConfig -> scope.launch(Dispatchers.IO) { refreshConfig(); refreshNotices() }
            is WebIntent.DismissNotice -> {
                prefs.dismissNotice(intent.id)
                stateHolder.update { it.copy(web = it.web.copy(notices = it.web.notices.filterNot { n -> n.id == intent.id })) }
            }
            WebIntent.CheckForUpdates -> scope.launch(Dispatchers.IO) { checkForUpdates() }
            is WebIntent.SetDeviceInfoConsent -> scope.launch(Dispatchers.IO) { setConsent(intent.shared) }
        }
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

    private fun refreshNotices() {
        if (!client.configured) return
        runCatching { client.notices() }.onSuccess { notices ->
            val dismissed = prefs.dismissedNotices
            stateHolder.update { it.copy(web = it.web.copy(notices = notices.filterNot { n -> n.id in dismissed })) }
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
