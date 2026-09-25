package dev.icode.mobile.data.log

import android.util.Log
import dev.icode.mobile.BuildConfig

/**
 * The backend's one way to write to Android's log.
 *
 * Tags are `iCode/<subsystem>`. Debug and info lines are written by debug
 * builds only; warnings and errors always. Every line passes [credentialSafe]
 * first — a Git remote URL can carry a token, and a log is not a safe place for one.
 */
object BackendLog {
    /** Where lines go; a unit test replaces it, since `android.util.Log` exists only on a device. */
    internal var writer: (priority: Int, tag: String, message: String) -> Unit =
        { priority, tag, message -> Log.println(priority, tag, message) }

    fun d(subsystem: String, message: String) {
        if (BuildConfig.DEBUG) write(Log.DEBUG, subsystem, message, null)
    }

    fun i(subsystem: String, message: String) {
        if (BuildConfig.DEBUG) write(Log.INFO, subsystem, message, null)
    }

    fun w(subsystem: String, message: String, error: Throwable? = null) = write(Log.WARN, subsystem, message, error)

    fun e(subsystem: String, message: String, error: Throwable? = null) = write(Log.ERROR, subsystem, message, error)

    /**
     * Masks the user and token of a URL (`user:token@` becomes `***@`); an SSH
     * address such as `git@host:repo` has no secret in it and stays as it is.
     */
    fun credentialSafe(text: String): String = CREDENTIALS_IN_URL.replace(text, "$1***@")

    private fun write(priority: Int, subsystem: String, message: String, error: Throwable?) {
        val text = if (error == null) message else "$message\n${error.stackTraceToString()}"
        writer(priority, "iCode/$subsystem", credentialSafe(text))
    }

    private val CREDENTIALS_IN_URL = Regex("""\b([a-zA-Z][a-zA-Z0-9+.-]*://)[^/\s@]+@""")
}
