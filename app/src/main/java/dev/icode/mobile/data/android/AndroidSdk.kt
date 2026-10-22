package dev.icode.mobile.data.android

import dev.icode.mobile.data.runtime.RuntimeLayout
import java.io.File

/**
 * Where the Android SDK tool installs, and what (docs/android-toolchain-plan.md §4.2): Android
 * Studio's own location on Linux, in the guest home, so Repair keeps it. One place, read by its
 * setup and by Build & Run.
 */
internal object AndroidSdk {
    const val GUEST_ROOT = "${RuntimeLayout.GUEST_HOME}/Android/Sdk"
    const val PLATFORM = "android-36"
    /** AGP 9's minimum and default; the one build tools whose host programs are replaced by ARM64 builds. */
    const val BUILD_TOOLS = "36.0.0"

    /** What sdkmanager installs, by its own package paths. */
    val PACKAGES = listOf("platforms;$PLATFORM", "build-tools;$BUILD_TOOLS")

    /** The host programs Google ships for x86-64 only, replaced by builds of the same AOSP source for ARM64. */
    val HOST_TOOLS = listOf("aapt", "aapt2", "aidl", "dexdump", "split-select", "zipalign")

    const val LICENCE = "android-sdk-license"

    fun host(layout: RuntimeLayout): File = File(layout.home, GUEST_ROOT.removePrefix("${RuntimeLayout.GUEST_HOME}/"))

    const val GUEST_BUILD_TOOLS = "$GUEST_ROOT/build-tools/$BUILD_TOOLS"
    const val GUEST_SDKMANAGER = "$GUEST_ROOT/cmdline-tools/latest/bin/sdkmanager"

    /** Side by side, as Android Studio installs them (docs/android-ndk-plan.md §3.2): `cmake/<version>`, `ndk/<revision>`. */
    fun cmake(version: String) = "cmake/$version"
    fun ndk(revision: String) = "ndk/$revision"
}
