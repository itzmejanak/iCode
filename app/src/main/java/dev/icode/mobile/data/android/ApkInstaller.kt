package dev.icode.mobile.data.android

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Installs an APK on this phone and opens it (docs/runtime-and-terminal.md): one
 * [PackageInstaller] session, its result to a receiver that only this app can reach; Android asks
 * the user to confirm, and says why when it refuses.
 */
internal class ApkInstaller(context: Context) {
    private val app = context.applicationContext

    /**
     * Android's confirmation of the install in hand. Android does not let an app in the background
     * open it, so it is kept for [confirm] to open again from iCode's screen.
     */
    @Volatile private var confirmation: Intent? = null

    /** Opens Android's confirmation of the install in hand again. */
    fun confirm() {
        confirmation?.let { app.startActivity(it) }
    }

    /** Installs [apk], answering once Android has; throws, in Android's words, when it does not. */
    suspend fun install(apk: File) {
        val installer = app.packageManager.packageInstaller
        val id = withContext(Dispatchers.IO) {
            installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)).also { id ->
                installer.openSession(id).use { session ->
                    session.openWrite(apk.name, 0, apk.length()).use { output ->
                        apk.inputStream().use { it.copyTo(output) }
                        session.fsync(output)
                    }
                }
            }
        }
        val action = "${app.packageName}.APK_INSTALLED.$id"
        val refused = suspendCancellableCoroutine<String?> { waiting ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        // Android's own confirmation: the user installs, or cancels.
                        confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        confirm()
                        return
                    }
                    confirmation = null
                    app.unregisterReceiver(this)
                    waiting.resume(if (status == PackageInstaller.STATUS_SUCCESS) null else failure(status, intent))
                }
            }
            ContextCompat.registerReceiver(app, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            fun abandon() {
                confirmation = null
                runCatching { app.unregisterReceiver(receiver) }
                runCatching { installer.abandonSession(id) }
            }
            waiting.invokeOnCancellation { abandon() }
            // Android adds the outcome to it, so it must be mutable; only this app's receiver can take it.
            val result = PendingIntent.getBroadcast(
                app, id, Intent(action).setPackage(app.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            try {
                installer.openSession(id).use { it.commit(result.intentSender) }
            } catch (refused: Exception) {
                abandon()
                throw refused
            }
        }
        refused?.let { throw IOException(it) }
    }

    /** Opens [packageName]'s launcher activity, as the home screen does. */
    fun open(packageName: String) {
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(launch)
        } catch (_: ActivityNotFoundException) {
            throw IOException("$packageName is installed, but has no activity to open from the home screen")
        }
    }

    private fun failure(status: Int, intent: Intent): String {
        val said = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        return when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "The install was cancelled"
            // Most often the debug key: another build of this app, signed by another key, is installed.
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "Android refused the update: the installed app was signed by another key. Uninstall it, then Build & Run again"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "The phone has no room to install the app"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "The app does not run on this phone"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install"
            else -> "Android did not install the app"
        } + said?.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()
    }
}
