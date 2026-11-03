pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Termux publishes its terminal libraries, and sherpa-onnx its Android
        // library, only through JitPack. The filter keeps every other
        // dependency off this repository.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.termux.termux-app")
                includeGroup("com.github.k2-fsa.sherpa-onnx")
            }
        }
    }
}

rootProject.name = "iCode"
include(":app")
