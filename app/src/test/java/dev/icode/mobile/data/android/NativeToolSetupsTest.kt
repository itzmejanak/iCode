package dev.icode.mobile.data.android

import dev.icode.mobile.data.runtime.RuntimeManifest
import dev.icode.mobile.domain.RuntimeTools
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CMake's and the NDKs' setups (docs/android-ndk-plan.md §3.2, §3.3): their ids, pins and what each takes from its archive. */
class NativeToolSetupsTest {
    private val manifest = RuntimeManifest.parse(File("src/main/assets/${RuntimeManifest.ASSET}").readText())

    @Test fun `each setup is its catalog row's, and the manifest pins what it downloads`() {
        assertEquals(RuntimeTools.Cmake.setup, CmakeSetup.ID)
        assertEquals(listOf("android-ndk-28", "android-ndk-27"), RuntimeTools.ndks.values.map { it.setup })
        RuntimeTools.ndks.values.forEach { tool ->
            val pin = manifest.archive(tool.id)
            assertTrue(pin.url.startsWith("https://github.com/HomuHomu833/android-ndk-custom/releases/download/"))
            assertTrue(pin.fileName.endsWith("-aarch64-linux-musl.tar.xz"))
        }
        assertEquals(46_757_098L, manifest.archive(CmakeSetup.CMAKE_ARCHIVE).bytes)
        assertEquals(121_787L, manifest.archive(CmakeSetup.NINJA_ARCHIVE).bytes)
        assertEquals(22_308_647L, manifest.packageBytes[CmakeSetup.PACKAGE])
    }

    @Test fun `Kitware's programs and modules go into cmake 3_22_1, and nothing that cannot or need not run`() {
        assertEquals("bin/cmake", CmakeSetup.kitware("cmake-3.22.1-linux-aarch64/bin/cmake"))
        assertEquals("bin/ctest", CmakeSetup.kitware("cmake-3.22.1-linux-aarch64/bin/ctest"))
        assertEquals("bin/cpack", CmakeSetup.kitware("cmake-3.22.1-linux-aarch64/bin/cpack"))
        assertEquals("share/cmake-3.22/Modules/FindJNI.cmake", CmakeSetup.kitware("cmake-3.22.1-linux-aarch64/share/cmake-3.22/Modules/FindJNI.cmake"))
        listOf("bin/ccmake", "bin/cmake-gui", "doc/cmake-3.22/Copyright.txt", "man/man1/cmake.1", "", "bin/").forEach {
            assertNull(it, CmakeSetup.kitware("cmake-3.22.1-linux-aarch64/$it"))
        }
        assertEquals(setOf("cmake", "ctest", "cpack", "ninja"), CmakeSetup.PROGRAMS)
    }

    @Test fun `an NDK's entries land under its own folder, without the archive's top`() {
        assertEquals("source.properties", AndroidNdkSetup.withinTop("android-ndk-r27/source.properties"))
        assertEquals("toolchains/llvm/prebuilt/linux-arm64/bin/clang-18", AndroidNdkSetup.withinTop("android-ndk-r28c/toolchains/llvm/prebuilt/linux-arm64/bin/clang-18"))
        assertNull(AndroidNdkSetup.withinTop("android-ndk-r27/"))
        assertNull(AndroidNdkSetup.withinTop("android-ndk-r27"))
    }

    @Test fun `sdkmanager's installed list is read by package path`() {
        val output = """
            Installed packages:
              Path                 | Version       | Description                     | Location
              -------              | -------       | -------                         | -------
              build-tools;36.0.0   | 36.0.0        | Android SDK Build-Tools 36      | build-tools/36.0.0
              cmake;3.22.1         | 3.22.1        | CMake 3.22.1                    | cmake/3.22.1
              ndk;27.0.12077973    | 27.0.12077973 | NDK (Side by side) 27.0.12077973 | ndk/27.0.12077973
              platforms;android-36 | 2             | Android SDK Platform 36         | platforms/android-36
        """.trimIndent()
        assertEquals(setOf("build-tools;36.0.0", "cmake;3.22.1", "ndk;27.0.12077973", "platforms;android-36"), SdkManager.listed(output))
    }
}
