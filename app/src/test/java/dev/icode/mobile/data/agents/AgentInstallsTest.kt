package dev.icode.mobile.data.agents

import dev.icode.mobile.data.runtime.PinnedFile
import dev.icode.mobile.domain.AgentSource
import dev.icode.mobile.domain.CodingAgent
import dev.icode.mobile.domain.ToolSetupProgress
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Agents installed in the runtime's home (docs/assistant-and-mcp.md), on real files; the network and npm faked. */
class AgentInstallsTest {
    @get:Rule val temp = TemporaryFolder()

    private val home by lazy { temp.newFolder("home") }
    private val sha = "0".repeat(64)
    /** What the fake download writes, and the pins it was asked for. */
    private var served: File? = null
    private val fetched = mutableListOf<PinnedFile>()
    private var nodeSays: String? = null
    private val npmRuns = mutableListOf<List<String>>()
    private var npm: (List<String>) -> Pair<Int, String> = { argv -> installPackage(argv) }

    private val installs by lazy {
        AgentInstalls(
            home, temp.newFolder("cache"),
            node = { nodeSays },
            npm = { argv -> npmRuns += argv; npm(argv) },
            fetch = { file, target, _, _ ->
                fetched += file
                (served ?: throw IOException("No network")).copyTo(target, overwrite = true)
            },
        )
    }

    private fun claude(version: String, archive: String = "claude-agent-acp-linux-arm64.tar.gz", command: String = "./claude-agent-acp") = CodingAgent(
        "claude-acp", "Claude Agent", version, "", null, "proprietary", "https://example.com/LICENSE",
        AgentSource.Binary("https://github.com/x/releases/download/v$version/$archive", sha, command, emptyList(), emptyMap()),
    )

    private val codex = CodingAgent(
        "codex-acp", "Codex", "2.7.0", "", null, "Apache-2.0", "https://example.com/LICENSE",
        AgentSource.Npm("@agentclientprotocol/codex-acp", "2.7.0", listOf("--acp"), mapOf("NO_UPDATE" to "1")),
    )

    private fun install(agent: CodingAgent) = runBlocking { installs.install(agent) { _: ToolSetupProgress -> } }

    /** A release's .tar.gz, its program runnable. */
    private fun tarGz(program: String): File = temp.newFile().also { file ->
        TarArchiveOutputStream(GzipCompressorOutputStream(file.outputStream())).use { tar ->
            val bytes = "#!/bin/sh\n".toByteArray()
            tar.putArchiveEntry(TarArchiveEntry(program).apply { size = bytes.size.toLong(); mode = "755".toInt(8) })
            tar.write(bytes)
            tar.closeArchiveEntry()
        }
    }

    /** As npm installs a package under its prefix: the package, and its program linked in `.bin`. */
    private fun installPackage(argv: List<String>): Pair<Int, String> {
        val prefix = File(home, argv[argv.indexOf("--prefix") + 1].removePrefix("/root/"))
        File(prefix, "node_modules/@agentclientprotocol/codex-acp").apply { mkdirs() }.resolve("package.json").writeText("""{"bin": {"codex-acp": "bin/cli.js"}}""")
        File(prefix, "node_modules/.bin").apply { mkdirs() }.resolve("codex-acp").writeText("#!/usr/bin/env node\n")
        return 0 to ""
    }

    private fun folder(id: String) = File(home, ".local/share/icode/agents/$id")

    @Test fun `a build is downloaded by its digest, unpacked into its version's folder, and runs`() {
        served = tarGz("claude-agent-acp")
        val installed = install(claude("0.108.0"))
        assertEquals(sha, fetched.single().checksum.hex)
        assertEquals(listOf("/root/.local/share/icode/agents/claude-acp/0.108.0/claude-agent-acp"), installed.command)
        assertTrue(File(folder("claude-acp"), "0.108.0/claude-agent-acp").canExecute())
        assertEquals(mapOf("claude-acp" to installed), installs.receipts.installed())
        assertTrue(installed.bytes > 0)
    }

    @Test fun `an update replaces the release only once it is in place, and a failed one leaves the installed release`() {
        served = tarGz("claude-agent-acp")
        install(claude("0.108.0"))
        served = null
        assertThrows(IOException::class.java) { install(claude("0.109.0")) }
        assertEquals("0.108.0", installs.receipts.installed().getValue("claude-acp").version)
        assertEquals(listOf("0.108.0"), folder("claude-acp").list()!!.toList())
        served = tarGz("claude-agent-acp")
        install(claude("0.109.0"))
        assertEquals("0.109.0", installs.receipts.installed().getValue("claude-acp").version)
        assertEquals(listOf("0.109.0"), folder("claude-acp").list()!!.toList())
    }

