import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// One version for the editor engine: the dependency, and what About reports.
val soraEditorVersion = "0.24.6"

android {
    namespace = "dev.icode.mobile"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "dev.icode.mobile"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // PRoot's syscall translation and the runtime rootfs are ARM64 only.
        ndk.abiFilters += "arm64-v8a"

        buildConfigField("String", "EDITOR_ENGINE_VERSION", "\"$soraEditorVersion\"")
        buildConfigField("int", "MIN_SDK", "$minSdk")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Sora's TextMate module uses newer java.time APIs below Android 13.
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
        )
        jniLibs {
            // Android forbids executing binaries from the app data directory
            // (W^X, enforced by SELinux for untrusted_app). Runtime executables
            // must therefore live in the native library directory, which only
            // exists on disk when native libs are extracted at install time.
            // Modern AGP defaults this to false and loads libs from the APK.
            useLegacyPackaging = true
            // Termux's prebuilt libtermux.so is linked for 4 KB pages; the one
            // CMake builds from the same source is 16 KB aligned and must be the
            // one packaged (third_party/termux-terminal-jni/README.md).
            pickFirsts += "lib/*/libtermux.so"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    val soraBom = platform("io.github.rosemoe:editor-bom:$soraEditorVersion")

    implementation(composeBom)
    implementation(soraBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    // VT/xterm emulator, renderer and input view (brings terminal-emulator).
    // Apache-2.0 per termux-app LICENSE.md; see docs/terminal-maturity-plan.md §3.
    implementation("com.github.termux.termux-app:terminal-view:v0.118.3")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("io.github.rosemoe:editor")
    implementation("io.github.rosemoe:language-textmate")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    // Android provides org.json in production; this supplies the same API to local JVM parser tests.
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

/**
 * Every native library that ships must be 16 KB page aligned, which recent
 * Android requires. libtermux.so is the one at risk: ours is built so,
 * Termux's prebuilt is not, and only pickFirsts decides which ships. Checked on
 * what is merged for packaging, so the build fails instead of trusting order.
 */
abstract class CheckPageAlignment : DefaultTask() {
    @get:InputDirectory
    abstract val nativeLibs: DirectoryProperty

    @get:Input
    abstract val abis: ListProperty<String>

    @TaskAction
    fun check() {
        val misaligned = abis.get().flatMap { abi ->
            nativeLibs.get().asFile.resolve(abi).listFiles { file -> file.name.endsWith(".so") }.orEmpty().toList()
        }.filter { lib -> loadAlignments(lib).any { it < 16_384L } }
        if (misaligned.isNotEmpty()) {
            throw GradleException("Not 16 KB aligned: ${misaligned.joinToString { it.path }}")
        }
    }

    /** p_align of each PT_LOAD segment of a 64-bit little-endian ELF; none for a 32-bit one. */
    private fun loadAlignments(lib: File): List<Long> {
        val elf = ByteBuffer.wrap(lib.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        if (elf.get(4).toInt() != 2) return emptyList()
        val table = elf.getLong(0x20).toInt()
        val entrySize = elf.getShort(0x36).toInt()
        val entries = elf.getShort(0x38).toInt()
        return (0 until entries).map { index -> table + index * entrySize }
            .filter { offset -> elf.getInt(offset) == 1 }
            .map { offset -> elf.getLong(offset + 48) }
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar(Char::titlecase)
        val check = tasks.register<CheckPageAlignment>("check${name}PageAlignment") {
            nativeLibs.set(layout.buildDirectory.dir("intermediates/merged_native_libs/${variant.name}/merge${name}NativeLibs/out/lib"))
            abis.set(android.defaultConfig.ndk.abiFilters.toList())
            dependsOn("merge${name}NativeLibs")
        }
        tasks.matching { it.name == "package$name" }.configureEach { dependsOn(check) }
    }
}
