package dev.icode.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.domain.ItemClipboard

/** Single owner for every file's actions — Explorer + Workspaces file explorer (E1). No duplication. */
object FileItemActions {
    fun pasteLabel(clipboard: ItemClipboard?): String = clipboard?.let { "Paste ${it.name}" } ?: "Paste"

    fun copyPathAction(context: Context, runtimePath: String) = IcodeMenuAction("Copy path", Icons.Outlined.Link) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Path", runtimePath))
        Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
    }
}
