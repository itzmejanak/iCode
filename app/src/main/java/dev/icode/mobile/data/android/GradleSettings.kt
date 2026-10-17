package dev.icode.mobile.data.android

/**
 * What the Android SDK tool sets for Gradle (docs/android-toolchain-plan.md §4.3), each setting
 * once, in the place Gradle documents for it:
 *
 * - **`~/.gradle/gradle.properties`**, a block marked as iCode's: the Gradle properties. The user's
 *   own file takes precedence over a project's, so a project asking for more memory than a phone has
 *   gets the phone's figure. The rest of the file is the user's and is never touched.
 * - **`~/.gradle/init.d/icode-android.gradle`**: the build tools an Android module uses when its
 *   build names none — the ARM64 ones. A build that names its own keeps them.
 */
internal object GradleSettings {
    const val BEGIN = "# >>> iCode: Android SDK (Settings › Runtime Tools), removed with it >>>"
    const val END = "# <<< iCode: Android SDK <<<"

    /** The block, with the heap [heapMegabytes] sets. */
    fun block(heapMegabytes: Int): String = listOf(
        BEGIN,
        // Gradle's own aapt2 download is built for x86-64 and cannot run here (L1).
        "android.aapt2FromMavenOverride=${AndroidSdk.GUEST_BUILD_TOOLS}/aapt2",
        "org.gradle.jvmargs=-Xmx${heapMegabytes}m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8",
        // Few processes: Android 12+ stops an app's children beyond about 32 (L8).
        "org.gradle.workers.max=2",
        "org.gradle.parallel=false",
        "kotlin.compiler.execution.strategy=in-process",
        // A guest process ends with its PRoot session, so a daemon could not outlive the build.
        "org.gradle.daemon=false",
        END,
    ).joinToString("\n")

    /** A quarter of the phone's memory, between 768 MB and 2 GB (L3). */
    fun heapMegabytes(totalMemoryBytes: Long): Int =
        (totalMemoryBytes / 4 / BYTES_PER_MEGABYTE).coerceIn(MIN_HEAP_MEGABYTES, MAX_HEAP_MEGABYTES).toInt()

    /** [existing] with iCode's block replaced by [block], or taken out when it is null; the user's lines stay as they are. */
    fun withBlock(existing: String, block: String?): String {
        val start = existing.indexOf(BEGIN)
        val end = existing.indexOf(END, start.coerceAtLeast(0))
        val own = if (start >= 0 && end >= 0) existing.removeRange(start, end + END.length) else existing
        val rest = own.trim('\n')
        return listOfNotNull(rest.takeIf { it.isNotEmpty() }, block).joinToString("\n\n").let { if (it.isEmpty()) it else "$it\n" }
    }

    const val INIT_SCRIPT_NAME = "icode-android.gradle"

    /** Sets the default when a module's plugin is applied, before its own `android { }` runs. */
    val INIT_SCRIPT = """
        // iCode: Android SDK (Settings › Runtime Tools), removed with it. An Android module that names
        // no build tools uses ${AndroidSdk.BUILD_TOOLS}, whose host programs run on ARM64.
        allprojects {
            ['com.android.application', 'com.android.library', 'com.android.test', 'com.android.dynamic-feature'].each { plugin ->
                plugins.withId(plugin) { extensions.getByName('android').buildToolsVersion = '${AndroidSdk.BUILD_TOOLS}' }
            }
        }
    """.trimIndent() + "\n"

    private const val BYTES_PER_MEGABYTE = 1024L * 1024L
    private const val MIN_HEAP_MEGABYTES = 768L
    private const val MAX_HEAP_MEGABYTES = 2048L
}
