package dev.icode.mobile.data.web

import android.content.Context
import android.os.Build
import dev.icode.mobile.BuildConfig
import dev.icode.mobile.data.runtime.RuntimeLayout
import java.util.Locale

/**
 * Everything iCode ever sends about a device — and only after the user agreed
 * (docs/device-telemetry-plan.md §3). Nothing about workspaces, files, remotes, keys or prompts.
 */
internal data class DeviceFacts(
    val appVersionName: String,
    val appVersionCode: Int,
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val abi: String,
    val locale: String,
    val runtimeInstalled: Boolean,
) {
    /** Registration repeats when any of these change. */
    val fingerprint: String get() = toString().hashCode().toString(16)

    companion object {
        fun read(context: Context) = DeviceFacts(
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            androidRelease = Build.VERSION.RELEASE.orEmpty().take(MAX),
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER.orEmpty().take(MAX),
            model = Build.MODEL.orEmpty().take(MAX),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            locale = Locale.getDefault().toLanguageTag().take(20),
            runtimeInstalled = RuntimeLayout(context).isInstalled,
        )

        private const val MAX = 80
    }
}
