package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
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
                Diagnostic(Severity.Error, "app/src/main/java/com/example/MainActivity.kt", "Unresolved reference 'Greting'.", "12:5", "Gradle"),
                Diagnostic(Severity.Warning, "app/src/main/java/com/example/MainActivity.kt", "'Foo' is deprecated.", "3:1", "Gradle"),
                Diagnostic(Severity.Error, "app/src/main/java/com/example/Util.java", "cannot find symbol", "7", "Gradle"),
            ),
            GradleIssues.parse(output, "/workspace/hello"),
        )
    }

    @Test
    fun `a build that printed no problems has none`() {
        assertEquals(emptyList<Diagnostic>(), GradleIssues.parse("BUILD SUCCESSFUL in 41s\n34 actionable tasks: 34 executed", "/workspace/hello"))
    }
}
