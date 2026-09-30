package dev.icode.mobile.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent

/**
 * Android's own prompts, pickers and pages: what iCode asks to be allowed, and Android's Files app.
 *
 * They need the activity, so they belong to the composition root. Every prompt
 * reports back when it closes, whatever the answer; the gateway then reads
 * what Android allows now.
 */
class SystemPrompts internal constructor(
    private val context: Context,
    private val answered: (SystemPermission) -> Unit,
    private val notificationDialog: ActivityResultLauncher<String>,
    private val notificationPage: ActivityResultLauncher<Intent>,
    private val batteryDialog: ActivityResultLauncher<Intent>,
) {
    /** Shows Android's prompt for [permission]. */
    // The battery prompt is the owner's decision; Google Play limits it to
    // listed use cases (docs/terminal-background-plan.md §7).
    @SuppressLint("BatteryLife")
    fun request(permission: SystemPermission) {
        when (permission) {
            // Android 13 introduced the dialog; before it notifications are
            // on unless the user switched them off on the settings page.
            SystemPermission.Notifications ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationDialog.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    launch(notificationPage, permission, notificationSettings())
                }
            SystemPermission.Battery -> launch(
                batteryDialog, permission,
                appPage(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        }
    }

    /** Opens Android's settings page for [permission]; the change is read on return. */
    fun openSettings(permission: SystemPermission) {
        val page = when (permission) {
            SystemPermission.Notifications -> notificationSettings()
            SystemPermission.Battery -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        startFirst(page, appPage(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
    }

    /** Android's Files app at the workspace's folder; at iCode's root there when that cannot be opened. */
    fun showInFiles(effect: WorkbenchEffect.ShowInFiles) {
        val opened = startFirst(
            Intent(Intent.ACTION_VIEW).setDataAndType(effect.documentUri.toUri(), DocumentsContract.Document.MIME_TYPE_DIR),
            Intent(Intent.ACTION_VIEW).setDataAndType(effect.rootUri.toUri(), DocumentsContract.Root.MIME_TYPE_ITEM),
        )
        if (!opened) Toast.makeText(context, "No Files app is available", Toast.LENGTH_LONG).show()
    }

    /** Starts the first of [pages] this phone has; false when it has none. */
    private fun startFirst(vararg pages: Intent): Boolean = pages.any { page ->
        try {
            context.startActivity(page)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Tries each page in turn; with none available the prompt counts as answered, so nothing waits on it. */
    private fun launch(launcher: ActivityResultLauncher<Intent>, permission: SystemPermission, vararg pages: Intent) {
        for (page in pages) {
            try {
                launcher.launch(page)
                return
            } catch (_: ActivityNotFoundException) {
                // A phone without this page; try the next.
            }
        }
        answered(permission)
    }

    private fun appPage(action: String) = Intent(action, Uri.fromParts("package", context.packageName, null))

    private fun notificationSettings() =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}

@Composable
fun rememberSystemPrompts(gateway: WorkbenchGateway): SystemPrompts {
    val context = LocalContext.current
    fun answered(permission: SystemPermission) = gateway.dispatch(WorkbenchIntent.SystemPermissionAnswered(permission))
    val notificationDialog = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        answered(SystemPermission.Notifications)
    }
    val notificationPage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        answered(SystemPermission.Notifications)
    }
    val batteryDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        answered(SystemPermission.Battery)
    }
    return remember(context, gateway, notificationDialog, notificationPage, batteryDialog) {
        SystemPrompts(context, ::answered, notificationDialog, notificationPage, batteryDialog)
    }
}

/**
 * Import's first step: Android's folder picker. The folder it returns is read for this import
 * only (docs/workspace-storage-plan.md §4.5); closing it without a choice changes nothing.
 */
@Composable
fun rememberImportPicker(): () -> Unit {
    val gateway = LocalWorkbenchGateway.current
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree?.let { gateway.dispatch(WorkbenchIntent.ChooseImportSource(it.toString())) }
    }
    return remember(picker, context) {
        {
            try {
                picker.launch(null)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "This phone has no folder picker", Toast.LENGTH_LONG).show()
            }
        }
    }
}
