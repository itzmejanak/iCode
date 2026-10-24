package dev.icode.mobile.data.android

import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.domain.RuntimeTools

/**
 * What the Android SDK tool sets for Gradle (docs/android-toolchain-plan.md §4.3), each setting
 * once, in the place Gradle documents for it:
 *
 * - **`~/.gradle/gradle.properties`**, a block marked as iCode's: the Gradle properties. The user's
 *   own file takes precedence over a project's, so a project asking for more memory than a phone has
 *   gets the phone's figure. The rest of the file is the user's and is never touched.
 * - **`~/.gradle/init.d/icode-android.gradle`**: the build tools an Android module uses when its
 *   build names none — the ARM64 ones — and the CMake a module with C or C++ uses when it names
 *   none (docs/android-ndk-plan.md §3.4). A build that names its own keeps them. A module whose NDK
 *   or CMake is not installed here for ARM64 stops with what to do, before the Android Gradle
 *   plugin would download Google's: built for x86-64, hundreds of megabytes that cannot run.
 */
internal object GradleSettings {
    const val BEGIN = "# >>> ${RuntimeLayout.MANAGED} Android SDK (Settings › Runtime Tools), removed with it >>>"
    const val END = "# <<< ${RuntimeLayout.MANAGED} Android SDK <<<"

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

    /**
     * The defaults are set when a module's plugin is applied, before its own `android { }` runs. The
     * check is registered for every project before its build script applies any plugin, so it runs
     * before the Android Gradle plugin's own `afterEvaluate`, where it resolves an NDK and CMake.
     * An NDK or CMake counts as installed when its program is an ARM64 ELF (e_machine 183): Google's
     * x86-64 one, fetched by a build before, does not.
     */
    val INIT_SCRIPT = """
        // ${RuntimeLayout.MANAGED} Android SDK (Settings › Runtime Tools), removed with it. An Android module that names
        // no build tools uses ${AndroidSdk.BUILD_TOOLS}, whose host programs run on ARM64, and one with C or C++
        // that names no CMake uses ${RuntimeTools.CMAKE_VERSION}. One whose NDK or CMake is not installed here
        // for ARM64 stops before the Android Gradle plugin downloads Google's, built for x86-64 only.
        def icodeSdk = new File('${AndroidSdk.GUEST_ROOT}')
        def icodeNdks = [${RuntimeTools.ndks.keys.joinToString { "'$it'" }}]
        // The NDK each Android Gradle plugin picks when a module names none, by its major.minor: [first, last, NDK].
        def icodeDefaults = [${RuntimeTools.ndkRevisions.joinToString { "['${it.firstAgp}', '${it.lastAgp}', '${it.revision}']" }}]
        def icodeCmake = '${RuntimeTools.CMAKE_VERSION}'
        def icodeWhere = 'in ${RuntimeTools.PAGE}'

        def icodeArm64 = { File program ->
            if (!program.isFile()) return false
            def head = new byte[20]
            def read = program.withInputStream { it.read(head) }
            read == 20 && (head[0] & 0xff) == 0x7f && head[1] == 0x45 && head[2] == 0x4c && head[3] == 0x46 && (head[18] & 0xff) == 183 && head[19] == 0
        }

        def icodeAtLeast = { String have, String least ->
            def a = have.tokenize('.').collect { it as int }
            def b = least.tokenize('.').collect { it as int }
            for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
                def x = i < a.size() ? a[i] : 0
                def y = i < b.size() ? b[i] : 0
                if (x != y) return x > y
            }
            true
        }

        // The NDK the Android Gradle plugin picks when a module names none: its release notes' (§2.3).
        def icodeDefaultNdk = { android ->
            def agp = null
            try {
                agp = android.getClass().classLoader.loadClass('com.android.Version').getField('ANDROID_GRADLE_PLUGIN_VERSION').get(null)
            } catch (Throwable ignored) {
            }
            def parts = agp?.tokenize('.-')
            if (parts == null || parts.size() < 2) return null
            def release = parts[0] + '.' + parts[1]
            icodeDefaults.find { icodeAtLeast(release, it[0]) && icodeAtLeast(it[1], release) }?.getAt(2)
        }

        // The module's C and C++ build is set aside before it stops: Gradle still runs the Android Gradle
        // plugin's afterEvaluate after this one fails, and with it in place that would resolve — and
        // download — the NDK and CMake.
        def icodeStop = { nativeBuild, String message ->
            nativeBuild.cmake.path = null
            nativeBuild.ndkBuild.path = null
            throw new GradleException('iCode: ' + message)
        }

        def icodeCheck = { project ->
            def android = project.extensions.findByName('android')
            if (android == null) return
            def nativeBuild = android.externalNativeBuild
            if (nativeBuild.cmake.path == null && nativeBuild.ndkBuild.path == null) return
            if (android.ndkPath == null) {
                def ndk = android.ndkVersion ?: icodeDefaultNdk(android)
                if (ndk == null) {
                    icodeStop(nativeBuild, project.path + ' names no NDK, and the Android Gradle plugin\'s own is not one iCode knows. ' +
                        'Set android.ndkVersion to ' + icodeNdks.join(' or ') + ', which Runtime Tools installs.')
                }
                if (!icodeArm64(new File(icodeSdk, 'ndk/' + ndk + '/toolchains/llvm/prebuilt/linux-x86_64/bin/clang'))) {
                    if (ndk in icodeNdks) icodeStop(nativeBuild, 'NDK ' + ndk + ' is not installed for this phone. Install ${RuntimeTools.Ndk.name} ' + ndk + ' ' + icodeWhere + '.')
                    icodeStop(nativeBuild, 'NDK ' + ndk + ' cannot run on this phone. Runtime Tools installs NDK ' + icodeNdks.join(' and ') +
                        ': set android.ndkVersion to one of them.')
                }
            }
            def local = new Properties()
            def localFile = project.rootProject.file('local.properties')
            if (localFile.isFile()) localFile.withInputStream { local.load(it) }
            if (nativeBuild.cmake.path != null && !local.getProperty('cmake.dir')) {
                def wanted = nativeBuild.cmake.version ?: icodeCmake
                def matches = wanted.endsWith('+') ? icodeAtLeast(icodeCmake, wanted - '+') : wanted == icodeCmake
                if (!matches) {
                    icodeStop(nativeBuild, 'CMake ' + wanted + ' cannot run on this phone. Runtime Tools installs CMake ' + icodeCmake +
                        ': set externalNativeBuild.cmake.version to it.')
                }
                if (!icodeArm64(new File(icodeSdk, 'cmake/' + icodeCmake + '/bin/cmake'))) {
                    icodeStop(nativeBuild, 'CMake ' + icodeCmake + ' is not installed for this phone. Install CMake ' + icodeCmake + ' ' + icodeWhere + '.')
                }
            }
        }

        allprojects { project ->
            project.afterEvaluate { if (project.state.failure == null) icodeCheck(project) }
            ['com.android.application', 'com.android.library', 'com.android.test', 'com.android.dynamic-feature'].each { plugin ->
                project.plugins.withId(plugin) {
                    def android = project.extensions.getByName('android')
                    android.buildToolsVersion = '${AndroidSdk.BUILD_TOOLS}'
                    android.externalNativeBuild.cmake.version = icodeCmake
                }
            }
        }
    """.trimIndent() + "\n"

    private const val BYTES_PER_MEGABYTE = 1024L * 1024L
    private const val MIN_HEAP_MEGABYTES = 768L
    private const val MAX_HEAP_MEGABYTES = 2048L
}
