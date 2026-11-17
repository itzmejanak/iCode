package dev.icode.mobile.data.assistant.acp

import android.content.Context
import dev.icode.mobile.data.io.ArchiveLinks
import dev.icode.mobile.data.io.Archives
import dev.icode.mobile.data.io.DigestAlgorithm
import dev.icode.mobile.data.io.Unpacking
import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.data.runtime.Checksum
import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.PinnedFile
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeToolSetup
import dev.icode.mobile.data.runtime.fetchPinned
import dev.icode.mobile.data.runtime.shellQuote
import dev.icode.mobile.domain.CodingAgents
import dev.icode.mobile.domain.ToolSetupProgress
import dev.icode.mobile.domain.files.ArchiveFormat
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Where each coding agent is installed in the runtime, and what starts it (docs/assistant-and-mcp.md):
 * `~/.local/share/icode/agents/<id>`, a binary unpacked there or an npm package installed into it.
 */
internal class AgentCommands(private val rootfs: File) {
    /** [agent]'s folder, as the runtime sees it. */
    fun folder(agent: String): String = "$AGENTS/$agent"

    /** [agent]'s folder in iCode's storage. */
    fun hostFolder(agent: String): File = File(rootfs, folder(agent).removePrefix("/"))

    /** What starts [entry] as an ACP agent over its standard streams; null while it is not installed whole. */
    fun server(entry: AgentEntry): List<String>? = program(entry)?.let { listOf(it) + entry.distribution.args }

    /**
     * [entry]'s own CLI, resuming [session] — the agent's ID for the chat — where the CLI is known to; null
     * when its install has none.
     */
    fun cli(entry: AgentEntry, session: String?): List<String>? {
        val listed = CodingAgents.byId(entry.agent.id) ?: return null
        val program = when (val distribution = entry.distribution) {
            is AgentDistribution.Binary -> "${folder(entry.agent.id)}/${listed.cli}"
            is AgentDistribution.Npm -> "${folder(entry.agent.id)}/node_modules/.bin/${listed.cli}"
        }.takeIf(::present) ?: return null
        val resume = listed.resume?.takeIf { session != null }?.map { if (it == CodingAgents.SESSION) session!! else it }.orEmpty()
        return listOf(program) + resume
    }

    private fun program(entry: AgentEntry): String? = when (val distribution = entry.distribution) {
        is AgentDistribution.Binary -> "${folder(entry.agent.id)}/${distribution.cmd.removePrefix("./")}"
        is AgentDistribution.Npm -> bin(entry.agent.id, distribution)?.let { "${folder(entry.agent.id)}/node_modules/.bin/$it" }
    }?.takeIf(::present)

    /** The program an npm package installs under its own name, or its only one, as its `package.json` names them. */
    fun bin(agent: String, npm: AgentDistribution.Npm): String? {
        val manifest = File(hostFolder(agent), "node_modules/${npm.name}/package.json").takeIf { it.isFile } ?: return null
        val bin = runCatching { Json.parseToJsonElement(manifest.readText()).jsonObject["bin"] }.getOrNull() ?: return null
        val own = npm.name.substringAfterLast('/')
        return when (bin) {
            is JsonPrimitive -> own
            is JsonObject -> bin.keys.firstOrNull { it == own } ?: bin.keys.singleOrNull() ?: bin.keys.firstOrNull()
            else -> null
        }
    }

    /** [guest] is a file in the runtime: an install's program is there. */
    private fun present(guest: String): Boolean = File(rootfs, guest.removePrefix("/")).let { it.exists() || Files.isSymbolicLink(it.toPath()) }

    companion object {
        const val AGENTS = "/root/.local/share/icode/agents"

        /** [argv] as a terminal reads it, [variables] before it: never a key, which would show on screen. */
        fun commandLine(argv: List<String>, variables: Map<String, String>): String =
            (if (variables.isEmpty()) "" else "env " + variables.entries.joinToString(" ") { "${it.key}=${shellQuote(it.value)}" } + " ") +
                argv.joinToString(" ", transform = ::shellQuote)
    }
}

