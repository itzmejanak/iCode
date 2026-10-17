package dev.icode.mobile.data.android

import dev.icode.mobile.data.runtime.RuntimeManifest
import dev.icode.mobile.domain.RuntimeTools
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidSdkSetupTest {
    @Test
    fun `the setup is the catalog's Android SDK, and the licence it records is the one shipped`() {
        assertEquals(RuntimeTools.AndroidSdk.setup, AndroidSdkSetup.ID)
        assertEquals(RuntimeTools.AndroidSdk.licence, AndroidSdk.LICENCE)
    }

    @Test
    fun `the shipped licence text hashes to what sdkmanager accepts for android-sdk-license`() {
        // The hash sdkmanager writes on Accept, and checks a package's licence against: the text's
        // SHA-1, byte for byte as Google's repository publishes it.
        val text = File("src/main/assets/runtime/licences/${AndroidSdk.LICENCE}.txt").readText()
        assertEquals("24333f8a63b6825ea9c5514f83c2829b004d1fee", AndroidSdkSetup.licenceHash(text))
    }

    @Test
    fun `the manifest pins both downloads and the size of each package sdkmanager fetches`() {
        val manifest = RuntimeManifest.parse(File("src/main/assets/${RuntimeManifest.ASSET}").readText())
        manifest.archive(AndroidSdkSetup.COMMAND_LINE_TOOLS)
        manifest.archive(AndroidSdkSetup.HOST_TOOLS)
        assertEquals(AndroidSdk.PACKAGES.toSet(), manifest.packageBytes.keys)
    }

    @Test
    fun `only the build tools' host programs are taken from the ARM64 archive, into the build tools' own folder`() {
        assertEquals("aapt2", AndroidSdkSetup.hostTool("build-tools/aapt2"))
        assertEquals("zipalign", AndroidSdkSetup.hostTool("build-tools/zipalign"))
        assertNull(AndroidSdkSetup.hostTool("build-tools/"))
        assertNull(AndroidSdkSetup.hostTool("platform-tools/adb"))
        assertNull(AndroidSdkSetup.hostTool("others/veridex"))
        assertNull(AndroidSdkSetup.hostTool("build-tools/lib/aapt2"))
    }

    @Test
    fun `the environment points at the SDK and puts its tools on PATH after the system's`() {
        assertEquals(
            listOf(
                "# iCode: Android SDK (Settings › Runtime Tools), removed with it.",
                "export ANDROID_HOME=/root/Android/Sdk",
                "export PATH=\"\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/build-tools/36.0.0\"",
            ),
            AndroidSdkSetup.PROFILE.trimEnd().lines(),
        )
    }
}
