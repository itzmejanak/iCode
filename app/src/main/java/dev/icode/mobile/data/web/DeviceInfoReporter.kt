package dev.icode.mobile.data.web

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.DeviceInfoConsent
import java.util.concurrent.TimeUnit

/**
 * Sends device info while — and only while — the user shares it (PRIVACY.md):
 * a registration when the facts change, a heartbeat otherwise. Silent on failure: the next run tries again.
 */
internal class DeviceInfoReporter(
    private val context: Context,
    private val prefs: WebPreferences = WebPreferences(context),
    private val client: ICodeWebClient = ICodeWebClient(),
) {
    private val sharing: Boolean
        get() = client.configured && prefs.consent == DeviceInfoConsent.Shared && prefs.telemetryEnabled

    /** One report. Blocking. */
    fun report() {
        if (!sharing) return
        val installId = prefs.installId ?: prefs.newInstallId()
        val facts = DeviceFacts.read(context)
        runCatching {
            val known = prefs.registeredFingerprint == facts.fingerprint && client.heartbeat(installId, facts)
            if (!known) {
                client.register(installId, facts)
                prefs.registeredFingerprint = facts.fingerprint
            }
        }.onFailure { BackendLog.d("web", "Device report skipped: ${it.message}") }
    }

    /** Asks the server to delete this install's record and forgets the ID. Blocking. */
    fun forget() {
        val installId = prefs.installId ?: return
        runCatching { if (client.configured) client.forget(installId) }
            .onFailure { BackendLog.d("web", "Device record removal failed: ${it.message}") }
        prefs.clearInstallId()
    }

    /** Starts or stops the periodic heartbeat to match the user's answer and the server's switch. */
    fun schedule() {
        val work = WorkManager.getInstance(context)
        if (!sharing) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DeviceHeartbeatWorker>(prefs.heartbeatMinutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "icode-device-heartbeat"
    }
}

/** WorkManager's entry point for the periodic heartbeat. */
internal class DeviceHeartbeatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        DeviceInfoReporter(applicationContext).report()
        return Result.success()
    }
}
