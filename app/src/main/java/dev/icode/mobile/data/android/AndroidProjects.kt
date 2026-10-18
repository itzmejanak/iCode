package dev.icode.mobile.data.android

import dev.icode.mobile.domain.AndroidProject
import java.io.File
import org.json.JSONObject

/**
 * Finds a workspace's Android apps (docs/android-toolchain-plan.md §4.4): a Gradle build — its
 * wrapper and `settings.gradle(.kts)` at the top — and each module, the top or a folder in it, that
 * holds `src/main/AndroidManifest.xml` and applies the Android application plugin, by its id
 * (`com.android.application`) or its version-catalog alias (`libs.plugins.android.application`).
 */
internal object AndroidProjects {
    fun apps(root: File, workspace: String): List<AndroidProject> {
        if (!File(root, "gradlew").isFile || SETTINGS.none { File(root, it).isFile }) return emptyList()
        val modules = listOf(root) + root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name }
        return modules.filter(::isApplication).map { AndroidProject(workspace, if (it == root) "" else it.name) }
    }

    private fun isApplication(module: File): Boolean {
        if (!File(module, "src/main/AndroidManifest.xml").isFile) return false
        val build = BUILD_FILES.map { File(module, it) }.firstOrNull(File::isFile) ?: return false
        return APPLICATION_PLUGIN.containsMatchIn(build.readText())
    }

    /** What `assembleDebug` wrote: `<module>/build/outputs/apk/debug/output-metadata.json`. */
    fun metadata(root: File, project: AndroidProject): File =
        File(if (project.module.isEmpty()) root else File(root, project.module), "build/outputs/apk/debug/output-metadata.json")

    /** The app's id and its APK, from AGP's [metadata]: the one `SINGLE` output of a debug build. */
    fun apk(metadata: File): Pair<String, File> {
        val json = JSONObject(metadata.readText())
        val elements = json.getJSONArray("elements")
        val single = (0 until elements.length()).map(elements::getJSONObject).firstOrNull { it.optString("type") == "SINGLE" }
            ?: throw IllegalArgumentException("The build wrote no single APK (split APKs are not installed)")
        return json.getString("applicationId") to File(metadata.parentFile, single.getString("outputFile"))
    }

    private val SETTINGS = listOf("settings.gradle", "settings.gradle.kts")
    private val BUILD_FILES = listOf("build.gradle.kts", "build.gradle")
    private val APPLICATION_PLUGIN = Regex("""com\.android\.application|plugins\.android\.application\b""")
}
