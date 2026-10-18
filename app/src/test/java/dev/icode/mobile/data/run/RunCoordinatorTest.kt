package dev.icode.mobile.data.run

import dev.icode.mobile.data.android.AndroidBuilds
import dev.icode.mobile.data.android.AndroidRun
import dev.icode.mobile.data.runtime.FakeSpawnedProcess
import dev.icode.mobile.data.runtime.ProcessLogs
import dev.icode.mobile.data.runtime.RuntimeProcesses
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AndroidAppStep
import dev.icode.mobile.domain.AndroidProject
import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RunConfiguration
import dev.icode.mobile.domain.RunRecord
import dev.icode.mobile.domain.Severity
import java.io.File
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RunCoordinatorTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    /** The Android kind, as the test drives it: the build is a managed process, what follows it is [follow]. */
    private inner class FakeBuilds : AndroidBuilds {
        var refusal: String? = null
        var follow: suspend () -> Unit = {}
        @Volatile var report: (((RunRecord) -> RunRecord) -> Unit)? = null
        var confirmed = 0

        override suspend fun buildAndRun(project: AndroidProject, report: ((RunRecord) -> RunRecord) -> Unit): Result<AndroidRun> {
            refusal?.let { return Result.failure(IOException(it)) }
            this.report = report
            return processes.start(project.workspace, "/workspace/hello", "bash ./gradlew --console=plain ${project.task}", "Build app", emptyMap())
                .map { AndroidRun(it) { follow() } }
        }

        override fun confirmInstall() {
            confirmed++
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateHolder = ICodeStateHolder()
    private val launched = Collections.synchronizedList(mutableListOf<Pair<RuntimeProcesses.Launch, FakeSpawnedProcess>>())
    private val processes by lazy {
        RuntimeProcesses(
            scope,
            { launch -> FakeSpawnedProcess().also { launched += launch to it } },
            { published -> stateHolder.update { it.copy(processes = published) } },
            ProcessLogs(temporaryFolder.newFolder("logs")),
            grace = 200,
        )
    }
    private val builds = FakeBuilds()
    private val commands by lazy { RunCommandStore(File(temporaryFolder.root, "run-commands.json")) }

    /** An Android Studio project: the wrapper, the settings, and an `app` module. */
    private val hello by lazy {
        temporaryFolder.newFolder("hello").also { root ->
            mapOf(
                "gradlew" to "#!/bin/sh",
                "settings.gradle.kts" to "include(\":app\")",
                "app/build.gradle.kts" to "plugins { alias(libs.plugins.android.application) }",
                "app/src/main/AndroidManifest.xml" to "<manifest/>",
            ).forEach { (path, text) -> File(root, path).apply { parentFile?.mkdirs() }.writeText(text) }
        }.path
    }
    private val runs by lazy {
        RunCoordinator(stateHolder, scope, processes, { mapOf("PATH" to "/usr/bin") }, { if (it == hello) "/workspace/hello" else null }, builds, commands)
    }

    @After fun stop() {
        scope.cancel()
    }

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out; run: ${stateHolder.current.run}" }
            Thread.sleep(5)
        }
    }

    private fun recordOf(configuration: String, workspace: String = hello) = stateHolder.current.run.recordOf(workspace, configuration)

    private fun open(workspace: String) = stateHolder.update { it.copy(activeWorkspaceLocation = workspace) }

    @Test fun `the open workspace lists its Android apps, then its saved commands, and again once one is saved`() {
        open(hello)
        runs.start()
        until { stateHolder.current.run.configurations.map { it.id } == listOf("android:app") }
        runs.handle(ICodeIntent.SaveRunCommand(null, "dev", "npm run dev"))
        until { stateHolder.current.run.configurations.size == 2 }
        assertEquals(listOf("Android app", "Command"), stateHolder.current.run.configurations.map { it.kind })
        open(temporaryFolder.newFolder("empty").path)
        until { stateHolder.current.run.configurations.isEmpty() }
    }

    @Test fun `a saved command runs in bash at the workspace's top, once at a time, and running it again forgets the last output`() {
        val dev = commands.save(hello, null, "dev", "npm run dev")
        val first = runBlocking { runs.start(hello, dev.id) }.getOrThrow()
        assertEquals(RuntimeProcesses.Launch(hello, "/workspace/hello", "npm run dev", mapOf("PATH" to "/usr/bin")), launched.single().first)
        assertEquals(RunRecord(hello, dev.id, first.id), recordOf(dev.id))
        assertEquals("dev is running already", runBlocking { runs.start(hello, dev.id) }.exceptionOrNull()?.message)
        launched.single().second.exit(0)
        until { processes.info(first.id)?.running == false }
        val second = runBlocking { runs.start(hello, dev.id) }.getOrThrow()
        assertNull(processes.info(first.id))
        assertEquals(second.id, recordOf(dev.id)?.process)
    }

    @Test fun `Stop stops its process`() {
        val dev = commands.save(hello, null, "dev", "npm run dev")
        open(hello)
        val process = runBlocking { runs.start(hello, dev.id) }.getOrThrow()
        runs.handle(ICodeIntent.StopRun(dev.id))
        until { processes.info(process.id)?.running == false }
    }

    @Test fun `an Android app's run follows its build to Android's install, which Stop abandons`() {
        var abandoned = false
        builds.follow = {
            try {
                awaitCancellation()
            } finally {
                abandoned = true
            }
        }
        open(hello)
        val build = runBlocking { runs.start(hello, "android:app") }.getOrThrow()
        assertEquals("bash ./gradlew --console=plain :app:assembleDebug", launched.single().first.command)
        launched.single().second.exit(0)
        until { processes.info(build.id)?.running == false }
        builds.report!!.invoke { it.copy(androidStep = AndroidAppStep.Installing) }
        assertEquals(AndroidAppStep.Installing, recordOf("android:app")?.androidStep)
        assertEquals("app is running already", runBlocking { runs.start(hello, "android:app") }.exceptionOrNull()?.message)
        runs.handle(ICodeIntent.ConfirmAndroidAppInstall)
        assertEquals(1, builds.confirmed)
        runs.handle(ICodeIntent.StopRun("android:app"))
        assertEquals("The install was cancelled", recordOf("android:app")?.failure)
        until { abandoned }
    }

    @Test fun `an app opened can run again`() {
        val build = runBlocking { runs.start(hello, "android:app") }.getOrThrow()
        launched.single().second.exit(0)
        until { processes.info(build.id)?.running == false }
        builds.report!!.invoke { it.copy(androidStep = AndroidAppStep.Opened) }
        val again = runBlocking { runs.start(hello, "android:app") }.getOrThrow()
        assertEquals(RunRecord(hello, "android:app", again.id), recordOf("android:app"))
    }

    @Test fun `a run that did not start says why on its row`() {
        builds.refusal = "Install the Android SDK first"
        assertTrue(runBlocking { runs.start(hello, "android:app") }.isFailure)
        assertEquals(RunRecord(hello, "android:app", null, failure = "Install the Android SDK first"), recordOf("android:app"))
        val elsewhere = temporaryFolder.newFolder("elsewhere").path
        val serve = commands.save(elsewhere, null, "serve", "python3 -m http.server")
        assertTrue(runBlocking { runs.start(elsewhere, serve.id) }.isFailure)
        assertEquals("$elsewhere is not in iCode's workspaces", recordOf(serve.id, elsewhere)?.failure)
        assertEquals("It is no longer in this workspace", runBlocking { runs.start(hello, "gone") }.exceptionOrNull()?.message)
    }

    @Test fun `the assistant's run_android_app starts the workspace's Android app, and says when there is none`() {
        val build = runBlocking { runs.startAndroidApp(hello) }.getOrThrow()
        assertEquals(build.id, recordOf("android:app")?.process)
        val empty = temporaryFolder.newFolder("empty").path
        assertEquals(
            "This workspace holds no Android app: a Gradle build with an application module",
            runBlocking { runs.startAndroidApp(empty) }.exceptionOrNull()?.message,
        )
    }

    @Test fun `a build's problems are its workspace's, and another workspace opens without them`() {
        open(hello)
        runs.start()
        until { stateHolder.current.run.configurations.isNotEmpty() }
        runBlocking { runs.start(hello, "android:app") }.getOrThrow()
        val problem = Diagnostic(Severity.Error, "app/src/main/java/Main.kt", "Unresolved reference 'x'.", "1:1", "Gradle")
        stateHolder.update { it.copy(diagnostics = listOf(problem)) }
        open(temporaryFolder.newFolder("other").path)
        until { stateHolder.current.diagnostics.isEmpty() }
    }

    @Test fun `a workspace renamed keeps its runs and commands, and one deleted forgets them`() {
        val dev = commands.save(hello, null, "dev", "npm run dev")
        val process = runBlocking { runs.start(hello, dev.id) }.getOrThrow()
        runs.workspaceMoved(hello, "/w/renamed")
        assertEquals(process.id, recordOf(dev.id, "/w/renamed")?.process)
        assertNull(recordOf(dev.id))
        assertEquals(listOf<RunConfiguration>(dev), commands.of("/w/renamed"))
        runs.workspaceForgotten("/w/renamed")
        assertNull(recordOf(dev.id, "/w/renamed"))
        assertEquals(emptyList<RunConfiguration>(), commands.of("/w/renamed"))
    }

    @Test fun `a saved command deleted takes its record with it`() {
        val dev = commands.save(hello, null, "dev", "npm run dev")
        open(hello)
        runBlocking { runs.start(hello, dev.id) }.getOrThrow()
        runs.handle(ICodeIntent.DeleteRunCommand(dev.id))
        until { recordOf(dev.id) == null }
        assertEquals(emptyList<RunConfiguration>(), commands.of(hello))
    }
}
