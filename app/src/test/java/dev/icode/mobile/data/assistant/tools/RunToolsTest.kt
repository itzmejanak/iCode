package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.android.AndroidBuilds
import dev.icode.mobile.data.android.AndroidRun
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.run.RunCommandStore
import dev.icode.mobile.data.run.RunCoordinator
import dev.icode.mobile.data.runtime.FakeSpawnedProcess
import dev.icode.mobile.data.runtime.ProcessLogs
import dev.icode.mobile.data.runtime.RuntimeProcesses
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AndroidProject
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.RunConfiguration
import dev.icode.mobile.domain.RunRecord
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The assistant's Run tab tools, over the RunCoordinator the tab itself uses (docs/runtime-and-terminal.md). */
class RunToolsTest {
    @get:Rule val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateHolder = ICodeStateHolder()

    /** An Android Studio project: the wrapper, the settings, and an `app` module. */
    private val runtime by lazy { TestRuntime(temp.root) }
    private val root by lazy {
        runtime.root.also { root ->
            mapOf(
                "gradlew" to "#!/bin/sh",
                "settings.gradle.kts" to "include(\":app\")",
                "app/build.gradle.kts" to "plugins { alias(libs.plugins.android.application) }",
                "app/src/main/AndroidManifest.xml" to "<manifest/>",
            ).forEach { (path, text) -> File(root, path).apply { parentFile?.mkdirs() }.writeText(text) }
        }
    }
    private val workspace by lazy { AssistantWorkspace(root, runtime.paths) }
    private val runs by lazy {
        val processes = RuntimeProcesses(scope, { FakeSpawnedProcess() }, { }, ProcessLogs(temp.newFolder("logs")), grace = 200)
        val builds = object : AndroidBuilds {
            override suspend fun buildAndRun(project: AndroidProject, report: ((RunRecord) -> RunRecord) -> Unit): Result<AndroidRun> =
                Result.failure(IOException("Not in this test"))

            override fun confirmInstall() = Unit
        }
        RunCoordinator(stateHolder, scope, processes, { emptyMap() }, { "/workspace/app" }, builds, RunCommandStore(File(temp.root, "run-commands.json")))
    }
    private val list by lazy { ListRunCommandsTool(workspace, runs) }
    private val save by lazy { SaveRunCommandTool(workspace, runs) }
    private val delete by lazy { DeleteRunCommandTool(workspace, runs) }

    @After fun stop() {
        scope.cancel()
    }

    private fun args(vararg pairs: Pair<String, Any>) = JSONObject(pairs.toMap())

    private fun refused(block: suspend () -> Unit): String = runBlocking {
        try {
            block()
            fail("expected a refusal")
            ""
        } catch (refusal: AssistantToolException) {
            refusal.message!!
        }
    }

    private fun saved(): List<RunConfiguration.Command> = runs.configurationsOf(root.path).filterIsInstance<RunConfiguration.Command>()

    @Test fun `listing is a read, saving and deleting are changes the user is asked about`() {
        assertEquals(AssistantToolKind.Read, list.kind)
        assertEquals(AssistantToolKind.Change, save.kind)
        assertEquals(AssistantToolKind.Change, delete.kind)
    }

    @Test fun `the Run tab is listed as the user sees it, the app found first`() = runBlocking {
        assertEquals("- app · Android app, found in the project: `./gradlew :app:assembleDebug`", list.run(JSONObject()))
        val dev = runs.saveCommand(root.path, null, "dev", "npm run dev")
        assertEquals(
            "- app · Android app, found in the project: `./gradlew :app:assembleDebug`\n- dev · id ${dev.id}: `npm run dev`",
            list.run(JSONObject()),
        )
        val empty = ListRunCommandsTool(TestRuntime(temp.newFolder("other"), "empty").workspace, runs)
        assertEquals("The Run tab is empty: no Android app found, no command saved.", empty.run(JSONObject()))
    }

    @Test fun `a command saved by the assistant is on the open Run tab at once`() = runBlocking {
        stateHolder.update { it.copy(activeWorkspaceLocation = root.path) }
        val arguments = args("command" to "npm run dev", "name" to "dev server")
        assertEquals("Add the Run command dev server", save.summary(arguments))
        assertEquals("Run tab: add “dev server” · npm run dev", save.detail(arguments))
        val answer = save.run(arguments)
        val dev = saved().single()
        assertEquals(RunConfiguration.Command(dev.id, "dev server", "npm run dev"), dev)
        assertEquals("Added “dev server” on the Run tab, id ${dev.id}: `npm run dev`. It runs when the user taps ▶.", answer)
        assertEquals(listOf("android:app", dev.id), stateHolder.current.run.configurations.map { it.id })
        // Without a name, the command's first word, as the Run tab's own dialog names it.
        save.run(args("command" to "python3 -m http.server"))
        assertEquals(listOf("dev server", "python3"), saved().map { it.name })
    }

    @Test fun `a change keeps the name or the command line it is not given`() = runBlocking {
        val dev = runs.saveCommand(root.path, null, "dev", "npm run dev")
        assertEquals("Change the Run command dev", save.summary(args("id" to dev.id)))
        assertEquals("Run tab: change “dev” · npm run dev -- --host", save.detail(args("id" to dev.id, "command" to "npm run dev -- --host")))
        save.run(args("id" to dev.id, "command" to "npm run dev -- --host"))
        assertEquals(listOf(RunConfiguration.Command(dev.id, "dev", "npm run dev -- --host")), saved())
        assertEquals("Run tab: change “dev” to “web” · npm run dev -- --host", save.detail(args("id" to dev.id, "name" to "web")))
        assertEquals("Changed “web” on the Run tab, id ${dev.id}: `npm run dev -- --host`. It runs when the user taps ▶.", save.run(args("id" to dev.id, "name" to "web")))
        assertEquals(listOf(RunConfiguration.Command(dev.id, "web", "npm run dev -- --host")), saved())
    }

    @Test fun `what cannot be saved or deleted is said, and nothing changes`() {
        assertEquals("Missing the “command” argument", refused { save.run(args("name" to "dev")) })
        assertEquals("No command on this workspace's Run tab has the id nope: list_run_commands lists them", refused { save.run(args("id" to "nope", "command" to "ls")) })
        val app = "app is an Android app found in the project: it is not saved, so it is not changed or deleted"
        assertEquals(app, refused { save.run(args("id" to "android:app", "command" to "ls")) })
        assertEquals(app, refused { delete.run(args("id" to "android:app")) })
        assertEquals("Missing the “id” argument", refused { delete.run(JSONObject()) })
        assertEquals(emptyList<RunConfiguration.Command>(), saved())
    }

    @Test fun `a command deleted by the assistant leaves the open Run tab`() = runBlocking {
        stateHolder.update { it.copy(activeWorkspaceLocation = root.path) }
        val dev = runs.saveCommand(root.path, null, "dev", "npm run dev")
        assertEquals("Delete the Run command dev", delete.summary(args("id" to dev.id)))
        assertEquals("Run tab: delete “dev” · npm run dev", delete.detail(args("id" to dev.id)))
        assertEquals("Deleted “dev” from the Run tab.", delete.run(args("id" to dev.id)))
        assertEquals(emptyList<RunConfiguration.Command>(), saved())
        assertEquals(listOf("android:app"), stateHolder.current.run.configurations.map { it.id })
        assertEquals("No command on this workspace's Run tab has the id ${dev.id}: list_run_commands lists them", refused { delete.run(args("id" to dev.id)) })
    }
}
