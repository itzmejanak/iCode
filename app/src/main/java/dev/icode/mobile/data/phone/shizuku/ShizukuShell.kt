package dev.icode.mobile.data.phone.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import dev.icode.mobile.BuildConfig
import dev.icode.mobile.domain.phone.PhoneShot
import dev.icode.mobile.domain.phone.ShizukuState
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Shizuku, as iCode uses it (docs/phone-agent-plan.md §5.11): its state, read live; the user's
 * permission, asked once; and the shell process iCode's [ShellUserService] runs in, bound when first
 * needed and kept while iCode runs.
 */
internal class ShizukuShell(private val context: Context) {
    /** What a command left: its exit code, its output, and the start of its errors. */
    class Result(val exit: Int, val output: ByteArray, val error: String) {
        val ok: Boolean get() = exit == 0
        val text: String get() = output.toString(Charsets.UTF_8)
    }

    private val lock = Mutex()
    @Volatile private var binder: IBinder? = null

    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellUserService::class.java.name))
        .daemon(false)
        .processNameSuffix("shell")
        // A stable tag: R8 renames the class, and Shizuku would take it for another service.
        .tag("icode-shell")
        .version(BuildConfig.VERSION_CODE)
        .debuggable(BuildConfig.DEBUG)

    /** Shizuku as it is now; Sui (Shizuku inside a root manager) answers as running without the app. */
    fun state(): ShizukuState {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return if (installed()) ShizukuState.NotRunning else ShizukuState.NotInstalled
        }
        if (runCatching { Shizuku.isPreV11() || Shizuku.getVersion() < MIN_VERSION }.getOrDefault(true)) return ShizukuState.TooOld
        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) ShizukuState.Ready else ShizukuState.NotAllowed
    }

    /** Asks the user to allow iCode in Shizuku; [answered] hears whether they did. */
    fun requestPermission(answered: (Boolean) -> Unit) {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST) return
                Shizuku.removeRequestPermissionResultListener(this)
                answered(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(REQUEST)
    }

    /** [command] run by the shell; null when Shizuku is not ready. */
    suspend fun run(command: String): Result? = call(ShellUserService.RUN, { writeString(command) }) { reply ->
        Result(reply.readInt(), reply.createByteArray() ?: ByteArray(0), reply.readString().orEmpty())
    }

    /** The screen as the shell captures it, ready for the model; null when Shizuku is not ready or capture failed. */
    suspend fun screenshot(): PhoneShot? = call(ShellUserService.SCREENSHOT, {}) { reply ->
        val jpeg = reply.createByteArray()
        val width = reply.readInt()
        val height = reply.readInt()
        val scale = reply.readFloat()
        jpeg?.let { PhoneShot(it, width, height, scale) }
    }

    /** Turns iCode's accessibility service on, restricted settings allowed first: one tap instead of Android's walk (§5.9). */
    suspend fun turnOnService(component: String): Boolean {
        // Android 13's guard on apps installed from a file; older versions have no such op, and refuse it.
        run("appops set ${context.packageName} ACCESS_RESTRICTED_SETTINGS allow")
        val enabled = run("settings get secure enabled_accessibility_services")?.takeIf { it.ok }?.text?.trim() ?: return false
        val services = enabled.split(':').filter { it.isNotBlank() && it != "null" }
        val list = (services.filterNot { it.equals(component, ignoreCase = true) } + component).joinToString(":")
        return run("settings put secure enabled_accessibility_services '$list' && settings put secure accessibility_enabled 1")?.ok == true
    }

    /** Taps, swipes and keys as the shell's `input`, where accessibility is refused. */
    suspend fun input(arguments: String): Boolean = run("input $arguments")?.ok == true

    private suspend fun <T> call(code: Int, write: Parcel.() -> Unit, read: (Parcel) -> T): T? {
        if (state() != ShizukuState.Ready) return null
        val service = bound() ?: return null
        return withContext(Dispatchers.IO) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(ShellUserService.DESCRIPTOR)
                data.write()
                service.transact(code, data, reply, 0)
                reply.readException()
                read(reply)
            } catch (failure: Exception) {
                // The shell process went away: bound again next time.
                binder = null
                null
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    private suspend fun bound(): IBinder? = lock.withLock {
        binder?.takeIf { it.pingBinder() }?.let { return@withLock it }
        withTimeoutOrNull(BIND_MILLIS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { waiting ->
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            binder = service
                            if (waiting.isActive) waiting.resume(service)
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            binder = null
                        }
                    }
                    Shizuku.bindUserService(args, connection)
                }
            }
        }
    }

    private fun installed(): Boolean =
        runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

        /** Shizuku's UserService is stable from API 11 (its own README). */
        private const val MIN_VERSION = 11
        private const val REQUEST = 0x1c0d
        private const val BIND_MILLIS = 10_000L
    }
}
