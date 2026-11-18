package dev.icode.mobile.data.agents

import dev.icode.mobile.data.io.ArchiveLinks
import dev.icode.mobile.data.io.Archives
import dev.icode.mobile.data.io.DigestAlgorithm
import dev.icode.mobile.data.io.Unpacking
import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.data.runtime.Checksum
import dev.icode.mobile.data.runtime.PinnedFile
import dev.icode.mobile.data.runtime.fetchPinned
import dev.icode.mobile.domain.AgentSource
import dev.icode.mobile.domain.CodingAgent
import dev.icode.mobile.domain.CodingAgents
import dev.icode.mobile.domain.InstalledAgent
import dev.icode.mobile.domain.ToolSetupProgress
import dev.icode.mobile.domain.files.ArchiveFormat
import dev.icode.mobile.domain.files.ArchiveFormats
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The agents installed in the runtime (docs/assistant-and-mcp.md). Each release goes in its own folder,
 * `~/.local/share/icode/agents/<id>/<version>`, and its receipt ([AgentReceipts]) is written last: the
 * release it replaces stays in use until then, and goes after. A build is used only once its
 * SHA-256 is the registry's; an npm package is installed at the registry's exact version, Node.js first.
 */
internal class AgentInstalls(
    /** The runtime's home, as the phone sees it. */
    private val home: File,
    /** Where a build's archive waits until it is unpacked. */
    private val downloads: File,
    /** Node.js installed unless it is; why it cannot be, or null once it is. */
    private val node: suspend () -> String?,
    /** npm run in the runtime: its exit code, and what it said on stderr. */
    private val npm: suspend (List<String>) -> Pair<Int, String>,
    private val fetch: suspend (PinnedFile, File, String, (ToolSetupProgress) -> Unit) -> Unit = ::fetchPinned,
) {
    private val agents = File(home, ".local/share/icode/agents")
    val receipts = AgentReceipts(home)

    /** [agent]'s release installed, in place of the one installed: saying each step. A failure or a cancel leaves nothing of it. */
    suspend fun install(agent: CodingAgent, onProgress: (ToolSetupProgress) -> Unit): InstalledAgent {
        val source = agent.source ?: throw IOException("${agent.name} has no build for this phone")
        if (!VERSION.matches(agent.version)) throw IOException("${agent.name}'s version is not one iCode installs")
        val folder = File(agents, "${agent.id}/${agent.version}")
        val installed = try {
            val program = when (source) {
                is AgentSource.Binary -> unpack(agent, source, folder, onProgress)
                is AgentSource.Npm -> npmInstall(agent, source, folder, onProgress)
            }
            InstalledAgent(agent.version, listOf(receipts.guest(program)) + source.args, source.env, sizeOf(folder)).also { receipts.record(agent.id, it) }
        } catch (failed: Throwable) {
            withContext(NonCancellable) { deleteTree(folder) }
            throw failed
        }
        // In place: the release it replaces goes; one that will not is gone with the next install.
        File(agents, agent.id).listFiles().orEmpty().filter { it.name != agent.version }.forEach { runCatching { deleteTree(it) } }
        return installed
    }

    /** [id] removed: its program and receipt; with [everything], its own folders in the home too ([CodingAgents.OWN]). */
    fun remove(id: String, everything: Boolean) {
        receipts.forget(id)
        deleteTree(File(agents, id))
        if (everything) CodingAgents.OWN[id].orEmpty().forEach { deleteTree(File(home, it)) }
    }

    private suspend fun unpack(agent: CodingAgent, source: AgentSource.Binary, folder: File, onProgress: (ToolSetupProgress) -> Unit): File {
        val name = source.archive.substringAfterLast('/')
        val format = ArchiveFormats.of(name)
        if (format != null && format !in ARCHIVES) throw IOException("${agent.name}'s download is a $name, which iCode does not unpack")
        val program = inside(folder, source.command)
        downloads.mkdirs()
        val download = File(downloads, "agent-${agent.id}-$name")
        fetch(PinnedFile(source.archive, null, Checksum(DigestAlgorithm.Sha256, source.sha256)), download, "Downloading ${agent.name}", onProgress)
        withContext(NonCancellable) {
            onProgress(ToolSetupProgress("Setting up ${agent.name}", cancellable = false))
            deleteTree(folder)
            folder.mkdirs()
            // A raw build is the program itself.
            if (format == null) download.copyTo(program, overwrite = true) else Archives.unpack(download, format, folder, Unpacking(ArchiveLinks.Inside))
            download.delete()
        }
        if (!program.isFile) throw IOException("${agent.name}'s download has no ${source.command}")
        program.setExecutable(true, false)
        return program
    }

    private suspend fun npmInstall(agent: CodingAgent, source: AgentSource.Npm, folder: File, onProgress: (ToolSetupProgress) -> Unit): File {
        onProgress(ToolSetupProgress("Getting Node.js ready", cancellable = false))
        node()?.let { throw IOException(it) }
        deleteTree(folder)
        folder.mkdirs()
        onProgress(ToolSetupProgress("Installing ${agent.name} with npm", cancellable = true))
        val (exit, said) = npm(listOf("npm", "install", "--prefix", receipts.guest(folder), "--no-audit", "--no-fund", "--loglevel=error", "${source.name}@${source.version}"))
        if (exit != 0) throw IOException("npm could not install ${agent.name}: ${said.lineSequence().map(String::trim).lastOrNull(String::isNotEmpty) ?: "exit $exit"}")
        val program = bin(folder, source.name)?.let { File(folder, "node_modules/.bin/$it") }
        if (program == null || !program.exists()) throw IOException("${source.name} names no program to run")
        return program
    }

    /** The program npm runs for [name], as `npm exec` picks it: its only one, or the one named as the package is. */
    private fun bin(folder: File, name: String): String? {
        val bin = JSONObject(File(folder, "node_modules/$name/package.json").readText()).opt("bin")
        val plain = name.substringAfterLast('/')
        return when (bin) {
            is String -> plain
            is JSONObject -> bin.keys().asSequence().toList().let { names -> names.singleOrNull() ?: names.firstOrNull { it == plain } }
            else -> null
        }
    }

    /** [path], the registry's `./name`, in [folder]; never outside it. */
    private fun inside(folder: File, path: String): File {
        val file = File(folder, path.removePrefix("./")).normalize()
        if (!file.path.startsWith(folder.normalize().path + File.separator)) throw IOException("The registry names a program outside its folder")
        return file
    }

    /** What [folder] takes on disk, its links counted as links. */
    private fun sizeOf(folder: File): Long = Files.walk(folder.toPath()).use { paths ->
        paths.filter { Files.isRegularFile(it, java.nio.file.LinkOption.NOFOLLOW_LINKS) }.mapToLong(Files::size).sum()
    }

    private companion object {
        /** The archives the registry's builds come in (FORMAT.md). */
        val ARCHIVES = setOf(ArchiveFormat.Zip, ArchiveFormat.TarGz, ArchiveFormat.TarBz2)

        /** A version that is also a folder's name: never a path. */
        val VERSION = Regex("[0-9A-Za-z][0-9A-Za-z.+_-]*")
    }
}
