package dev.icode.mobile.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent

/**
 * Android's own prompts and pages: what iCode asks to be allowed, and Android's Files app.
 *
 * They need the activity, so they belong to the composition root. Every prompt
 * reports back when it closes, whatever the answer; the gateway then reads
 * what Android allows now.
 */
class SystemPrompts internal constructor(
    private val context: Context,
    private val answered: (SystemPermission) -> Unit,
    private val storagePage: ActivityResultLauncher<Intent>,
    private val notificationDialog: ActivityResultLauncher<String>,
    private val notificationPage: ActivityResultLauncher<Intent>,
    private val batteryDialog: ActivityResultLauncher<Intent>,
    private val installPage: ActivityResultLauncher<Intent>,
) {
    /** Shows Android's prompt for [permission]. */
    // The battery prompt is the owner's decision; Google Play limits it to
    // listed use cases (docs/terminal-background-plan.md §7).
    @SuppressLint("BatteryLife")
    fun request(permission: SystemPermission) {
        when (permission) {
            // All Files Access has no dialog: Android's page for this app, else its list of apps.
            SystemPermission.Storage ->
                if (Environment.isExternalStorageManager()) answered(permission)
                else launch(
                    storagePage, permission,
                    appPage(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION),
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                )
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
            // Install unknown apps has no dialog either: Android's page for this app.
            SystemPermission.InstallApps ->
                if (context.packageManager.canRequestPackageInstalls()) answered(permission)
                else launch(installPage, permission, appPage(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
        }
    }

    /** Opens Android's settings page for [permission]; the change is read on return. */
    fun openSettings(permission: SystemPermission) {
        val page = when (permission) {
            SystemPermission.Storage -> appPage(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            SystemPermission.Notifications -> notificationSettings()
            SystemPermission.Battery -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            SystemPermission.InstallApps -> appPage(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        }
        startFirst(page, appPage(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
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
fun rememberSystemPrompts(gateway: ICodeGateway): SystemPrompts {
    val context = LocalContext.current
    fun answered(permission: SystemPermission) = gateway.dispatch(ICodeIntent.SystemPermissionAnswered(permission))
    val storagePage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        answered(SystemPermission.Storage)
    }
    val notificationDialog = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        answered(SystemPermission.Notifications)
    }
    val notificationPage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        answered(SystemPermission.Notifications)
    }
    val batteryDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        answered(SystemPermission.Battery)
    }
    val installPage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        answered(SystemPermission.InstallApps)
    }
    return remember(context, gateway, storagePage, notificationDialog, notificationPage, batteryDialog, installPage) {
        SystemPrompts(context, ::answered, storagePage, notificationDialog, notificationPage, batteryDialog, installPage)
    }
}
