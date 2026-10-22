package dev.icode.mobile.data.android

import dev.icode.mobile.domain.AndroidProject
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidProjectsTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun project(vararg files: Pair<String, String>): File = temporaryFolder.newFolder().also { root ->
        files.forEach { (path, text) -> File(root, path).apply { parentFile?.mkdirs() }.writeText(text) }
    }

    private val wrapper = arrayOf("gradlew" to "#!/bin/sh", "settings.gradle.kts" to "include(\":app\")")

    /** What AGP's `assembleDebug` writes beside the APK. */
    private val metadata = "app/build/outputs/apk/debug/output-metadata.json" to """
        {"version": 3, "artifactType": {"type": "APK", "kind": "Directory"}, "applicationId": "com.example.hello",
         "variantName": "debug", "elements": [{"type": "SINGLE", "filters": [], "attributes": [], "versionCode": 1,
         "versionName": "1.0", "outputFile": "app-debug.apk"}], "elementType": "File"}
    """.trimIndent()

    @Test
    fun `Android Studio's template is found by its version-catalog alias`() {
        val root = project(
            *wrapper,
            "app/build.gradle.kts" to "plugins {\n    alias(libs.plugins.android.application)\n}",
            "app/src/main/AndroidManifest.xml" to "<manifest/>",
        )
        assertEquals(listOf(AndroidProject(root.path, "app", native = false)), AndroidProjects.apps(root, root.path))
        assertEquals(":app:assembleDebug", AndroidProject(root.path, "app", native = false).task)
    }

    @Test
    fun `every app module is found by the plugin's id, by folder name, and a library is not an app`() {
        val root = project(
            *wrapper,
            "demo/build.gradle" to "apply plugin: 'com.android.application'",
            "demo/src/main/AndroidManifest.xml" to "<manifest/>",
            "app/build.gradle.kts" to "plugins { id(\"com.android.application\") }",
            "app/src/main/AndroidManifest.xml" to "<manifest/>",
            "core/build.gradle.kts" to "plugins { alias(libs.plugins.android.library) }",
            "core/src/main/AndroidManifest.xml" to "<manifest/>",
        )
        assertEquals(listOf("app", "demo"), AndroidProjects.apps(root, root.path).map { it.module })
    }

    @Test
    fun `an app whose build names a CMake or ndk-build script, in any module, builds C or C++`() {
        val app = "app/src/main/AndroidManifest.xml" to "<manifest/>"
        val kotlin = project(
            *wrapper, app,
            "app/build.gradle.kts" to """
                plugins { alias(libs.plugins.android.application) }
                android {
                    defaultConfig { externalNativeBuild { cmake { cppFlags += "-std=c++17" } } }
                    externalNativeBuild {
                        cmake {
                            path = file("src/main/cpp/CMakeLists.txt")
                            version = "3.22.1"
                        }
                    }
                }
            """.trimIndent(),
        )
        assertEquals(listOf(true), AndroidProjects.apps(kotlin, kotlin.path).map { it.native })
        val library = project(
            *wrapper, app,
            "app/build.gradle" to "apply plugin: 'com.android.application'",
            "core/build.gradle" to "android { externalNativeBuild { ndkBuild { path 'src/main/jni/Android.mk' } } }",
        )
        assertEquals(listOf(true), AndroidProjects.apps(library, library.path).map { it.native })
        val flagsOnly = project(
            *wrapper, app,
            "app/build.gradle.kts" to "plugins { id(\"com.android.application\") }\nandroid { defaultConfig { externalNativeBuild { cmake { arguments += \"-DX=1\" } } } }",
        )
        assertEquals(listOf(false), AndroidProjects.apps(flagsOnly, flagsOnly.path).map { it.native })
    }

    @Test
    fun `a project that is one module builds at its top`() {
        val root = project(*wrapper, "build.gradle" to "apply plugin: 'com.android.application'", "src/main/AndroidManifest.xml" to "<manifest/>")
        assertEquals(listOf(""), AndroidProjects.apps(root, root.path).map { it.module })
        assertEquals("assembleDebug", AndroidProject(root.path, "", native = false).task)
    }

    @Test
    fun `without the wrapper and settings, or without an app module, there is no Android app`() {
        assertEquals(emptyList<AndroidProject>(), AndroidProjects.apps(project("app/build.gradle.kts" to "com.android.application", "app/src/main/AndroidManifest.xml" to ""), "w"))
        assertEquals(emptyList<AndroidProject>(), AndroidProjects.apps(project(*wrapper, "lib/build.gradle.kts" to "com.android.library", "lib/src/main/AndroidManifest.xml" to ""), "w"))
        assertEquals(emptyList<AndroidProject>(), AndroidProjects.apps(project(*wrapper, "app/build.gradle.kts" to "com.android.application"), "w"))
    }

    @Test
    fun `the APK and the app's id are read from what Gradle wrote`() {
        val root = project(metadata)
        val (id, apk) = AndroidProjects.debugApk(root, AndroidProject(root.path, "app", native = false))
        assertEquals("com.example.hello", id)
        assertEquals(File(root, "app/build/outputs/apk/debug/app-debug.apk"), apk)
    }

    @Test
    fun `a build that found nothing changed uses the last build's APK, however old`() {
        val root = project(metadata)
        // UP-TO-DATE: Gradle checked the outputs and wrote nothing, so they keep the first build's time.
        File(root, metadata.first).setLastModified(1_000_000_000_000L)
        assertEquals("com.example.hello", AndroidProjects.debugApk(root, AndroidProject(root.path, "app", native = false)).first)
    }

    @Test
    fun `without Gradle's metadata there is no APK to install`() {
        val root = project(*wrapper)
        val error = assertThrows(IOException::class.java) { AndroidProjects.debugApk(root, AndroidProject(root.path, "app", native = false)) }
        assertEquals("The build wrote no debug APK where Gradle writes one", error.message)
    }

    @Test
    fun `split APKs are said as not installed`() {
        val root = project(metadata.first to """{"applicationId": "a", "elements": [{"type": "ONE_OF_MANY", "outputFile": "a-arm64.apk"}]}""")
        assertThrows(IllegalArgumentException::class.java) { AndroidProjects.debugApk(root, AndroidProject(root.path, "app", native = false)) }
    }
}
