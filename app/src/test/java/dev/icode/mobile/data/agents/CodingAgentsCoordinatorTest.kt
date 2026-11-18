package dev.icode.mobile.data.agents

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AgentAction
import dev.icode.mobile.domain.AgentIntent
import dev.icode.mobile.domain.CodingAgentsState
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.said
import dev.icode.mobile.domain.notices.NoticeTone
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Settings › Assistant › Coding agents' requests, on real files, the registry and npm faked. */
class CodingAgentsCoordinatorTest {
    @get:Rule val temp = TemporaryFolder()

    private val originalWriter = BackendLog.writer
    private val stateHolder = ICodeStateHolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val effects = CopyOnWriteArrayList<ICodeEffect>()
    private val home by lazy { temp.newFolder("home") }
    private var list = registry("2.7.0")
    private var runtime = true
    private var npmGate: CompletableDeferred<Unit>? = null
    private var npmFails = false
    private val forgotten = CopyOnWriteArrayList<String>()

    private fun registry(codex: String) = """
        {"agents": [{"id": "codex-acp", "name": "Codex", "version": "$codex", "description": "ACP adapter for OpenAI's coding assistant",
          "license": "Apache-2.0", "license_url": "https://example.com/LICENSE",
          "distribution": {"npx": {"package": "@agentclientprotocol/codex-acp@$codex"}}}]}
    """.trimIndent()

    private val coordinator by lazy {
        CodingAgentsCoordinator(
            stateHolder, scope,
            AgentRegistry(File(temp.root, "registry.json"), download = { list.ifEmpty { throw IOException("No network") } }, measure = { null }),
            AgentInstalls(home, temp.newFolder("cache"), node = { null }, npm = { argv ->
                npmGate?.await()
                if (npmFails) 1 to "npm error ETIMEDOUT" else {
                    val prefix = File(home, argv[argv.indexOf("--prefix") + 1].removePrefix("/root/"))
                    File(prefix, "node_modules/@agentclientprotocol/codex-acp").apply { mkdirs() }.resolve("package.json").writeText("""{"bin": "cli.js"}""")
                    File(prefix, "node_modules/.bin").apply { mkdirs() }.resolve("codex-acp").writeText("")
                    0 to ""
                }
            }),
            runtimeReady = { runtime },
            forget = { forgotten += it },
        )
    }

    @Before fun start() {
        BackendLog.writer = { _, _, _ -> }
        scope.launch { stateHolder.effects.collect { effects += it } }
        coordinator.start()
    }

    @After fun stop() {
        scope.cancel()
        BackendLog.writer = originalWriter
    }

    private fun agents(): CodingAgentsState = stateHolder.current.agents

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out; agents: ${agents()}" }
            Thread.sleep(5)
        }
    }

    private fun told(title: String) = until { effects.any { it.said()?.title == title } }

    /** Installed, and its row at rest again. */
    private fun installed(title: String) {
        told(title)
        until { agents().work.isEmpty() }
    }

    private fun checked() {
        coordinator.handle(AgentIntent.Check)
        until { !agents().checking && agents().catalogue.isNotEmpty() }
    }

    @Test fun `an install shows its work on its row, then the agent is installed and said so`() {
        checked()
        npmGate = CompletableDeferred()
        coordinator.handle(AgentIntent.Install("codex-acp"))
        until { agents().work["codex-acp"]?.progress?.step == "Installing Codex with npm" }
        assertEquals(AgentAction.Install, agents().work.getValue("codex-acp").action)
        npmGate!!.complete(Unit)
        told("Codex 2.7.0 installed")
        until { agents().work.isEmpty() }
        assertEquals("2.7.0", agents().installed.getValue("codex-acp").version)
    }

    @Test fun `a newer release is an update, Update All installs it, and the page says it`() {
        checked()
        coordinator.handle(AgentIntent.Install("codex-acp"))
        installed("Codex 2.7.0 installed")
        list = registry("2.8.0")
        checked()
        until { agents().updates.isNotEmpty() }
        coordinator.handle(AgentIntent.UpdateAll)
        told("Codex 2.8.0 installed")
        until { agents().updates.isEmpty() && agents().work.isEmpty() }
    }

    @Test fun `a failure is said with npm's words, and nothing is installed`() {
        checked()
        npmFails = true
        coordinator.handle(AgentIntent.Install("codex-acp"))
        until { effects.any { it.said()?.title == "Couldn't install Codex" } }
        val said = effects.mapNotNull { it.said() }.first { it.title == "Couldn't install Codex" }
        assertEquals(NoticeTone.Problem, said.tone)
        assertEquals("npm could not install Codex: npm error ETIMEDOUT", said.output)
        until { agents().work.isEmpty() }
        assertTrue(agents().installed.isEmpty())
    }

    @Test fun `without the runtime nothing is installed`() {
        checked()
        runtime = false
        coordinator.handle(AgentIntent.Install("codex-acp"))
        told("Install the Linux runtime first")
        assertTrue(agents().work.isEmpty())
    }

    @Test fun `when the registry cannot be reached, the list last read stays, with why`() {
        checked()
        list = ""
        coordinator.handle(AgentIntent.Check)
        until { !agents().checking && agents().checkFailure != null }
        assertEquals(listOf("codex-acp"), agents().catalogue.map { it.id })
        assertEquals("No network", agents().checkFailure)
    }

    @Test fun `a removed agent is no longer installed, and said so`() {
        checked()
        coordinator.handle(AgentIntent.Install("codex-acp"))
        installed("Codex 2.7.0 installed")
        coordinator.handle(AgentIntent.Remove("codex-acp", everything = false))
        told("Codex removed")
        until { agents().work.isEmpty() }
        assertNull(agents().installed["codex-acp"])
        assertTrue("Keeping sign-in and settings keeps its keys", forgotten.isEmpty())
    }

    @Test fun `an agent deleted with everything has its keys and sign-in forgotten`() {
        checked()
        coordinator.handle(AgentIntent.Install("codex-acp"))
        installed("Codex 2.7.0 installed")
        coordinator.handle(AgentIntent.Remove("codex-acp", everything = true))
        told("Codex removed")
        until { agents().work.isEmpty() }
        assertEquals(listOf("codex-acp"), forgotten.toList())
    }
}