    @Test fun `a raw build is the program itself`() {
        served = temp.newFile().apply { writeText("binary") }
        install(claude("1.0.0", archive = "claude-agent-acp", command = "./claude-agent-acp"))
        assertTrue(File(folder("claude-acp"), "1.0.0/claude-agent-acp").canExecute())
    }

    @Test fun `what is not a release iCode installs is refused, leaving nothing`() {
        served = tarGz("claude-agent-acp")
        assertThrows(IOException::class.java) { install(claude("../escape")) }
        assertThrows(IOException::class.java) { install(claude("1.0.0", command = "../../outside")) }
        assertThrows(IOException::class.java) { install(claude("1.0.0", archive = "claude.7z")) }
        assertThrows(IOException::class.java) { install(claude("1.0.0", command = "./missing")) }
        assertTrue(installs.receipts.installed().isEmpty())
        assertFalse(File(folder("claude-acp"), "1.0.0").exists())
    }

    @Test fun `an npm package installs at its exact version under its own prefix, Node js first`() {
        val installed = install(codex)
        val prefix = "/root/.local/share/icode/agents/codex-acp/2.7.0"
        assertEquals(listOf("npm", "install", "--prefix", prefix, "--no-audit", "--no-fund", "--loglevel=error", "@agentclientprotocol/codex-acp@2.7.0"), npmRuns.single())
        assertEquals(listOf("$prefix/node_modules/.bin/codex-acp", "--acp"), installed.command)
        assertEquals(mapOf("NO_UPDATE" to "1"), installed.env)
    }

    @Test fun `a program npm did not link, as when two packages name it, runs from where its package keeps it`() {
        // Cline and its build for the phone both name `cline`: npm links neither.
        npm = { argv ->
            val prefix = File(home, argv[argv.indexOf("--prefix") + 1].removePrefix("/root/"))
            File(prefix, "node_modules/cline").apply { mkdirs() }.resolve("package.json").writeText("""{"bin": {"cline": "./bin/cline"}}""")
            File(prefix, "node_modules/cline/bin").apply { mkdirs() }.resolve("cline").writeText("#!/usr/bin/env node\n")
            File(prefix, "node_modules/.bin").mkdirs()
            0 to ""
        }
        val cline = CodingAgent("cline", "Cline", "3.0.74", "", null, "Apache-2.0", "https://example.com/LICENSE", AgentSource.Npm("cline", "3.0.74", listOf("--acp"), emptyMap()))
        val installed = install(cline)
        val prefix = "/root/.local/share/icode/agents/cline/3.0.74"
        assertEquals(listOf("$prefix/node_modules/cline/bin/cline", "--acp"), installed.command)
        assertTrue(File(folder("cline"), "3.0.74/node_modules/cline/bin/cline").canExecute())
    }

    @Test fun `without Node js, or when npm fails, nothing is installed and its words are said`() {
        nodeSays = "Install the Linux runtime first"
        assertEquals("Install the Linux runtime first", assertThrows(IOException::class.java) { install(codex) }.message)
        assertTrue(npmRuns.isEmpty())
        nodeSays = null
        npm = { 1 to "npm notice\nnpm error 404 Not Found - GET https://registry.npmjs.org/x\n" }
        assertEquals(
            "npm could not install Codex: npm error 404 Not Found - GET https://registry.npmjs.org/x",
            assertThrows(IOException::class.java) { install(codex) }.message,
        )
        assertFalse(File(folder("codex-acp"), "2.7.0").exists())
    }

    @Test fun `removing keeps the agent's own folders unless everything goes`() {
        install(codex)
        val own = File(home, ".codex/auth.json").apply { parentFile.mkdirs(); writeText("{}") }
        installs.remove("codex-acp", everything = false)
        assertTrue(installs.receipts.installed().isEmpty())
        assertFalse(folder("codex-acp").exists())
        assertTrue(own.exists())
        install(codex)
        installs.remove("codex-acp", everything = true)
        assertFalse(File(home, ".codex").exists())
    }

    @Test fun `a receipt whose program is gone is not an installed agent`() {
        install(codex)
        File(folder("codex-acp"), "2.7.0/node_modules/.bin/codex-acp").delete()
        assertTrue(installs.receipts.installed().isEmpty())
    }
}
