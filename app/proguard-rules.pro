# The rest of iCode is Compose and plain Kotlin and needs no project-specific keep rules.

# The assistant (docs/assistant-koog-plan.md §4.9). Koog's AARs ship no consumer rules, and parts of
# it are reflective (tool schemas, serializers found at run time). Kept whole until a release build
# has been checked on a device (§9 D9); then trimmed to what is reflective.
-keep class ai.koog.** { *; }

# Code the assistant's libraries carry for the JVM only, never reached on Android: Typesafe Config
# (through ktor-server-core) reads java.beans; the coroutines debug agent uses java.lang.management
# and java.lang.instrument; kotlin-logging can bind to Logback; OkHttp has Conscrypt and OpenJSSE
# shims. Measured with Animal Sniffer against the API 30 signature (§2).
-dontwarn java.beans.**
-dontwarn java.lang.management.**
-dontwarn java.lang.instrument.**
-dontwarn ch.qos.logback.**
-dontwarn org.slf4j.impl.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Shizuku starts iCode's shell service by its class name, with either constructor
# (docs/phone-agent-plan.md §5.11); its tag, not its name, identifies it after R8.
-keep class dev.icode.mobile.data.phone.shizuku.ShellUserService { <init>(...); }
