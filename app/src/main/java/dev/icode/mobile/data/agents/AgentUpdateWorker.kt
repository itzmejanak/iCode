package dev.icode.mobile.data.agents

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.icode.mobile.data.notices.NoticeNotifications
import dev.icode.mobile.data.notices.NoticeStore
import dev.icode.mobile.data.notify.IcodeNotifications
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.domain.CodingAgents
import dev.icode.mobile.domain.notices.AgentNotices
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The daily check for newer releases of the installed agents (docs/assistant-and-mcp.md): with none
 * installed it reads nothing. Newer ones are told once, as a notice the shade shows. iCode may not be
 * running, so it reaches the notice store and the shade directly, as
 * [dev.icode.mobile.data.notices.NoticeSwipedReceiver] reaches the store. Nothing is downloaded: an
 * update is the user's tap.
 */
internal class AgentUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val installed = AgentReceipts(RuntimeLayout(context).home).installed()
        if (installed.isEmpty()) return Result.success()
        // Unreachable today: tomorrow's check tries again.
        val catalogue = runCatching { AgentRegistry(kept(context)).read() }.getOrElse { return Result.success() }
        val updates = CodingAgents.updates(catalogue, installed)
        if (updates.isEmpty()) return Result.success()
        val notice = AgentNotices.updates(updates).copy(at = System.currentTimeMillis())
        val store = NoticeStore.get(context)
        if (store.knows(notice.id)) return Result.success()
        store.add(notice)
        NoticeNotifications(context, IcodeNotifications(context)).show(store.notices.value, screenVisible = false)
        return Result.success()
    }

    companion object {
        private const val NAME = "icode-agent-updates"

        /** The registry's list, last read, in iCode's storage. */
        fun kept(context: Context) = File(context.filesDir, "agents/registry.json")

        /** Once a day while the phone has a connection; scheduled once, kept across starts. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AgentUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
