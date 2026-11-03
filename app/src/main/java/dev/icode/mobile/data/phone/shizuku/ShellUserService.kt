package dev.icode.mobile.data.phone.shizuku

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Parcel
import androidx.annotation.Keep
import dev.icode.mobile.data.phone.PhoneCapture
import dev.icode.mobile.domain.phone.PhoneShot
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * iCode's own code in a shell process (docs/phone-agent-plan.md §5.11): Shizuku starts it by this
 * class's name, with the shell's identity, while iCode is bound to it. It answers a command line —
 * run by `sh`, its exit code and output — a picture of the screen, and Shizuku's reserved destroy
 * call. No AIDL: the transactions are written by hand, here and in [ShizukuShell].
 */
class ShellUserService() : Binder() {
    /** Shizuku 13 hands the service a Context; it needs none. */
    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = when (code) {
        RUN -> {
            data.enforceInterface(DESCRIPTOR)
            val (exit, output, error) = run(data.readString().orEmpty())
            reply?.writeNoException()
            reply?.writeInt(exit)
            reply?.writeByteArray(output)
            reply?.writeString(error)
            true
        }
        SCREENSHOT -> {
            data.enforceInterface(DESCRIPTOR)
            val shot = screenshot()
            reply?.writeNoException()
            reply?.writeByteArray(shot?.jpeg)
            reply?.writeInt(shot?.width ?: 0)
            reply?.writeInt(shot?.height ?: 0)
            reply?.writeFloat(shot?.scale ?: 0f)
            true
        }
        DESTROY -> exitProcess(0)
        else -> super.onTransact(code, data, reply, flags)
    }

    /** [command] run by `sh`: its exit code, its output up to [max] bytes, and the start of its errors. */
    private fun run(command: String, max: Int = MAX_OUTPUT): Triple<Int, ByteArray, String> {
        val process = ProcessBuilder("sh", "-c", command).start()
        val output = process.inputStream.use { input ->
            // Bounded by hand: InputStream.readNBytes is Android 13's.
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(CHUNK)
            while (buffer.size() < max) {
                val read = input.read(chunk, 0, minOf(CHUNK, max - buffer.size()))
                if (read < 0) break
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }
        val error = process.errorStream.bufferedReader().use { it.readText().take(MAX_ERROR) }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
        return Triple(runCatching { process.exitValue() }.getOrDefault(-1), output, error)
    }

    /** The screen as `screencap` sees it, made small enough for a transaction, as the model is sent one. */
    private fun screenshot(): PhoneShot? {
        val png = run("screencap -p", MAX_PNG).takeIf { it.first == 0 }?.second ?: return null
        return BitmapFactory.decodeByteArray(png, 0, png.size)?.let(PhoneCapture::shrink)
    }

    companion object {
        const val DESCRIPTOR = "dev.icode.mobile.shell"
        const val RUN = FIRST_CALL_TRANSACTION
        const val SCREENSHOT = FIRST_CALL_TRANSACTION + 1

        /** Shizuku's own code for destroy, outside AIDL's numbering (its UserService documentation). */
        const val DESTROY = 16777115

        /** Output kept at most: a transaction holds about a megabyte. */
        private const val MAX_OUTPUT = 512 * 1024
        /** A screen's PNG, read whole before it is made small: a 4K screen's is under this. */
        private const val MAX_PNG = 64 * 1024 * 1024
        private const val MAX_ERROR = 4_000
        private const val CHUNK = 8192
        private const val TIMEOUT_SECONDS = 20L
    }
}
