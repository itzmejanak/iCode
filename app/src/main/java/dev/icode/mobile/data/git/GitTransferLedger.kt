package dev.icode.mobile.data.git

import android.content.Context
import androidx.core.content.edit
import dev.icode.mobile.domain.AppNotice

/** A transfer in flight, written down so an ending nobody saw can still be told. */
internal interface TransferLedger {
    fun started(label: String, target: String)
    fun finished()
}

/**
 * Written when a transfer starts and cleared when it ends, however it ends.
 * Android may end iCode's process with nothing run after it; an entry still
 * here at the next start is a transfer Git never finished.
 */
internal class GitTransferLedger(context: Context) : TransferLedger {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    // Committed at once: the process may be gone before an apply() reaches the disk.
    override fun started(label: String, target: String) = preferences.edit(commit = true) {
        putString(KEY_LABEL, label)
        putString(KEY_TARGET, target)
        putLong(KEY_AT, System.currentTimeMillis())
    }

    override fun finished() = preferences.edit(commit = true) { clear() }

    /** The transfer the previous process never finished, told once; call before any transfer can start. */
    fun takeInterrupted(): AppNotice.GitTransferInterrupted? {
        val label = preferences.getString(KEY_LABEL, null) ?: return null
        val target = preferences.getString(KEY_TARGET, null).orEmpty()
        val at = preferences.getLong(KEY_AT, 0L)
        finished()
        return AppNotice.GitTransferInterrupted(id = "git-transfer-$at", at = at, read = false, label = label, target = target)
    }

    private companion object {
        // Stored keys; do not rename.
        const val PREFERENCES_NAME = "git_transfer"
        const val KEY_LABEL = "label"
        const val KEY_TARGET = "target"
        const val KEY_AT = "at"
    }
}
