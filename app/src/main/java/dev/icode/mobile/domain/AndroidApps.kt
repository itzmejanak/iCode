package dev.icode.mobile.domain

// Build & Run (docs/android-toolchain-plan.md §4.4): an Android project's app, built in the Linux
// runtime by the project's own Gradle, installed on this phone and opened.

/** The open workspace's Android app: the Gradle module that builds it, by its folder ("" for the top). */
data class AndroidProject(val workspace: String, val module: String) {
    /** `:app:assembleDebug`, or `assembleDebug` for a project that is one module. */
    val task: String get() = if (module.isEmpty()) "assembleDebug" else ":${module.replace('/', ':')}:assembleDebug"
}

enum class AndroidAppStep { Building, Installing, Opened }

/** One Build & Run: its step, the build's managed process (its output is that process's tab), and why it stopped. */
data class AndroidAppRun(
    val workspace: String,
    val step: AndroidAppStep,
    val process: String? = null,
    /** In the words of what failed; null while it goes, and once the app is open. */
    val failure: String? = null,
) {
    val running: Boolean get() = failure == null && step != AndroidAppStep.Opened
}

data class AndroidAppsState(val project: AndroidProject? = null, val run: AndroidAppRun? = null)

/**
 * The problems a Gradle build printed, as the Problems panel lists them: Kotlin's
 * `e: file:///workspace/app/src/Main.kt:12:5 Unresolved reference 'x'.` and javac's
 * `/workspace/app/src/Main.java:12: error: cannot find symbol`, by file within the workspace.
 */
object GradleIssues {
    fun parse(output: String, workspaceGuestPath: String): List<Diagnostic> = output.lineSequence().mapNotNull { line ->
        KOTLIN.find(line)?.destructured?.let { (kind, path, row, column, message) ->
            Diagnostic(if (kind == "e") Severity.Error else Severity.Warning, relative(path, workspaceGuestPath), message, "$row:$column", SOURCE)
        } ?: JAVAC.find(line)?.destructured?.let { (path, row, kind, message) ->
            Diagnostic(if (kind == "error") Severity.Error else Severity.Warning, relative(path, workspaceGuestPath), message, row, SOURCE)
        }
    }.distinct().toList()

    private fun relative(path: String, top: String) = path.removePrefix("$top/")

    private const val SOURCE = "Gradle"
    private val KOTLIN = Regex("""^([ew]): file://(/[^:]+):(\d+):(\d+) (.+)$""")
    private val JAVAC = Regex("""^(/[^:]+\.java):(\d+): (error|warning): (.+)$""")
}
