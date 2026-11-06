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
// One version for the assistant's Koog modules; an upgrade repeats docs/assistant-koog-plan.md §9 D1 and D9.
val koogVersion = "1.3.0"

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
        // The iCode website (icode-web): Settings › Application, update checks, opt-in device info.
        // Set with -PicodeWebUrl=https://… or in gradle.properties; empty turns every web feature off.
        val icodeWebUrl = (project.findProperty("icodeWebUrl") as String?).orEmpty()
        buildConfigField("String", "ICODE_WEB_URL", "\"$icodeWebUrl\"")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // The editor's TextMate engine logs through android.util.Log while it loads grammars; the
    // JVM tests that drive that engine need the stub to be quiet rather than throw. No test
    // relies on a log call throwing (GrammarAssetsTest checks what the parser returns).
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // EditorColours runs on the JVM and on the phone (native Oniguruma): one source for both.
    sourceSets {
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
        // RaTeX's prebuilt native library, with its provenance (third_party/ratex/README.md).
        getByName("main").jniLibs.srcDir("../third_party/ratex/jniLibs")
        // Termux's emulator, with iCode's output tap (third_party/termux-terminal-emulator/README.md).
        getByName("main").java.srcDir("../third_party/termux-terminal-emulator/src/main/java")
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
            // Seventeen of the assistant's libraries (Koog, Ktor, kotlinx, Jackson) carry one each;
            // an Android app has no Java module path (docs/assistant-koog-plan.md §4.9).
            "/META-INF/versions/9/module-info.class",
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
    // Accent colour picker. 1.1.4 is the newest release built for Kotlin 2.3 and
    // Compose 1.10+; later ones need Kotlin 2.4 (docs/accent-color-plan.md).
    implementation("com.github.skydoves:colorpicker-compose:1.1.4")
    implementation("org.apache.commons:commons-compress:1.27.1")
    // Commons Compress's own optional dependency for .7z and .xz (0BSD, pure Java;
    // docs/explorer-gaps-plan.md §3.11).
    implementation("org.tukaani:xz:1.10")
    // The renderer and input view. Apache-2.0 per termux-app LICENSE.md; see docs/terminal-maturity-plan.md §3.
    // Its emulator is built from third_party/termux-terminal-emulator, the same release with iCode's tap.
    implementation("com.github.termux.termux-app:terminal-view:v0.118.3") {
        exclude(module = "terminal-emulator")
    }
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    // The opt-in device heartbeat (docs/device-telemetry-plan.md): periodic, network-constrained, survives restarts.
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("io.github.rosemoe:editor")
    implementation("io.github.rosemoe:language-textmate")
    // The regex engine the TextMate grammars are written for (Oniguruma, as in VS Code).
    // Without it tm4e falls back to joni, which rejects variable-length lookbehinds and
    // dropped CSS selectors, TypeScript's `export default`, C#'s `await` (editor-colors-plan).
    implementation("io.github.rosemoe:oniguruma-native")
    // Markdown as GitHub reads it, math included, for the preview and the assistant's answers
    // (docs/markdown-rendering-plan.md §2.1). Pure Kotlin; depends only on kotlin-stdlib.
    implementation("org.jetbrains:markdown:0.7.17")
    // The assistant's agent runtime (docs/assistant-koog-plan.md §2, §4.9): the modules it uses,
    // not the koog-agents umbrella (Bedrock's AWS SDK, OpenTelemetry…). Not the DeepSeek client:
    // its Android build calls List.removeLast(), which Android 11–14 lack; DeepSeek is reached as
    // an OpenAI-compatible server instead.
    implementation("ai.koog:agents-core:$koogVersion")
    implementation("ai.koog:agents-tools:$koogVersion")
    // The standard agent's events: each reply, tool call and result as it happens (§12 R1).
    implementation("ai.koog:agents-features-event-handler:$koogVersion")
    implementation("ai.koog:prompt-executor-openai-client:$koogVersion")
    implementation("ai.koog:prompt-executor-anthropic-client:$koogVersion")
    implementation("ai.koog:prompt-executor-google-client:$koogVersion-beta")
    implementation("ai.koog:prompt-executor-openrouter-client:$koogVersion")
    implementation("ai.koog:prompt-executor-ollama-client:$koogVersion")
    implementation("ai.koog:http-client-ktor:$koogVersion")
    // The HTTP engine Koog's clients and the MCP client run on, handed to them explicitly. 3.5.2 is
    // the Ktor the MCP SDK needs; Koog 1.3.0 was built with 3.3.3 (docs/mcp-plan.md M0).
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    // MCP servers' tools for the assistant (docs/mcp-plan.md): the official SDK's client only.
    implementation("io.modelcontextprotocol:kotlin-sdk-client:0.16.0")
    // The coroutines the MCP SDK runs on; Android's dispatcher at the same version.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Shizuku (docs/phone-agent-plan.md §5.11): its binder, the user's permission, and a UserService of
    // iCode's own in a shell process. Apache-2.0 / MIT, on Maven Central.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    // A real MCP server, over Streamable HTTP on this machine, for the client's tests (docs/mcp-plan.md M2).
    testImplementation("io.modelcontextprotocol:kotlin-sdk-server:0.16.0")
    testImplementation("io.ktor:ktor-server-cio:3.5.2")
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
