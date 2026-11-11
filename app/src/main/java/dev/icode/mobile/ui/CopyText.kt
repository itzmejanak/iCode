package dev.icode.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.notices.SettingsNotices

/** [text] to the clipboard; below Android 13, which says so itself, the bell says [said] (docs/notices-plan.md §2.6). */
internal fun copyText(context: Context, label: String, text: String, said: String, dispatch: (ICodeIntent) -> Unit) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) dispatch(NoticeIntent.Tell(SettingsNotices.copied(said)))
}
