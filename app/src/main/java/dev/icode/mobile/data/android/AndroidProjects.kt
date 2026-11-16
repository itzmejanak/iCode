package dev.icode.mobile.data.android

import dev.icode.mobile.domain.AndroidProject
import java.io.File
import java.io.IOException
import org.json.JSONObject

/**
 * Finds a workspace's Android apps (docs/runtime-and-terminal.md): a Gradle build — its
 * wrapper and `settings.gradle(.kts)` at the top — and each module, the top or a folder in it, that
 * holds `src/main/AndroidManifest.xml` and applies the Android application plugin, by its id
 * (`com.android.application`) or its version-catalog alias (`libs.plugins.android.application`).
 * Its build compiles C or C++ when any of its modules names a CMake or ndk-build script.
 */
internal object AndroidProjects {
    fun apps(root: File, workspace: String): List<AndroidProject> {
        if (!File(root, "gradlew").isFile || SETTINGS.none { File(root, it).isFile }) return emptyList()
        val modules = listOf(root) + root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name }
        val native = modules.any { module -> buildFile(module)?.readText()?.let(NATIVE_BUILD::containsMatchIn) == true }
        return modules.filter(::isApplication).map { AndroidProject(workspace, if (it == root) "" else it.name, native) }
    }

    private fun buildFile(module: File): File? = BUILD_FILES.map { File(module, it) }.firstOrNull(File::isFile)

    private fun isApplication(module: File): Boolean {
        if (!File(module, "src/main/AndroidManifest.xml").isFile) return false
        val build = buildFile(module) ?: return false
        return APPLICATION_PLUGIN.containsMatchIn(build.readText())
    }

    /**
     * The app's id and its APK, once `assembleDebug` ended with 0: the one `SINGLE` output AGP's
     * `<module>/build/outputs/apk/debug/output-metadata.json` names. However old: a build that
     * found nothing changed left the last build's, which Gradle checked is current (UP-TO-DATE).
     */
    fun debugApk(root: File, project: AndroidProject): Pair<String, File> {
        val metadata = File(if (project.module.isEmpty()) root else File(root, project.module), "build/outputs/apk/debug/output-metadata.json")
        if (!metadata.isFile) throw IOException("The build wrote no debug APK where Gradle writes one")
        val json = JSONObject(metadata.readText())
        val elements = json.getJSONArray("elements")
        val single = (0 until elements.length()).map(elements::getJSONObject).firstOrNull { it.optString("type") == "SINGLE" }
            ?: throw IllegalArgumentException("The build wrote no single APK (split APKs are not installed)")
        return json.getString("applicationId") to File(metadata.parentFile, single.getString("outputFile"))
    }

    private val SETTINGS = listOf("settings.gradle", "settings.gradle.kts")
    private val BUILD_FILES = listOf("build.gradle.kts", "build.gradle")
    private val APPLICATION_PLUGIN = Regex("""com\.android\.application|plugins\.android\.application\b""")
    /** `externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }`, in either DSL; `defaultConfig`'s, with no path, is only flags. */
    private val NATIVE_BUILD = Regex("""externalNativeBuild\s*\{\s*(cmake|ndkBuild)\s*\{[^}]*\bpath\b""")
}
