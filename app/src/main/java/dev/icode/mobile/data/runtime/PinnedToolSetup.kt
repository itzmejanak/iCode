package dev.icode.mobile.data.runtime

import android.content.Context
import dev.icode.mobile.data.io.ArchiveLinks
import dev.icode.mobile.data.io.Archives
import dev.icode.mobile.data.io.Unpacking
import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.ToolSetupProgress
import dev.icode.mobile.domain.files.ArchiveFormat
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * A runtime tool that is one pinned archive and nothing from apt (docs/assistant-and-mcp.md): unpacked whole
 * into the guest home's `.local/share/icode/<id>`, its programs linked into `~/.local/bin` — first on
 * the runtime's PATH ([RuntimeLayout]) — and run once to show they work. Everything is in the home,
 * so Repair keeps it. Node.js and uv: what MCP servers start with (`npx`, `uvx`).
 */
internal class PinnedToolSetup(
    private val layout: RuntimeLayout,
    private val context: Context,
    override val id: String,
    /** How the page says it: "Node.js 26.13.0". */
    private val name: String,
    /** Its pin in `assets/runtime/manifest.json`. */
    private val pin: String,
    private val format: ArchiveFormat,
    /** Where an archive's entry goes in the tool's folder; null leaves it out. */
    private val place: (String) -> String?,
    /** Its programs, by their paths in its folder: each linked into `~/.local/bin` by its file name. */
    private val programs: List<String>,
    /** What runs to show it works, and what that must print. */
    private val check: List<String>,
    private val expect: String,
) : RuntimeToolSetup {
    private val manifest by lazy { readManifest(context) }
    private val archive by lazy { manifest.archive(pin) }
    private val folder = File(layout.home, "$SHARE/$id")
    private val bin = File(layout.home, BIN)
    private val guest = GuestCommand(layout, GuestContext.Command)

    override val receipt: String by lazy { "$pin ${archive.checksum.hex}\n" }

    override fun measure(): Map<String, Long> =
        mapOf(archive.fileName to (archive.bytes ?: error("The runtime manifest pins no size for ${archive.fileName}")))

    override suspend fun install(onProgress: (ToolSetupProgress) -> Unit, onLine: (String) -> Unit) {
        // Kept until the whole setup has succeeded: a retry after a later step downloads nothing again.
        val download = File(context.cacheDir, archive.fileName)
        fetchPinned(archive, download, "Downloading $name", onProgress)
        withContext(NonCancellable) {
            onProgress(ToolSetupProgress("Setting up $name", cancellable = false))
            deleteTree(folder)
            folder.mkdirs()
            Archives.unpack(download, format, folder, Unpacking(ArchiveLinks.AsWritten, place = place))
            link()
        }
        onProgress(ToolSetupProgress("Verifying $name", cancellable = true))
        val ran = guest.capture(null, check, "verify")
        if (ran.exitCode != 0 || expect !in ran.stdout) {
            val said = ran.stderr.lineSequence().map(String::trim).lastOrNull(String::isNotEmpty)
            throw IOException("$name does not run as it should: ${said ?: "exit ${ran.exitCode}"}")
        }
        download.delete()
    }

    override fun configure() = Unit

    override suspend fun remove() {
        programs.forEach { program -> File(bin, File(program).name).toPath().takeIf(::ours)?.let(Files::delete) }
        deleteTree(folder)
    }

    /** Each program linked by a relative link, which reads the same from the phone and from inside the runtime. */
    private fun link() {
        bin.mkdirs()
        programs.forEach { program ->
            val link = File(bin, File(program).name).toPath()
            Files.deleteIfExists(link)
            Files.createSymbolicLink(link, Paths.get("$TO_SHARE/$id/$program"))
        }
    }

    /** A link of this tool's: never someone else's program of the same name. */
    private fun ours(link: java.nio.file.Path): Boolean =
        Files.isSymbolicLink(link) && Files.readSymbolicLink(link).toString().startsWith("$TO_SHARE/$id/")

    companion object {
        private const val SHARE = ".local/share/icode"
        private const val BIN = ".local/bin"

        /** From `~/.local/bin` to [SHARE]. */
        private const val TO_SHARE = "../share/icode"

        /** Node.js: node, npm and npx, the archive's top folder stripped. */
        fun node(layout: RuntimeLayout, context: Context) = PinnedToolSetup(
            layout, context, RuntimeTools.NodeJs.id, RuntimeTools.NodeJs.name, "nodejs-arm64", ArchiveFormat.TarXz,
            place = { entry -> entry.substringAfter('/', "").ifEmpty { null } },
            programs = listOf("bin/node", "bin/npm", "bin/npx"),
            check = listOf("${RuntimeLayout.GUEST_HOME}/$BIN/node", "--version"),
            expect = "v${RuntimeTools.NODE_VERSION}",
        )

        /** uv and uvx, from the wheel PyPI publishes: its two programs only. */
        fun uv(layout: RuntimeLayout, context: Context) = PinnedToolSetup(
            layout, context, RuntimeTools.Uv.id, RuntimeTools.Uv.name, "uv-arm64", ArchiveFormat.Zip,
            place = { entry -> entry.substringAfterLast('/').takeIf { it in UV_PROGRAMS && entry.contains(".data/scripts/") } },
            programs = UV_PROGRAMS,
            check = listOf("${RuntimeLayout.GUEST_HOME}/$BIN/uv", "--version"),
            expect = "uv ${RuntimeTools.UV_VERSION}",
        )

        private val UV_PROGRAMS = listOf("uv", "uvx")
    }
}