/**
 * One coding agent's install, as a runtime tool's setup (docs/runtime-and-terminal.md): its receipt the
 * version and checksum or package it pins, so a build pinning another reads as not installed and
 * installing brings it to this one. A binary is downloaded, checked against its SHA-256 and unpacked; an
 * npm package is installed with the runtime's Node.js at its exact version. The download's size is
 * unknown until it is fetched: the registry does not record it.
 */
internal class AgentSetup(
    private val layout: RuntimeLayout,
    private val context: Context,
    private val entry: AgentEntry,
    private val commands: AgentCommands = AgentCommands(layout.rootfs),
) : RuntimeToolSetup {
    override val id: String = entry.agent.tool

    override val receipt: String = when (val distribution = entry.distribution) {
        is AgentDistribution.Binary -> "${entry.agent.id} ${entry.agent.version} ${distribution.sha256}\n"
        is AgentDistribution.Npm -> "${entry.agent.id} ${entry.agent.version} ${distribution.spec}\n"
    }

    override fun measure(): Map<String, Long> = emptyMap()

    /** Its size is not pinned: the tools page says "Size unknown" rather than a size of nothing. */
    override val measurable: Boolean get() = false

    override suspend fun install(onProgress: (ToolSetupProgress) -> Unit, onLine: (String) -> Unit) {
        val name = entry.agent.name
        val folder = commands.hostFolder(entry.agent.id)
        when (val distribution = entry.distribution) {
            is AgentDistribution.Binary -> {
                val pin = PinnedFile(distribution.archive, bytes = null, Checksum(DigestAlgorithm.Sha256, distribution.sha256))
                val download = File(context.cacheDir, "agent-${entry.agent.id}-${pin.fileName}")
                fetchPinned(pin, download, "Downloading $name", onProgress)
                withContext(NonCancellable) {
                    onProgress(ToolSetupProgress("Setting up $name", cancellable = false))
                    deleteTree(folder)
                    Archives.unpack(download, formatOf(pin.fileName), folder, Unpacking(ArchiveLinks.Inside))
                    download.delete()
                }
            }
            is AgentDistribution.Npm -> {
                onProgress(ToolSetupProgress("Installing $name with npm", cancellable = true))
                val staging = File(folder.parentFile, ".${entry.agent.id}.installing")
                deleteTree(staging)
                staging.mkdirs()
                val guestStaging = "${AgentCommands.AGENTS}/.${entry.agent.id}.installing"
                val output = ArrayDeque<String>()
                val ran = GuestCommand(layout, GuestContext.Command).stream(
                    null,
                    listOf(NPM, "install", "--prefix", guestStaging, "--no-fund", "--no-audit", "--omit=dev", "--no-package-lock", distribution.spec),
                    "agent-install",
                ) { line ->
                    onLine(line)
                    output.addLast(line)
                    if (output.size > KEPT_LINES) output.removeFirst()
                    true
                }
                if (ran.exitCode != 0) {
                    deleteTree(staging)
                    throw IOException("npm could not install ${distribution.spec}: ${ran.failureDetail() ?: output.lastOrNull() ?: "exit ${ran.exitCode}"}")
                }
                withContext(NonCancellable) {
                    deleteTree(folder)
                    if (!staging.renameTo(folder)) throw IOException("Could not put $name in place")
                }
            }
        }
        onProgress(ToolSetupProgress("Verifying $name", cancellable = false))
        if (commands.server(entry) == null) {
            throw IOException("$name is installed without the program that starts it")
        }
    }

    override fun configure() = Unit

    override suspend fun remove() = withContext(NonCancellable) { deleteTree(commands.hostFolder(entry.agent.id)) }

    private fun formatOf(name: String): ArchiveFormat =
        ArchiveFormat.entries.firstOrNull { format -> format.suffixes.any { name.endsWith(it) } } ?: throw IOException("$name is not an archive iCode unpacks")

    private companion object {
        const val NPM = "/root/.local/bin/npm"
        const val KEPT_LINES = 40
    }
}

/** Every listed agent's setup, for the runtime tools' registry (docs/runtime-and-terminal.md). */
internal fun agentSetups(layout: RuntimeLayout, context: Context): List<RuntimeToolSetup> =
    AgentRegistry.pinned(context).map { AgentSetup(layout, context, it) }
