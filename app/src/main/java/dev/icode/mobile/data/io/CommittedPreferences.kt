package dev.icode.mobile.data.io

import android.annotation.SuppressLint
import android.content.SharedPreferences

/**
 * Writes [changes] to disk now, and a failed write is an error with [failure] as
 * its message — for a value the user is then told was saved. The KTX `edit {}`
 * writes in the background, and `edit(commit = true) {}` discards commit()'s
 * answer: with either, a full or failing disk would pass as saved.
 */
@SuppressLint("UseKtx")
internal fun SharedPreferences.commitOrFail(failure: String, changes: SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    editor.changes()
    check(editor.commit()) { failure }
}
