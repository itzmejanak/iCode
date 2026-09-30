package dev.icode.mobile.data.system

import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import androidx.core.content.edit
import dev.icode.mobile.domain.SystemAccess
import dev.icode.mobile.domain.SystemPermission

/**
 * Reads what Android allows iCode, and remembers which optional permissions
 * onboarding has offered.
 *
 * The permissions themselves are Android's to keep; only the "offered" marks
 * are stored here, so a refusal is not asked again on every launch.
 */
class AndroidSystemAccess(context: Context) {
    private val app = context.applicationContext
    private val offered = app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): SystemAccess = SystemAccess(
        // Android's documented check; below Android 13 there is no runtime
        // permission and this reports whether the user switched them off.
        notifications = app.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
        unrestrictedBattery = app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName),
        offered = SystemPermission.entries.filterTo(mutableSetOf()) { offered.getBoolean(offeredKey(it), false) },
    )

    fun markOffered(permission: SystemPermission) {
        offered.edit { putBoolean(offeredKey(permission), true) }
    }

    /** Keys are stored; do not rename. */
    private fun offeredKey(permission: SystemPermission): String = when (permission) {
        SystemPermission.Notifications -> "offered.notifications"
        SystemPermission.Battery -> "offered.battery"
    }

    private companion object {
        const val PREFERENCES_NAME = "onboarding"
    }
}
