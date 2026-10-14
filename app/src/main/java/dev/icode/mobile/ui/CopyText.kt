package dev.icode.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast

/** [text] to the clipboard; below Android 13, which says so itself, a toast says [said]. */
internal fun copyText(context: Context, label: String, text: String, said: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
}
