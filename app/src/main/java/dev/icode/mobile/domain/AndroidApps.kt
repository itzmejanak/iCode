package dev.icode.mobile.domain

// Build & Run (docs/android-toolchain-plan.md §4.4): an Android project's app, built in the Linux
// runtime by the project's own Gradle, installed on this phone and opened — one kind of run on the
// Terminal panel's Run tab (docs/run-tab-plan.md).

/** An Android app of a workspace: the Gradle module that builds it, by its folder ("" for the top). */
data class AndroidProject(
    val workspace: String,
    val module: String,
    /** Its build compiles C or C++ (docs/android-ndk-plan.md §3.5): it needs CMake and an NDK. */
    val native: Boolean,
) {
    /** `:app:assembleDebug`, or `assembleDebug` for a project that is one module. */
    val task: String get() = if (module.isEmpty()) "assembleDebug" else ":${module.replace('/', ':')}:assembleDebug"
}

/** What follows an Android app's build once it ends with 0. */
enum class AndroidAppStep { Installing, Opened }

/**
 * What a Gradle build printed: the problems, as the Problems panel lists them — Kotlin's
 * `e: file:///workspace/app/src/Main.kt:12:5 Unresolved reference 'x'.` and javac's
 * `/workspace/app/src/Main.java:12: error: cannot find symbol`, by file within the workspace — and
 * what went wrong, as its `* What went wrong:` block says it.
 */
object GradleIssues {
    fun parse(output: String, workspaceGuestPath: String): List<Diagnostic> = output.lineSequence().mapNotNull { line ->
        KOTLIN.find(line)?.destructured?.let { (kind, path, row, column, message) ->
            Diagnostic(if (kind == "e") Severity.Error else Severity.Warning, relative(path, workspaceGuestPath), message, "$row:$column", SOURCE, at(row, column))
        } ?: JAVAC.find(line)?.destructured?.let { (path, row, kind, message) ->
            Diagnostic(if (kind == "error") Severity.Error else Severity.Warning, relative(path, workspaceGuestPath), message, row, SOURCE, at(row, null))
        } ?: CLANG.find(line)?.destructured?.let { (path, row, column, kind, message) ->
            Diagnostic(if (kind == "warning") Severity.Warning else Severity.Error, relative(path, workspaceGuestPath), message, "$row:$column", SOURCE, at(row, column))
        }
    }.distinct().toList()

    /** A tool's one-based line and column as a place to open. */
    private fun at(row: String, column: String?): TextRange? = row.toIntOrNull()?.let { TextRange.at(it, column?.toIntOrNull() ?: 1) }

    /** The first two lines after `* What went wrong:`, without the `> ` a cause is indented with; null when there are none. */
    fun failure(output: String): String? {
        val lines = output.lines()
        val start = lines.indexOfFirst { it.trim() == WENT_WRONG }.takeIf { it >= 0 } ?: return null
        return lines.drop(start + 1).takeWhile { it.isNotBlank() && !it.startsWith("* ") }.take(2)
            .joinToString(" ") { it.trim().removePrefix(">").trim() }.take(MAX_FAILURE).ifEmpty { null }
    }

    private fun relative(path: String, top: String) = path.removePrefix("$top/")

    private const val SOURCE = "Gradle"
    private const val WENT_WRONG = "* What went wrong:"
    private const val MAX_FAILURE = 300
    private val KOTLIN = Regex("""^([ew]): file://(/[^:]+):(\d+):(\d+) (.+)$""")
    private val JAVAC = Regex("""^(/[^:]+\.java):(\d+): (error|warning): (.+)$""")
    /** clang's, as the Android plugin's C and C++ tasks print it, with or without their `C/C++: ` prefix (docs/android-ndk-plan.md §3.5). */
    private val CLANG = Regex("""^(?:C/C\+\+: )?(/[^:]+):(\d+):(\d+): (error|fatal error|warning): (.+)$""")
}
