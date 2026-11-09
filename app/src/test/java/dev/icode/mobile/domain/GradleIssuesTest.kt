package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradleIssuesTest {
    @Test
    fun `Kotlin's and javac's problems become Problems rows, by file within the workspace`() {
        val output = """
            > Task :app:compileDebugKotlin FAILED
            e: file:///workspace/hello/app/src/main/java/com/example/MainActivity.kt:12:5 Unresolved reference 'Greting'.
            w: file:///workspace/hello/app/src/main/java/com/example/MainActivity.kt:3:1 'Foo' is deprecated.
            /workspace/hello/app/src/main/java/com/example/Util.java:7: error: cannot find symbol
            e: file:///workspace/hello/app/src/main/java/com/example/MainActivity.kt:12:5 Unresolved reference 'Greting'.
            FAILURE: Build failed with an exception.
        """.trimIndent()
        assertEquals(
            listOf(
                Diagnostic(Severity.Error, "app/src/main/java/com/example/MainActivity.kt", "Unresolved reference 'Greting'.", "12:5", "Gradle", TextRange.at(12, 5)),
                Diagnostic(Severity.Warning, "app/src/main/java/com/example/MainActivity.kt", "'Foo' is deprecated.", "3:1", "Gradle", TextRange.at(3, 1)),
                Diagnostic(Severity.Error, "app/src/main/java/com/example/Util.java", "cannot find symbol", "7", "Gradle", TextRange.at(7)),
            ),
            GradleIssues.parse(output, "/workspace/hello"),
        )
    }

    @Test
    fun `clang's problems become Problems rows, with or without the C and C++ tasks' prefix`() {
        val output = """
            > Task :app:buildCMakeDebug[arm64-v8a] FAILED
            C/C++: /workspace/hello/app/src/main/cpp/native-lib.cpp:12:5: error: use of undeclared identifier 'helo'
            /workspace/hello/app/src/main/cpp/util.c:3:10: fatal error: 'missing.h' file not found
            C/C++: /workspace/hello/app/src/main/cpp/native-lib.cpp:4:9: warning: unused variable 'x' [-Wunused-variable]
            C/C++: ninja: build stopped: subcommand failed.
        """.trimIndent()
        assertEquals(
            listOf(
                Diagnostic(Severity.Error, "app/src/main/cpp/native-lib.cpp", "use of undeclared identifier 'helo'", "12:5", "Gradle", TextRange.at(12, 5)),
                Diagnostic(Severity.Error, "app/src/main/cpp/util.c", "'missing.h' file not found", "3:10", "Gradle", TextRange.at(3, 10)),
                Diagnostic(Severity.Warning, "app/src/main/cpp/native-lib.cpp", "unused variable 'x' [-Wunused-variable]", "4:9", "Gradle", TextRange.at(4, 9)),
            ),
            GradleIssues.parse(output, "/workspace/hello"),
        )
    }

    @Test
    fun `a build that printed no problems has none`() {
        assertEquals(emptyList<Diagnostic>(), GradleIssues.parse("BUILD SUCCESSFUL in 41s\n34 actionable tasks: 34 executed", "/workspace/hello"))
    }

    @Test
    fun `what went wrong is its first two lines, without the cause's marker or the stack trace under it`() {
        val output = """
            > Task :app:configureCMakeDebug[arm64-v8a] FAILED

            FAILURE: Build failed with an exception.

            * What went wrong:
            Execution failed for task ':app:configureCMakeDebug[arm64-v8a]'.
            > [CXX1428] exception while building Json A problem occurred starting process 'command '/opt/android-sdk/cmake/3.22.1/bin/cmake''
                    at org.gradle.process.internal.DefaultExecHandle.execExceptionFor(DefaultExecHandle.java:241)

            * Try:
            > Run with --stacktrace option to get the stack trace.
        """.trimIndent()
        assertEquals(
            "Execution failed for task ':app:configureCMakeDebug[arm64-v8a]'. [CXX1428] exception while building Json A problem occurred starting process 'command '/opt/android-sdk/cmake/3.22.1/bin/cmake''",
            GradleIssues.failure(output),
        )
    }

    @Test
    fun `a build that said nothing went wrong has no failure, and a long one is cut`() {
        assertNull(GradleIssues.failure("BUILD SUCCESSFUL in 41s"))
        assertNull(GradleIssues.failure("* What went wrong:\n\n* Try:"))
        assertEquals(300, GradleIssues.failure("* What went wrong:\n" + "x".repeat(500))?.length)
    }
}
