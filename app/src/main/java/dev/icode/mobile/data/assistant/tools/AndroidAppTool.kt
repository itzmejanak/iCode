package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.android.AndroidApps
import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.domain.AssistantToolKind
import org.json.JSONObject

/** Build & Run, as the Explorer's ▶ starts it (docs/android-toolchain-plan.md §4.4); asked about first. */
internal class RunAndroidAppTool(private val workspace: AssistantWorkspace, private val apps: AndroidApps) : AssistantTool {
    override val name = "run_android_app"
    override val description = "Builds this workspace's Android app with the project's own Gradle wrapper " +
        "(`./gradlew :app:assembleDebug`) as a process, and when the build succeeds installs the APK on this phone and opens " +
        "it; Android asks the user to confirm the install. Needs the Android SDK, installed from Runtime Tools. Answers the " +
        "build's process ID at once: follow it with process_status. The build's errors also go to the user's Problems panel."
    override val parameters = emptyList<AssistantParameter>()
    override val kind = AssistantToolKind.Command

    override fun summary(arguments: JSONObject): String = "Build & Run the Android app"

    override suspend fun run(arguments: JSONObject): String {
        val process = apps.buildAndRun(workspace.root.path).getOrElse { throw AssistantToolException(it.message ?: "Build & Run did not start") }
        return "Started ${process.id} · ${process.name}: `${process.command}` in ${process.directory}. " +
            "When it ends with exit code 0 the app is installed and opened."
    }
}
